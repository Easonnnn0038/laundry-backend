package com.laundry.api.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientIpResolverTest {
    @Test void ignoresSpoofedForwardedHeaderFromUntrustedSource() {
        var request=new MockHttpServletRequest();request.setRemoteAddr("203.0.113.9");request.addHeader("X-Forwarded-For","1.2.3.4");
        assertEquals("203.0.113.9",new ClientIpResolver("127.0.0.1,::1").resolve(request));
    }
    @Test void acceptsFirstForwardedAddressFromTrustedProxy() {
        var request=new MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");request.addHeader("X-Forwarded-For","1.2.3.4, 127.0.0.1");
        assertEquals("1.2.3.4",new ClientIpResolver("127.0.0.1,::1").resolve(request));
    }
}
