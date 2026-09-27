package com.pedeai.printing.controller;

import com.pedeai.printing.dto.PrintAlertResponse;
import com.pedeai.printing.dto.PrintJobResponse;
import com.pedeai.printing.dto.ReprintRequest;
import com.pedeai.printing.service.PrintJobService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Painel de impressões e reimpressão. A cozinha só mexe com tickets de produção (regra no serviço). */
@RestController
@PreAuthorize(Permissions.ADVANCE_ORDERS)
public class PrintJobController {
    private final PrintJobService printJobService;

    public PrintJobController(PrintJobService printJobService) {
        this.printJobService = printJobService;
    }

    /** Impressões das últimas 24 h. */
    @GetMapping("/api/print-jobs")
    public List<PrintJobResponse> recent(CurrentUser user) {
        return printJobService.recent(user.storeId());
    }

    /** Para a faixa de alerta de todas as telas. */
    @GetMapping("/api/print-alerts")
    public List<PrintAlertResponse> alerts(CurrentUser user) {
        return printJobService.alerts(user.storeId());
    }

    /** Imprimir de novo o que falhou, ficou incerto ou expirou. Sem {@code printerId}, na mesma impressora. */
    @PostMapping("/api/print-jobs/{id}/retry")
    public PrintJobResponse retry(CurrentUser user, @PathVariable UUID id,
                                  @RequestParam(required = false) UUID printerId) {
        return printJobService.retry(user, id, printerId);
    }

    @PostMapping("/api/orders/{orderId}/print-jobs")
    @ResponseStatus(HttpStatus.CREATED)
    public PrintJobResponse reprint(CurrentUser user, @PathVariable UUID orderId,
                                    @Valid @RequestBody ReprintRequest request,
                                    @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return printJobService.reprint(user, orderId, request, idempotencyKey);
    }
}
