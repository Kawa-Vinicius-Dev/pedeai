package com.pedeai.printing.controller;

import com.pedeai.printing.domain.DocumentType;
import com.pedeai.printing.dto.PrintJobResponse;
import com.pedeai.printing.dto.TicketResponse;
import com.pedeai.printing.service.PrintQueueService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders/{orderId}")
public class TicketController {
    private final TicketService ticketService;
    private final PrintQueueService printQueueService;

    public TicketController(TicketService ticketService, PrintQueueService printQueueService) {
        this.ticketService = ticketService;
        this.printQueueService = printQueueService;
    }

    /** Documento pronto para imprimir. {@code sectorId} é obrigatório no ticket de produção. */
    @GetMapping("/tickets/{documentType}")
    @PreAuthorize(Permissions.ADVANCE_ORDERS)
    public TicketResponse get(CurrentUser user, @PathVariable UUID orderId, @PathVariable DocumentType documentType,
                              @RequestParam(required = false) UUID sectorId,
                              @RequestParam(defaultValue = "48") @Min(24) @Max(64) int columns) {
        return ticketService.render(user, orderId, documentType, sectorId, columns);
    }

    /** O que a impressão automática fez com este pedido: onde foi, se saiu, e o erro se não saiu. */
    @GetMapping("/print-jobs")
    @PreAuthorize(Permissions.ADVANCE_ORDERS)
    public List<PrintJobResponse> printJobs(CurrentUser user, @PathVariable UUID orderId) {
        return printQueueService.forOrder(user.storeId(), orderId);
    }
}
