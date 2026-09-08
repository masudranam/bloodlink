package com.bloodlink.logging;

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
 * Puts one id on every log line produced while handling a request.
 *
 * "What happened to this request" becomes a single grep, which is the whole
 * ambition. A client-supplied header is echoed rather than replaced, so a caller
 * can join its own logs to the server's.
 *
 * Ordered first, ahead of the security filter chain, so that a 401 is logged
 * with an id too - the failures are the lines somebody will be looking for.
 *
 * The MDC value is removed in a finally block, unconditionally. MDC is
 * thread-local and the container reuses threads, so a value left behind
 * reappears on whatever request that thread serves next and attributes one
 * person's activity to another.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** Long enough to be unique in a log file, short enough to type into a grep. */
    private static final int ID_LENGTH = 12;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String correlationId = isUsable(supplied) ? supplied.trim() : generate();

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * A supplied id is trusted only if it is short and printable.
     *
     * It ends up in log lines, so accepting arbitrary client input would let a
     * caller inject newlines and forge log entries.
     */
    private static boolean isUsable(String supplied) {
        if (supplied == null || supplied.isBlank() || supplied.length() > 64) {
            return false;
        }
        return supplied.trim().chars().allMatch(CorrelationIdFilter::isSafe);
    }

    private static boolean isSafe(int character) {
        return Character.isLetterOrDigit(character) || character == '-' || character == '_';
    }

    private static String generate() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, ID_LENGTH);
    }
}
