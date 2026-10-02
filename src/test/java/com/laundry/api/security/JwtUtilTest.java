package com.laundry.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {
    @Test
    void miniappTokenKeepsIdentityClaims() {
        JwtUtil jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secret", "test-secret-that-is-long-enough-for-hs256-signing-key");
        ReflectionTestUtils.setField(jwt, "expiration", 60_000L);

        String token = jwt.generateToken("openid-1", "MINIAPP", Map.of("phone", "13800138000"));

        assertTrue(jwt.validateToken(token));
        assertEquals("openid-1", jwt.getUsernameFromToken(token));
        assertEquals("MINIAPP", jwt.getRoleFromToken(token));
        assertEquals("13800138000", jwt.parseToken(token).get("phone", String.class));
    }
}
