package com.pedeai.integration.controller;

import com.pedeai.integration.config.IfoodProperties;
import com.pedeai.integration.ifood.IfoodSignature;
import com.pedeai.integration.service.InboundService;
import com.pedeai.order.domain.OrderSource;
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
 * Webhook do iFood (docs/05-integracoes.md#fluxo-de-um-evento): confere a assinatura no corpo cru, grava no inbox e
 * responde 202 na hora. O processamento é o mesmo do polling, que continua rodando como contingência.
 */
@Hidden
@RestController
public class IfoodWebhookController {
    public static final String PATH = "/api/integrations/ifood/webhook";
    static final int MAX_BODY_BYTES = 1_000_000;
    private static final Logger log = LoggerFactory.getLogger(IfoodWebhookController.class);

    private final IfoodProperties properties;
    private final InboundService inbound;
    private final ObjectMapper json;

    public IfoodWebhookController(IfoodProperties properties, InboundService inbound, ObjectMapper json) {
        this.properties = properties;
        this.inbound = inbound;
        this.json = json;
    }

    @PostMapping(PATH)
    public ResponseEntity<Void> receive(@RequestBody(required = false) byte[] body,
                                        @RequestHeader(name = "X-IFood-Signature", required = false)
                                        String signature) {
        if (!properties.configured()) {
            return ResponseEntity.notFound().build();
        }
        if (body == null || body.length > MAX_BODY_BYTES
                || !IfoodSignature.matches(body, signature, properties.clientSecret())) {
            // Nada é gravado: quem não tem o client secret não injeta pedido.
            log.warn("Webhook do iFood recusado: assinatura inválida ou corpo grande demais.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        JsonNode root;
        try {
            root = json.readTree(body);
        } catch (JacksonException e) {
            return ResponseEntity.badRequest().build();
        }
        if (root.isArray()) {
            root.forEach(event -> inbound.record(OrderSource.IFOOD, event));
        } else {
            inbound.record(OrderSource.IFOOD, root);
        }
        return ResponseEntity.accepted().build();
    }
}
