package com.pedeai.printing.controller;

import com.pedeai.printing.domain.DocumentType;
import com.pedeai.printing.dto.TicketResponse;
import com.pedeai.printing.service.TicketService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders/{orderId}/tickets")
public class TicketController {
    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    /** Documento pronto para imprimir. {@code sectorId} é obrigatório no ticket de produção. */
    @GetMapping("/{documentType}")
    @PreAuthorize(Permissions.ADVANCE_ORDERS)
    public TicketResponse get(CurrentUser user, @PathVariable UUID orderId, @PathVariable DocumentType documentType,
                              @RequestParam(required = false) UUID sectorId,
                              @RequestParam(defaultValue = "48") @Min(24) @Max(64) int columns) {
        return ticketService.render(user, orderId, documentType, sectorId, columns);
    }
}
