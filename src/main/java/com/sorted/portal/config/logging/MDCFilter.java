package com.sorted.portal.config.logging;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * Filter to manage MDC (Mapped Diagnostic Context) for logging.
 * Adds a unique trace_id to each request for better traceability.
 */
public class MDCFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(MDCFilter.class);

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_TRACE_ID = "trace_id";

//    @Override
//    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
//            throws IOException, ServletException {
//
//    }

    private String getOrGenerateTraceId(HttpServletRequest request) {
        // Try to get trace ID from header
        String traceId = request.getHeader(TRACE_ID_HEADER);

        // If not present in header, generate a new one
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        }

        return traceId;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        log.info("MDCFilter: Starting request processing");
        log.info("MDCFilter: Request URI: {}", ((HttpServletRequest) request).getRequestURI());

        // Log initial MDC state
        Map<String, String> initialContext = MDC.getCopyOfContextMap();
        log.info("MDCFilter: Initial MDC context: {}", initialContext != null ? initialContext : "<null>");

        try {
            // Get trace ID from header or generate a new one
            String traceId = getOrGenerateTraceId((HttpServletRequest) request);
            log.info("MDCFilter: Using trace_id: {}", traceId);

            // Add trace ID to MDC
            MDC.put(MDC_TRACE_ID, traceId);
            log.info("MDCFilter: MDC context after setting trace_id: {}", MDC.getCopyOfContextMap());

            // Verify MDC is working
            String verifyTraceId = MDC.get(MDC_TRACE_ID);
            log.info("MDCFilter: Verified trace_id from MDC: {}", verifyTraceId);

            // Add trace ID to response headers
            if (response instanceof HttpServletResponse) {
                ((HttpServletResponse) response).addHeader(TRACE_ID_HEADER, traceId);
                log.info("MDCFilter: Added trace_id to response headers");
            }

            // Log MDC context before proceeding with the filter chain
            log.info("MDCFilter: MDC context before filter chain: {}", MDC.getCopyOfContextMap());

            // Create a new Runnable to verify MDC context propagation
            Runnable mdcCheck = () -> {
                log.info("MDCFilter: Inside new thread - MDC context: {}", MDC.getCopyOfContextMap());
            };
            new Thread(mdcCheck, "mdc-check-thread").start();

            chain.doFilter(request, response);

            // Log MDC context after filter chain
            log.info("MDCFilter: MDC context after filter chain: {}", MDC.getCopyOfContextMap());
        } catch (Exception e) {
            log.error("MDCFilter: Error in filter", e);
            throw e;
        } finally {
            // Log MDC context before clearing
            log.info("MDCFilter: MDC context before clearing: {}", MDC.getCopyOfContextMap());

            // Clear MDC after request is processed
            MDC.clear();
            log.info("MDCFilter: MDC context after clearing: {}", MDC.getCopyOfContextMap());
            log.info("MDCFilter: Completed request processing");
        }
    }

    @Override
    public void destroy() {
        // Cleanup not needed
    }
}
