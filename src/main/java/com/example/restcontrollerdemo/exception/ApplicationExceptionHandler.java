package com.example.restcontrollerdemo.exception;

import com.example.restcontrollerdemo.filter.CorrelationIdFilter;
import com.example.restcontrollerdemo.filter.RequestBodyLimitFilter.PayloadTooLargeException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.util.DisconnectedClientHelper;

/**
 * Maps every uncaught exception to an RFC 9457 problem-detail response — including those raised by filters (401/403
 * from Spring Security, 413 from {@code RequestBodyLimitFilter}), which delegate to the
 * {@code HandlerExceptionResolver} so they reach this advice too.
 *
 * <p>Client-visible messages are an allowlist, not a denylist: {@link AppException} is the only type whose message (an
 * i18n key resolved via {@link MessageSource}) reaches the client. Any other exception may carry internals (SQL,
 * paths, state), so its message is never exposed: the client receives a generic status phrase. A framework
 * {@link ErrorResponse} (404/405/415 from routing, {@code ResponseStatusException}) keeps its 4xx status code, but
 * never its text. The real message and stack trace go to the log, tied to the response by the correlationId.
 */
@RestControllerAdvice
public class ApplicationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApplicationExceptionHandler.class);
    private static final String LIST_ELEMENT_NODE = ".<list element>";

    private final MessageSource messageSource;
    private final LocaleResolver localeResolver;

    public ApplicationExceptionHandler(MessageSource messageSource, LocaleResolver localeResolver) {
        this.messageSource = messageSource;
        this.localeResolver = localeResolver;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handle(
            Exception ex, HttpServletRequest request, HttpServletResponse response) {
        if (DisconnectedClientHelper.isClientDisconnectedException(ex)) {
            // Client disconnected mid-request — not a server error, and nothing to write to.
            return null;
        }
        String correlationId = CorrelationIdFilter.of(request, response);
        if (response.isCommitted()) {
            // Headers already sent (error mid-response): the status can no longer change.
            log.error("[{}] Error after response was committed", correlationId, ex);
            return null;
        }

        // The resolver, not LocaleContextHolder: for errors raised in filters, before the DispatcherServlet, the
        // thread-bound locale is not the one Accept-Language asks for yet.
        Locale locale = localeResolver.resolveLocale(request);

        ProblemDetail problem = toProblem(request, ex, correlationId, locale);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("correlationId", correlationId);

        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private ProblemDetail toProblem(HttpServletRequest request, Exception ex, String correlationId, Locale locale) {
        // Business field errors that Bean Validation annotations cannot express.
        if (ex instanceof AppException.ValidationErrors validation) {
            logAppException(validation, correlationId);
            Map<String, List<String>> resolved = new LinkedHashMap<>();
            validation
                    .getFieldErrors()
                    .forEach((field, keys) -> resolved.put(
                            field,
                            keys.stream()
                                    .map(key -> messageSource.getMessage(key, null, key, locale))
                                    .toList()));
            ProblemDetail body = validation.updateAndGetBody(messageSource, locale);
            body.setProperty("errors", resolved);
            return body;
        }

        // Custom app exceptions — messages are intentionally user-safe and i18n-aware.
        if (ex instanceof AppException app) {
            logAppException(app, correlationId);
            return app.updateAndGetBody(messageSource, locale);
        }

        // Bean Validation messages are already localized: Spring MVC binds the request's locale to the thread before
        // validating, and the validator interpolates in that locale (see I18nConfig).

        // @Valid @RequestBody without method validation on the same method.
        if (ex instanceof MethodArgumentNotValidException bind) {
            Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
            bind.getFieldErrors().forEach(error -> fieldErrors
                    .computeIfAbsent(error.getField(), k -> new ArrayList<>())
                    .add(message(error, locale)));
            logValidation(request, correlationId, fieldErrors);
            return validationProblem(fieldErrors, locale);
        }

        // Constraints on @RequestParam / @PathVariable / @RequestHeader, and on @Valid @RequestBody of the same method.
        if (ex instanceof HandlerMethodValidationException methodValidation) {
            Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
            for (ParameterValidationResult result : methodValidation.getParameterValidationResults()) {
                if (result instanceof ParameterErrors body) {
                    body.getFieldErrors().forEach(error -> fieldErrors
                            .computeIfAbsent(error.getField(), k -> new ArrayList<>())
                            .add(message(error, locale)));
                } else {
                    String name = parameterName(result.getMethodParameter());
                    result.getResolvableErrors().forEach(error -> fieldErrors
                            .computeIfAbsent(name, k -> new ArrayList<>())
                            .add(message(error, locale)));
                }
            }
            logValidation(request, correlationId, fieldErrors);
            return validationProblem(fieldErrors, locale);
        }

        // Manual Validator.validate calls.
        if (ex instanceof ConstraintViolationException cve) {
            Map<String, List<String>> fieldErrors = new LinkedHashMap<>();
            cve.getConstraintViolations().forEach(violation -> fieldErrors
                    .computeIfAbsent(
                            violation.getPropertyPath().toString().replace(LIST_ELEMENT_NODE, ""),
                            k -> new ArrayList<>())
                    .add(violation.getMessage()));
            logValidation(request, correlationId, fieldErrors);
            return validationProblem(fieldErrors, locale);
        }

        // Missing required header / query parameter: the request is malformed (400), before any validation.
        if (ex instanceof MissingRequestHeaderException missing) {
            log.warn("[{}] Missing header '{}'", correlationId, missing.getHeaderName());
            return paramProblem(missing.getHeaderName(), "param.required", locale);
        }
        if (ex instanceof MissingServletRequestParameterException missing) {
            log.warn("[{}] Missing query parameter '{}'", correlationId, missing.getParameterName());
            return paramProblem(missing.getParameterName(), "param.required", locale);
        }

        // Path or query value of the wrong type (e.g. /users/abc).
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            String name = parameterName(mismatch.getParameter());
            log.warn("[{}] Type mismatch on '{}'", correlationId, name);
            return paramProblem(name, "param.type", locale);
        }

        // Body larger than app.max-request-body: rejected up front, or while Jackson was reading it.
        if (isPayloadTooLarge(ex)) {
            log.warn("[{}] Request body too large: {}", correlationId, ex.getMessage());
            return ProblemDetail.forStatusAndDetail(
                    HttpStatus.PAYLOAD_TOO_LARGE, resolveStatusMessage(HttpStatus.PAYLOAD_TOO_LARGE, locale));
        }

        // Malformed JSON, unknown field, wrong JSON type.
        if (ex instanceof HttpMessageNotReadableException unreadable) {
            log.warn(
                    "[{}] Unreadable request body: {}",
                    correlationId,
                    unreadable.getMostSpecificCause().getMessage());
            return ProblemDetail.forStatusAndDetail(
                    HttpStatus.BAD_REQUEST, resolveStatusMessage(HttpStatus.BAD_REQUEST, locale));
        }

        // 404/405/406/415 from routing, ResponseStatusException, and other framework errors that carry a status.
        if (ex instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            if (status.is4xxClientError()) {
                log.warn("[{}] {}: {}", correlationId, ex.getClass().getSimpleName(), ex.getMessage());
                return ProblemDetail.forStatusAndDetail(status, resolveStatusMessage(status, locale));
            }
            log.error("[{}] {}: {}", correlationId, ex.getClass().getSimpleName(), ex.getMessage(), ex);
            return internalError(locale);
        }

        log.error("[{}] {}: {}", correlationId, ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return internalError(locale);
    }

    /** The interpolated Bean Validation message; a generic phrase for a binding error that has none. */
    private String message(MessageSourceResolvable error, Locale locale) {
        String message = error.getDefaultMessage();
        return message != null
                ? message
                : messageSource.getMessage("validation.invalid.value", null, "Invalid value", locale);
    }

    private static boolean isPayloadTooLarge(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof PayloadTooLargeException) {
                return true;
            }
        }
        return false;
    }

    private ProblemDetail validationProblem(Map<String, List<String>> fieldErrors, Locale locale) {
        String summary = messageSource.getMessage("error.validation_failed", null, "Validation failed", locale);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, summary);
        problem.setType(AppException.typeUri("error.validation_failed"));
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    private ProblemDetail internalError(Locale locale) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, resolveStatusMessage(HttpStatus.INTERNAL_SERVER_ERROR, locale));
    }

    /** 400 for a request that cannot be bound: missing or mistyped parameter. */
    private ProblemDetail paramProblem(String name, String messageKey, Locale locale) {
        String summary = messageSource.getMessage("error.param_invalid", null, "Invalid request parameter", locale);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, summary);
        problem.setType(AppException.typeUri("error.param_invalid"));
        problem.setProperty("errors", Map.of(name, List.of(messageSource.getMessage(messageKey, null, locale))));
        return problem;
    }

    /** The name the client used: the annotation value ({@code Idempotency-Key}), else the Java parameter name. */
    private static String parameterName(MethodParameter parameter) {
        RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
        if (header != null && !header.name().isEmpty()) {
            return header.name();
        }
        RequestParam param = parameter.getParameterAnnotation(RequestParam.class);
        if (param != null && !param.name().isEmpty()) {
            return param.name();
        }
        PathVariable path = parameter.getParameterAnnotation(PathVariable.class);
        if (path != null && !path.name().isEmpty()) {
            return path.name();
        }
        return parameter.getParameterName();
    }

    /** Generic phrase per status: never the exception's technical message. */
    private String resolveStatusMessage(HttpStatusCode status, Locale locale) {
        String fallback = status.is4xxClientError()
                ? "The request could not be processed."
                : "An unexpected error occurred. Please try again later.";
        return messageSource.getMessage("error." + status.value(), null, fallback, locale);
    }

    private void logAppException(AppException ex, String correlationId) {
        String label = "AppException." + ex.getClass().getSimpleName();
        if (ex.getCause() != null) {
            log.warn("[{}] {}: {}", correlationId, label, ex.getMessageKey(), ex);
        } else {
            log.warn("[{}] {}: {}", correlationId, label, ex.getMessageKey());
        }
    }

    private void logValidation(
            HttpServletRequest request, String correlationId, Map<String, List<String>> fieldErrors) {
        String details = fieldErrors.entrySet().stream()
                .map(e -> e.getKey() + ": " + String.join("; ", e.getValue()))
                .collect(Collectors.joining(", "));
        log.warn(
                "[{}] Validation failed on {} {}: {}",
                correlationId,
                request.getMethod(),
                request.getRequestURI(),
                details);
    }
}
