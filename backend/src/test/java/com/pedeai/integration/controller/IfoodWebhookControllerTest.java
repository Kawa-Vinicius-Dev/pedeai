package com.pedeai.integration.controller;

import com.pedeai.integration.config.IfoodProperties;
import com.pedeai.integration.ifood.IfoodSignature;
import com.pedeai.integration.service.InboundService;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.shared.config.TimeConfig;
import com.pedeai.shared.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IfoodWebhookController.class)
@Import({SecurityConfig.class, TimeConfig.class, IfoodWebhookControllerTest.Credentials.class})
class IfoodWebhookControllerTest {
    private static final String SECRET = "segredo-de-teste";
    private static final String EVENT = """
            {"id":"evt-1","code":"PLC","fullCode":"PLACED","orderId":"ord-1","merchantId":"m-1"}""";

    @TestConfiguration
    static class Credentials {
        @Bean
        IfoodProperties ifoodProperties() {
            return new IfoodProperties(true, "https://merchant-api.ifood.com.br", "client", SECRET,
                    "/order/v1.0/orders:polling", "/order/v1.0/orders:acknowledgment", false);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InboundService inbound;

    @Test
    void signedEventIsRecordedWithoutLogin() throws Exception {
        mockMvc.perform(post(IfoodWebhookController.PATH).contentType(MediaType.APPLICATION_JSON).content(EVENT)
                        .header("X-IFood-Signature", hmac(EVENT).toUpperCase()))
                .andExpect(status().isAccepted());
        verify(inbound).record(eq(OrderSource.IFOOD), argThat(event -> event.path("id").asString().equals("evt-1")));

        String batch = "[" + EVENT + "," + EVENT.replace("evt-1", "evt-2") + "]";
        mockMvc.perform(post(IfoodWebhookController.PATH).contentType(MediaType.APPLICATION_JSON).content(batch)
                        .header("X-IFood-Signature", hmac(batch)))
                .andExpect(status().isAccepted());
        verify(inbound, times(3)).record(eq(OrderSource.IFOOD), any());
    }

    @Test
    void wrongOrMissingSignatureRecordsNothing() throws Exception {
        mockMvc.perform(post(IfoodWebhookController.PATH).contentType(MediaType.APPLICATION_JSON).content(EVENT)
                        .header("X-IFood-Signature", hmac(EVENT.replace("ord-1", "ord-2"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(IfoodWebhookController.PATH).contentType(MediaType.APPLICATION_JSON).content(EVENT))
                .andExpect(status().isUnauthorized());
        verify(inbound, never()).record(any(), any());
    }

    @Test
    void signatureIsHmacOfTheRawBody() throws Exception {
        byte[] body = EVENT.getBytes(StandardCharsets.UTF_8);
        assertThat(IfoodSignature.matches(body, hmac(EVENT), SECRET)).isTrue();
        assertThat(IfoodSignature.matches(body, hmac(EVENT), "outro")).isFalse();
        assertThat(IfoodSignature.matches(body, "", SECRET)).isFalse();
        assertThat(IfoodSignature.matches(body, hmac(EVENT), null)).isFalse();
    }

    private static String hmac(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
