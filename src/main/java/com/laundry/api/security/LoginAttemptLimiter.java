package com.laundry.api.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** Small single-instance login limiter; put the same rule at the reverse proxy when scaling out. */
@Component
public class LoginAttemptLimiter {
    private final ConcurrentHashMap<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration window;
    private final Duration block;

    public LoginAttemptLimiter(@Value("${app.login.max-failures:5}") int maxFailures,
                               @Value("${app.login.window-minutes:15}") long windowMinutes,
                               @Value("${app.login.block-minutes:15}") long blockMinutes) {
        this.maxFailures = maxFailures;
        this.window = Duration.ofMinutes(windowMinutes);
        this.block = Duration.ofMinutes(blockMinutes);
    }

    public boolean blocked(String ip, String username) {
        Attempt attempt = attempts.get(key(ip, username));
        if (attempt == null) return false;
        Instant now = Instant.now();
        if (attempt.blockedUntil != null && now.isBefore(attempt.blockedUntil)) return true;
        if (now.isAfter(attempt.firstFailure.plus(window))) attempts.remove(key(ip, username), attempt);
        return false;
    }

    public void failed(String ip, String username) {
        Instant now = Instant.now();
        attempts.compute(key(ip, username), (key, old) -> {
            Attempt current = old == null || now.isAfter(old.firstFailure.plus(window))
                    ? new Attempt(now, 0, null) : old;
            int failures = current.failures + 1;
            return new Attempt(current.firstFailure, failures,
                    failures >= maxFailures ? now.plus(block) : current.blockedUntil);
        });
        if (attempts.size() > 10_000) attempts.entrySet().removeIf(e ->
                now.isAfter(e.getValue().firstFailure.plus(window).plus(block)));
    }

    public void succeeded(String ip, String username) {
        attempts.remove(key(ip, username));
    }

    private String key(String ip, String username) {
        return ip + '|' + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    private record Attempt(Instant firstFailure, int failures, Instant blockedUntil) {}
}
