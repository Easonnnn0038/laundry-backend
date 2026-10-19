package com.laundry.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.laundry.api.common.Result;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MiniappRateLimitFilter extends OncePerRequestFilter {
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final ClientIpResolver ips;
    private final ObjectMapper json;

    public MiniappRateLimitFilter(ClientIpResolver ips, ObjectMapper json) { this.ips = ips; this.json = json; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI(), method = request.getMethod();
        String group; int limit;
        if ("POST".equals(method) && (path.equals("/api/miniapp/auth/login") || path.equals("/api/miniapp/auth/test-login") || path.equals("/api/miniapp/auth/password-login"))) {
            group = "login"; limit = 20;
        } else if ("POST".equals(method) && path.equals("/api/miniapp/auth/bind-phone")) {
            group = "bind"; limit = 8;
        } else if ("POST".equals(method) && path.equals("/api/miniapp/pickup-orders")) {
            group = "pickup"; limit = 5;
        } else if (("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))
                && path.matches("/api/miniapp/addresses(?:/\\d+(?:/default)?)?")) {
            group = "address"; limit = 30;
        } else { chain.doFilter(request, response); return; }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String subject = auth != null && auth.getPrincipal() instanceof MiniappPrincipal user
                ? user.openid() : ips.resolve(request);
        long minute = System.currentTimeMillis() / 60000;
        Window window = windows.compute(group + ':' + subject, (key, previous) ->
                previous == null || previous.minute() != minute ? new Window(minute, 1) : new Window(minute, previous.count() + 1));
        // ponytail: single-instance limiter; move the same limits to the gateway when deploying multiple instances.
        if (windows.size() > 10_000) windows.entrySet().removeIf(entry -> entry.getValue().minute() < minute);
        if (window.count() > limit) {
            response.setStatus(429);
            response.setCharacterEncoding("UTF-8");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            json.writeValue(response.getWriter(), Result.error(429, "操作太频繁，请稍后重试"));
            return;
        }
        chain.doFilter(request, response);
    }

    private record Window(long minute, int count) {}
}
