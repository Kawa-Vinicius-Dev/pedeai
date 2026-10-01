package com.pedeai.integration.controller;

import com.pedeai.integration.config.OpenDeliveryProperties;
import com.pedeai.integration.ifood.IfoodSignature;
import com.pedeai.integration.opendelivery.OpenDeliveryEvents;
import com.pedeai.integration.service.InboundService;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Webhook Open Delivery: o app é reconhecido pelo {@code X-App-Id}, a assinatura {@code X-App-Signature} (HMAC-SHA256
 * hexadecimal do corpo cru com o client secret do app) é conferida, e o evento vai para o mesmo inbox do polling.
 * Responde 200 sem corpo, como a especificação pede (webhook não tem acknowledgment).
 */
@Hidden
@RestController
public class OpenDeliveryWebhookController {
    public static final String PATH = "/api/integrations/opendelivery/webhook";
    static final int MAX_BODY_BYTES = 1_000_000;
    private static final Logger log = LoggerFactory.getLogger(OpenDeliveryWebhookController.class);

    private final OpenDeliveryProperties properties;
    private final InboundService inbound;
    private final ObjectMapper json;

    public OpenDeliveryWebhookController(OpenDeliveryProperties properties, InboundService inbound, ObjectMapper json) {
        this.properties = properties;
        this.inbound = inbound;
        this.json = json;
    }

    @PostMapping(PATH)
    public ResponseEntity<Void> receive(@RequestBody(required = false) byte[] body,
                                        @RequestHeader(name = "X-App-Id", required = false) String appId,
                                        @RequestHeader(name = "X-App-MerchantId", required = false) String merchantId,
                                        @RequestHeader(name = "X-App-Signature", required = false) String signature) {
        var app = properties.appById(appId);
        if (app.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (body == null || body.length > MAX_BODY_BYTES
                || !IfoodSignature.matches(body, signature, app.get().clientSecret())) {
            log.warn("Webhook Open Delivery recusado ({}): assinatura inválida ou corpo grande demais.", appId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (JacksonException e) {
            return ResponseEntity.badRequest().build();
        }
        if (root.isArray()) {
            root.forEach(event -> inbound.record(app.get().provider(), OpenDeliveryEvents.toInbox(json, event,
                    merchantId)));
        } else {
            inbound.record(app.get().provider(), OpenDeliveryEvents.toInbox(json, root, merchantId));
        }
        return ResponseEntity.ok().build();
    }
}
