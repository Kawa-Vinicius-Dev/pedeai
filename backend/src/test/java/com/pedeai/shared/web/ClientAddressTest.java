package com.pedeai.shared.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientAddressTest {

    @Test
    void behindAProxyTheClientIsTheFirstForwardedAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.2");
        request.addHeader("X-Forwarded-For", "200.1.2.3, 76.76.21.21");

        assertThat(ClientAddress.of(request)).isEqualTo("200.1.2.3");
    }

    @Test
    void withoutProxyOrWithGarbageItIsTheConnection() {
        MockHttpServletRequest direct = new MockHttpServletRequest();
        direct.setRemoteAddr("200.1.2.3");
        assertThat(ClientAddress.of(direct)).isEqualTo("200.1.2.3");

        MockHttpServletRequest garbage = new MockHttpServletRequest();
        garbage.setRemoteAddr("200.1.2.3");
        garbage.addHeader("X-Forwarded-For", "x".repeat(200));
        assertThat(ClientAddress.of(garbage)).isEqualTo("200.1.2.3");
    }
}
