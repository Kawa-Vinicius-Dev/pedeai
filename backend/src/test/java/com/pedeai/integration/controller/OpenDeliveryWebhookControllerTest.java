package com.pedeai.integration.controller;

import com.pedeai.integration.config.OpenDeliveryProperties;
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
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OpenDeliveryWebhookController.class)
@Import({SecurityConfig.class, TimeConfig.class, OpenDeliveryWebhookControllerTest.Apps.class})
class OpenDeliveryWebhookControllerTest {
    private static final String SECRET = "segredo-99";
    private static final String EVENT = """
            {"eventId":"evt-1","eventType":"CREATED","orderId":"ord-1","orderURL":"https://app/v1/orders/ord-1",
             "createdAt":"2026-10-01T12:00:00Z"}""";

    @TestConfiguration
    static class Apps {
        @Bean
        OpenDeliveryProperties openDeliveryProperties() {
            return new OpenDeliveryProperties(false, List.of(new OpenDeliveryProperties.App(
                    OrderSource.NINETY_NINE_FOOD, "99Food", "https://api.99food.test", "cliente", SECRET, "app-99",
                    "/v1/events:polling")));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InboundService inbound;

    @Test
    void signedEventFromAKnownAppGoesToTheInboxWithTheMerchant() throws Exception {
        mockMvc.perform(post(OpenDeliveryWebhookController.PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(EVENT).header("X-App-Id", "app-99").header("X-App-MerchantId", "loja-7")
                        .header("X-App-Signature", hmac(EVENT)))
                .andExpect(status().isOk());

        verify(inbound).record(eq(OrderSource.NINETY_NINE_FOOD), argThat(event ->
                event.path("id").asString().equals("evt-1") && event.path("code").asString().equals("CREATED")
                        && event.path("merchantId").asString().equals("loja-7")));
    }

    @Test
    void wrongSignatureOrUnknownAppRecordsNothing() throws Exception {
        mockMvc.perform(post(OpenDeliveryWebhookController.PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(EVENT).header("X-App-Id", "app-99").header("X-App-Signature", hmac("outro corpo")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(OpenDeliveryWebhookController.PATH).contentType(MediaType.APPLICATION_JSON)
                        .content(EVENT).header("X-App-Id", "app-desconhecido").header("X-App-Signature", hmac(EVENT)))
                .andExpect(status().isNotFound());
        verify(inbound, never()).record(any(), any());
    }

    private static String hmac(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
