package com.pedeai.printing.controller;

import com.pedeai.printing.dto.CashReportPrintRequest;
import com.pedeai.printing.dto.PrintAlertResponse;
import com.pedeai.printing.dto.PrintJobResponse;
import com.pedeai.printing.dto.ReprintRequest;
import com.pedeai.printing.service.PrintJobService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import com.pedeai.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
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

    /** Impressões das últimas 24 h, da mais nova para a mais antiga. */
    @GetMapping("/api/print-jobs")
    public PageResponse<PrintJobResponse> recent(CurrentUser user,
                                                 @RequestParam(defaultValue = "0") @Min(0) int page,
                                                 @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return printJobService.recent(user.storeId(), page, size);
    }

    @GetMapping("/api/print-jobs/{id}")
    public PrintJobResponse get(CurrentUser user, @PathVariable UUID id) {
        return printJobService.get(user.storeId(), id);
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

    /** Reimpressão com a faixa REIMPRESSÃO. A mesma {@code Idempotency-Key} devolve a mesma impressão. */
    @PostMapping("/api/orders/{orderId}/print-jobs")
    public ResponseEntity<PrintJobResponse> reprint(CurrentUser user, @PathVariable UUID orderId,
                                                    @Valid @RequestBody ReprintRequest request,
                                                    @RequestHeader(name = "Idempotency-Key", required = false)
                                                    String idempotencyKey) {
        PrintJobResponse created = printJobService.reprint(user, orderId, request, idempotencyKey);
        return ResponseEntity.created(URI.create("/api/print-jobs/" + created.id())).body(created);
    }

    /** Relatório do caixa (parcial ou de fechamento) na impressora térmica. */
    @PostMapping("/api/cash-sessions/{sessionId}/print-jobs")
    @PreAuthorize(Permissions.MANAGE_CASH)
    public ResponseEntity<PrintJobResponse> printCashReport(CurrentUser user, @PathVariable UUID sessionId,
                                                            @Valid @RequestBody CashReportPrintRequest request,
                                                            @RequestHeader(name = "Idempotency-Key", required = false)
                                                            String idempotencyKey) {
        PrintJobResponse created = printJobService.printCashReport(user, sessionId, request.printerId(),
                idempotencyKey);
        return ResponseEntity.created(URI.create("/api/print-jobs/" + created.id())).body(created);
    }
}
