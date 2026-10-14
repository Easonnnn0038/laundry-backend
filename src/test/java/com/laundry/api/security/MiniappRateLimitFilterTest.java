package com.laundry.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MiniappRateLimitFilterTest {
    @Test
    void limitsPickupSubmissionsPerUser() throws Exception {
        var filter = new MiniappRateLimitFilter(new ClientIpResolver("127.0.0.1"), new ObjectMapper());
        try {
            for (int i = 0; i < 6; i++) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(new MiniappPrincipal("user-1", "13800000001"), null));
                var request = new MockHttpServletRequest("POST", "/api/miniapp/pickup-orders");
                request.setRemoteAddr("127.0.0.1");
                var response = new MockHttpServletResponse();
                filter.doFilter(request, response, new MockFilterChain());
                assertEquals(i < 5 ? 200 : 429, response.getStatus());
            }
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(new MiniappPrincipal("user-2", "13800000002"), null));
            var response = new MockHttpServletResponse();
            filter.doFilter(new MockHttpServletRequest("POST", "/api/miniapp/pickup-orders"), response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
