package com.pedeai.integration.service;

import com.pedeai.integration.config.IfoodProperties;
import com.pedeai.integration.domain.MarketplaceConnection;
import com.pedeai.integration.domain.OutboundAction;
import com.pedeai.integration.dto.ConnectionRequest;
import com.pedeai.integration.dto.ConnectionResponse;
import com.pedeai.integration.dto.ConnectionUpdateRequest;
import com.pedeai.integration.dto.IfoodSetupResponse;
import com.pedeai.integration.dto.MerchantResponse;
import com.pedeai.integration.ifood.IfoodClient;
import com.pedeai.integration.repository.MarketplaceConnectionRepository;
import com.pedeai.integration.repository.OutboundActionRepository;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Vínculo da loja com o merchant do iFood (docs/05-integracoes.md#autenticação-e-vínculo-da-loja). */
@Service
public class ConnectionService {
    static final String NOT_CONFIGURED =
            "A integração com o iFood ainda não está configurada no servidor (credenciais do iFood Developer).";
    static final String MERCHANT_NOT_ALLOWED = "Este merchant não deu permissão ao PedeAí. No Portal do Parceiro do "
            + "iFood, aceite a permissão do aplicativo PedeAí e tente de novo em alguns minutos.";
    static final String MERCHANT_TAKEN = "Este merchant do iFood já está ligado a uma loja.";
    static final String NOT_FOUND = "Integração não encontrada.";

    private final MarketplaceConnectionRepository repository;
    private final OutboundActionRepository actionRepository;
    private final IfoodClient ifood;
    private final IfoodProperties properties;
    private final Clock clock;

    public ConnectionService(MarketplaceConnectionRepository repository, OutboundActionRepository actionRepository,
                             IfoodClient ifood, IfoodProperties properties, Clock clock) {
        this.repository = repository;
        this.actionRepository = actionRepository;
        this.ifood = ifood;
        this.properties = properties;
        this.clock = clock;
    }

    public IfoodSetupResponse setup() {
        return new IfoodSetupResponse(properties.configured(), properties.simulator());
    }

    @Transactional(readOnly = true)
    public List<ConnectionResponse> list(UUID storeId) {
        long failed = actionRepository.countByStoreIdAndStatus(storeId, OutboundAction.Status.FAILED);
        return repository.findAllByStoreIdOrderByCreatedAtAsc(storeId).stream()
                .map(connection -> ConnectionResponse.from(connection, failed))
                .toList();
    }

    /** Merchants que a credencial do PedeAí alcança: os que aceitaram a permissão no Portal do Parceiro. */
    public List<MerchantResponse> merchants() {
        requireConfigured();
        return ifood.merchants().stream().map(merchant -> new MerchantResponse(merchant.id(), merchant.name()))
                .toList();
    }

    /**
     * Liga a loja ao merchant. Com credenciais, o merchant precisa estar entre os que deram permissão. Sem
     * credenciais, só o simulador aceita (merchant de mentira, para demonstrar).
     */
    @Transactional
    public ConnectionResponse connect(UUID storeId, ConnectionRequest request) {
        String merchantId = request.externalMerchantId().trim();
        String name;
        if (properties.configured()) {
            name = ifood.merchants().stream().filter(merchant -> merchant.id().equals(merchantId))
                    .map(IfoodClient.Merchant::name).findFirst()
                    .orElseThrow(() -> new BusinessRuleException(MERCHANT_NOT_ALLOWED));
        } else if (properties.simulator()) {
            name = "Loja simulada";
        } else {
            throw new BusinessRuleException(NOT_CONFIGURED);
        }
        if (repository.findByProviderAndExternalMerchantId(OrderSource.IFOOD, merchantId).isPresent()) {
            throw new ConflictException(MERCHANT_TAKEN);
        }
        MarketplaceConnection connection = repository.save(new MarketplaceConnection(storeId, OrderSource.IFOOD,
                merchantId, name, request.autoConfirm(), Instant.now(clock)));
        return ConnectionResponse.from(connection, 0);
    }

    @Transactional
    public ConnectionResponse update(UUID storeId, UUID id, ConnectionUpdateRequest request) {
        MarketplaceConnection connection = find(storeId, id);
        connection.update(request.status(), request.autoConfirm(), Instant.now(clock));
        return ConnectionResponse.from(connection,
                actionRepository.countByStoreIdAndStatus(storeId, OutboundAction.Status.FAILED));
    }

    MarketplaceConnection find(UUID storeId, UUID id) {
        return repository.findByIdAndStoreId(id, storeId).orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new BusinessRuleException(NOT_CONFIGURED);
        }
    }
}
