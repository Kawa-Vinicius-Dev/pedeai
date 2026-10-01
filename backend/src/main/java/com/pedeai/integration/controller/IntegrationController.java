package com.pedeai.integration.controller;

import com.pedeai.catalog.dto.CatalogImportResponse;
import com.pedeai.integration.dto.CatalogImportRequest;
import com.pedeai.integration.dto.ConnectionRequest;
import com.pedeai.integration.dto.ConnectionResponse;
import com.pedeai.integration.dto.ConnectionUpdateRequest;
import com.pedeai.integration.dto.IfoodSetupResponse;
import com.pedeai.integration.dto.MerchantResponse;
import com.pedeai.integration.dto.PlatformResponse;
import com.pedeai.integration.service.ConnectionService;
import com.pedeai.integration.service.IfoodCatalogImportService;
import com.pedeai.integration.service.SimulatorService;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Permissions;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Integrações da loja com marketplaces (hoje, o iFood). */
@RestController
@RequestMapping("/api/integrations")
@PreAuthorize(Permissions.MANAGE_SETTINGS)
public class IntegrationController {
    private final ConnectionService connectionService;
    private final SimulatorService simulatorService;
    private final IfoodCatalogImportService ifoodCatalogImportService;

    public IntegrationController(ConnectionService connectionService, SimulatorService simulatorService,
                                 IfoodCatalogImportService ifoodCatalogImportService) {
        this.connectionService = connectionService;
        this.simulatorService = simulatorService;
        this.ifoodCatalogImportService = ifoodCatalogImportService;
    }

    /** Se o servidor tem as credenciais do iFood, e se o simulador está ligado. */
    @GetMapping("/ifood/setup")
    public IfoodSetupResponse setup() {
        return connectionService.setup();
    }

    /** iFood, 99Food e o app Open Delivery: quais estão disponíveis neste servidor. */
    @GetMapping("/platforms")
    public List<PlatformResponse> platforms() {
        return connectionService.platforms();
    }

    @GetMapping
    public List<ConnectionResponse> list(CurrentUser user) {
        return connectionService.list(user.storeId());
    }

    /** Merchants que deram permissão ao PedeAí no Portal do Parceiro do iFood. */
    @GetMapping("/ifood/merchants")
    public List<MerchantResponse> merchants() {
        return connectionService.merchants();
    }

    @PostMapping
    public ResponseEntity<ConnectionResponse> connect(CurrentUser user, @Valid @RequestBody ConnectionRequest request) {
        ConnectionResponse created = connectionService.connect(user.storeId(), request);
        return ResponseEntity.created(URI.create("/api/integrations/" + created.id())).body(created);
    }

    @PatchMapping("/{id}")
    public ConnectionResponse update(CurrentUser user, @PathVariable UUID id,
                                     @Valid @RequestBody ConnectionUpdateRequest request) {
        return connectionService.update(user.storeId(), id, request);
    }

    /** Traz o cardápio da loja no iFood para o PedeAí. Com {@code dryRun}, só mostra o que seria feito. */
    @PostMapping("/{id}/catalog-import")
    public CatalogImportResponse importCatalog(CurrentUser user, @PathVariable UUID id,
                                               @Valid @RequestBody CatalogImportRequest request) {
        return ifoodCatalogImportService.importCatalog(user.storeId(), id, request.dryRun());
    }

    /** Só com o simulador ligado: injeta um pedido de teste do "iFood" no mesmo caminho do pedido real. */
    @PostMapping("/{id}/simulated-orders")
    public ResponseEntity<Map<String, String>> simulate(CurrentUser user, @PathVariable UUID id) {
        return ResponseEntity.accepted().body(Map.of("externalOrderId",
                simulatorService.simulateOrder(user.storeId(), id)));
    }
}
