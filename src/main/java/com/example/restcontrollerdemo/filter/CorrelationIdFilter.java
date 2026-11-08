package com.example.restcontrollerdemo.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stamps every request with a server-generated correlation id, echoed in the {@value #CORRELATION_ID_HEADER} response
 * header and reused by the error handler, so a problem-detail {@code correlationId} matches the header and the log
 * lines. Inbound values are ignored (not trusted into logs).
 *
 * <p>Thread-per-request: the id also goes in the logging {@link MDC} for the whole request, which a reactive stack
 * cannot do without context propagation.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String ATTRIBUTE = CorrelationIdFilter.class.getName();
    private static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** The id stamped by this filter; a fresh one if the request bypassed it. */
    public static String of(HttpServletRequest request, HttpServletResponse response) {
        if (request.getAttribute(ATTRIBUTE) instanceof String existing) {
            return existing;
        }
        String created = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, created);
        if (!response.isCommitted()) {
            response.setHeader(CORRELATION_ID_HEADER, created);
        }
        return created;
    }
}
