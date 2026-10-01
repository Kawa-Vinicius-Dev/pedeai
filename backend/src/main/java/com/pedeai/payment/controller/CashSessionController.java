package com.pedeai.payment.controller;

import com.pedeai.payment.dto.CashMovementRequest;
import com.pedeai.payment.dto.CashSessionResponse;
import com.pedeai.payment.dto.CashSessionSummaryResponse;
import com.pedeai.payment.dto.CloseCashSessionRequest;
import com.pedeai.payment.dto.OpenCashSessionRequest;
import com.pedeai.payment.service.CashSessionService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import com.pedeai.shared.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/cash-sessions")
@PreAuthorize(Permissions.MANAGE_CASH)
public class CashSessionController {
    private final CashSessionService cashSessionService;

    public CashSessionController(CashSessionService cashSessionService) {
        this.cashSessionService = cashSessionService;
    }

    /** O caixa aberto, com o esperado ao vivo. 204 quando não há caixa aberto. */
    @GetMapping("/current")
    public ResponseEntity<CashSessionResponse> current(CurrentUser user) {
        return cashSessionService.current(user.storeId()).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @GetMapping
    public PageResponse<CashSessionSummaryResponse> list(CurrentUser user,
                                                         @RequestParam(defaultValue = "0") @Min(0) int page,
                                                         @RequestParam(defaultValue = "20") @Min(1) @Max(100)
                                                         int size) {
        return cashSessionService.list(user.storeId(), page, size);
    }

    @GetMapping("/{id}")
    public CashSessionResponse get(CurrentUser user, @PathVariable UUID id) {
        return cashSessionService.get(user.storeId(), id);
    }

    @PostMapping
    public ResponseEntity<CashSessionResponse> open(CurrentUser user,
                                                    @Valid @RequestBody OpenCashSessionRequest request) {
        CashSessionResponse created = cashSessionService.open(user, request);
        return ResponseEntity.created(URI.create("/api/cash-sessions/" + created.id())).body(created);
    }

    /** Sangria ou suprimento. Devolve o caixa atualizado. */
    @PostMapping("/{id}/movements")
    public ResponseEntity<CashSessionResponse> addMovement(CurrentUser user, @PathVariable UUID id,
                                                           @Valid @RequestBody CashMovementRequest request) {
        CashSessionResponse session = cashSessionService.addMovement(user, id, request);
        return ResponseEntity.created(URI.create("/api/cash-sessions/" + id)).body(session);
    }

    /** Fecha o caixa com a contagem de cada forma de pagamento. */
    @PatchMapping("/{id}")
    public CashSessionResponse close(CurrentUser user, @PathVariable UUID id,
                                     @Valid @RequestBody CloseCashSessionRequest request) {
        return cashSessionService.close(user, id, request);
    }
}
