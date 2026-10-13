package com.laundry.api.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ClientIpResolver {
    private final Set<String> trustedProxies;
    public ClientIpResolver(@Value("${app.login.trusted-proxies:127.0.0.1,::1}") String configured) {
        trustedProxies = Arrays.stream(configured.split(",")).map(String::trim).filter(v -> !v.isEmpty()).collect(Collectors.toUnmodifiableSet());
    }
    public String resolve(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        if (!trustedProxies.contains(remote)) return remote;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) return remote;
        String candidate = forwarded.split(",", 2)[0].trim();
        return candidate.matches("[0-9a-fA-F:.]{2,45}") ? candidate : remote;
    }
}
