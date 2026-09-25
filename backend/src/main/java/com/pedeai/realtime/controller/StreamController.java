package com.pedeai.realtime.controller;

import com.pedeai.realtime.service.RealtimeBroadcaster;
import com.pedeai.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class StreamController {
    private final RealtimeBroadcaster broadcaster;

    public StreamController(RealtimeBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @GetMapping(path = "/api/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Avisos em tempo real da loja (SSE): order.created e order.status_changed")
    @ApiResponse(responseCode = "200",
            content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE, schema = @Schema(type = "string")))
    public SseEmitter stream(CurrentUser user, HttpServletResponse response) {
        // Proxies como o nginx seguram respostas em buffer; aqui cada aviso tem que sair na hora.
        response.setHeader("X-Accel-Buffering", "no");
        return broadcaster.subscribe(user.storeId());
    }
}
