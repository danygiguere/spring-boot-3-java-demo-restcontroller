package com.example.restcontrollerdemo.exception;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * Client-safe error. {@code messageKey} is resolved via {@code MessageSource} into the problem "detail" and also feeds
 * the stable "type" URI (see {@link #typeUri}). The cause, when given, is logged but never sent to the client.
 */
public abstract sealed class AppException extends ErrorResponseException {

    private final String messageKey;

    protected AppException(HttpStatus status, Throwable cause, String messageKey, Object[] args) {
        super(status, ProblemDetail.forStatus(status), cause, messageKey, args);
        this.messageKey = messageKey;
        getBody().setDetail(messageKey);
        getBody().setType(typeUri(messageKey));
    }

    public String getMessageKey() {
        return messageKey;
    }

    /**
     * Stable, locale-independent RFC 9457 "type" clients can switch on. {@code error.user.not.found} →
     * {@code /errors/user-not-found}. Message keys are thereby part of the API contract.
     */
    public static URI typeUri(String messageKey) {
        String slug = messageKey.replaceFirst("^error\\.", "").replace('.', '-').replace('_', '-');
        return URI.create("/errors/" + slug);
    }

    public static final class NotFound extends AppException {
        public NotFound(String messageKey, Object... args) {
            super(HttpStatus.NOT_FOUND, null, messageKey, args);
        }

        public NotFound(Throwable cause, String messageKey, Object... args) {
            super(HttpStatus.NOT_FOUND, cause, messageKey, args);
        }
    }

    public static final class Conflict extends AppException {
        public Conflict(String messageKey, Object... args) {
            super(HttpStatus.CONFLICT, null, messageKey, args);
        }
    }

    public static final class BadRequest extends AppException {
        public BadRequest(String messageKey, Object... args) {
            super(HttpStatus.BAD_REQUEST, null, messageKey, args);
        }
    }

    public static final class Unauthorized extends AppException {
        public Unauthorized(String messageKey, Object... args) {
            super(HttpStatus.UNAUTHORIZED, null, messageKey, args);
        }
    }

    public static final class Forbidden extends AppException {
        public Forbidden(String messageKey, Object... args) {
            super(HttpStatus.FORBIDDEN, null, messageKey, args);
        }
    }

    /**
     * Field errors detected by business logic that Bean Validation cannot express (cross-field rules, uniqueness
     * checks). Each value is a list of message keys.
     */
    public static final class ValidationErrors extends AppException {
        private final Map<String, List<String>> fieldErrors;

        public ValidationErrors(Map<String, List<String>> fieldErrors) {
            super(HttpStatus.UNPROCESSABLE_ENTITY, null, "error.validation_failed", new Object[0]);
            this.fieldErrors = Map.copyOf(fieldErrors);
        }

        public Map<String, List<String>> getFieldErrors() {
            return fieldErrors;
        }
    }
}
