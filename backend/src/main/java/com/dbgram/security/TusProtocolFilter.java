package com.dbgram.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;

/** TUS protocol headers and method override, including error responses from security filters. */
@Component
@Order(-120)
public class TusProtocolFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/uploads")) {
            chain.doFilter(request, response);
            return;
        }

        // Run before authentication so rejected requests still carry the TUS protocol version.
        response.setHeader("Tus-Resumable", "1.0.0");
        response.setHeader("Tus-Version", "1.0.0");
        response.setHeader("Cache-Control", "no-store");

        String override = request.getHeader("X-HTTP-Method-Override");
        if (override == null) {
            chain.doFilter(request, response);
            return;
        }
        if (!Set.of("PATCH", "DELETE", "HEAD", "OPTIONS").contains(override)) {
            response.sendError(400, "Invalid method override");
            return;
        }

        HttpServletRequest overriddenRequest = new HttpServletRequestWrapper(request) {
            @Override
            public String getMethod() {
                return override;
            }
        };
        chain.doFilter(overriddenRequest, response);
    }
}
