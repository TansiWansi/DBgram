package com.dbgram.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded per-instance abuse protection. A multi-replica installation should also throttle at its gateway. */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
 private final ConcurrentHashMap<String,Window> windows=new ConcurrentHashMap<>();
 private record Window(long start,int count) {}
    @Override
    protected boolean shouldNotFilter(HttpServletRequest r) {
        return !r.getMethod().equals("POST") || !(r.getRequestURI().equals("/api/auth/login") || r.getRequestURI().equals("/api/auth/register"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        long now = System.currentTimeMillis();
        if (windows.size() > 10000) windows.entrySet().removeIf(e -> now - e.getValue().start() > 60000);
        if (windows.size() > 20000) {
            reject(res);
            return;
        }
        Window w = windows.compute(req.getRemoteAddr(), (k, old) -> old == null || now - old.start() > 60000 ? new Window(now, 1) : new Window(old.start(), old.count() + 1));
        if (w.count() > 30) {
            reject(res);
            return;
        }
        chain.doFilter(req, res);
    }

    private void reject(HttpServletResponse res) throws IOException {
        res.setStatus(429);
        res.setHeader("Retry-After", "60");
        res.setContentType("application/json");
        res.getWriter().write("{\"success\":false,\"message\":\"Too many sign-in attempts. Try again in a minute.\"}");
    }
}
