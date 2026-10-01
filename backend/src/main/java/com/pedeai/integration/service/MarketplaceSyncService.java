package com.pedeai.integration.service;

import com.pedeai.catalog.domain.PricingRule;
import com.pedeai.catalog.dto.CategoryResponse;
import com.pedeai.catalog.dto.OptionGroupResponse;
import com.pedeai.catalog.dto.ProductResponse;
import com.pedeai.catalog.event.CatalogChanged;
import com.pedeai.catalog.service.CategoryService;
import com.pedeai.catalog.service.OptionGroupService;
import com.pedeai.catalog.service.ProductService;
import com.pedeai.integration.domain.MarketplaceCategoryLink;
import com.pedeai.integration.domain.MarketplaceConnection;
import com.pedeai.integration.domain.MarketplaceSync;
import com.pedeai.integration.ifood.IfoodClient;
import com.pedeai.integration.ifood.IfoodItemPayload;
import com.pedeai.integration.repository.MarketplaceCategoryLinkRepository;
import com.pedeai.integration.repository.MarketplaceConnectionRepository;
import com.pedeai.integration.repository.MarketplaceSyncRepository;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.storage.ImageStorage;
import com.pedeai.store.dto.OpeningHoursResponse;
import com.pedeai.store.dto.StoreResponse;
import com.pedeai.store.event.StoreChannelsChanged;
import com.pedeai.store.service.StoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * O PedeAí manda no iFood (docs/05-integracoes.md#sincronização-com-o-ifood): a chave "Cardápio aberto" pausa e
 * reabre a loja lá, o horário daqui substitui o de lá e, com a sincronização do cardápio ligada no vínculo, cada
 * produto alterado vai em segundos. Tudo passa por uma fila com nova tentativa; o conteúdo é lido na hora do envio.
 */
@Service
public class MarketplaceSyncService {
    static final String PIZZA_RULE = "Produto com grupo cobrado pelo maior valor ou pela média (pizza meio a meio): "
            + "o iFood soma os complementos. Cadastre esse produto no iFood pelo modelo de pizza.";
    private static final int BATCH = 20;
    private static final Logger log = LoggerFactory.getLogger(MarketplaceSyncService.class);

    private final MarketplaceSyncRepository syncs;
    private final MarketplaceConnectionRepository connections;
    private final MarketplaceCategoryLinkRepository categoryLinks;
    private final StoreService storeService;
    private final ProductService productService;
    private final CategoryService categoryService;
    private final OptionGroupService optionGroupService;
    private final ImageStorage images;
    private final IfoodClient ifood;
    private final Platforms platforms;
    private final Clock clock;

    public MarketplaceSyncService(MarketplaceSyncRepository syncs, MarketplaceConnectionRepository connections,
                                  MarketplaceCategoryLinkRepository categoryLinks, StoreService storeService,
                                  ProductService productService, CategoryService categoryService,
                                  OptionGroupService optionGroupService, ImageStorage images, IfoodClient ifood,
                                  Platforms platforms, Clock clock) {
        this.syncs = syncs;
        this.connections = connections;
        this.categoryLinks = categoryLinks;
        this.storeService = storeService;
        this.productService = productService;
        this.categoryService = categoryService;
        this.optionGroupService = optionGroupService;
        this.images = images;
        this.ifood = ifood;
        this.platforms = platforms;
        this.clock = clock;
    }

    /** Produto, grupo de adicionais ou categoria mudou: os produtos afetados vão para o iFood. */
    @EventListener
    public void onCatalogChanged(CatalogChanged event) {
        List<MarketplaceConnection> targets = ifoodConnections(event.storeId()).stream()
                .filter(MarketplaceConnection::isCatalogSync).toList();
        if (targets.isEmpty()) {
            return;
        }
        Set<UUID> productIds = new LinkedHashSet<>(event.productIds());
        if (event.optionGroupId() != null || event.categoryId() != null) {
            productService.list(event.storeId(), null).stream()
                    .filter(product -> product.optionGroupIds().contains(event.optionGroupId())
                            || Objects.equals(product.categoryId(), event.categoryId()))
                    .forEach(product -> productIds.add(product.id()));
        }
        targets.forEach(connection -> productIds.forEach(id -> enqueue(connection, MarketplaceSync.Kind.ITEM, id)));
    }

    /** A chave do quadro, o horário ou o acréscimo de preço mudou. */
    @EventListener
    public void onStoreChannelsChanged(StoreChannelsChanged event) {
        for (MarketplaceConnection connection : ifoodConnections(event.storeId())) {
            if (event.status()) {
                enqueue(connection, MarketplaceSync.Kind.STORE_STATUS, null);
            }
            if (event.hours()) {
                enqueue(connection, MarketplaceSync.Kind.OPENING_HOURS, null);
            }
            if (event.prices() && connection.isCatalogSync()) {
                productService.list(event.storeId(), null)
                        .forEach(product -> enqueue(connection, MarketplaceSync.Kind.ITEM, product.id()));
            }
        }
    }

    /**
     * "Enviar tudo": o horário e, com a sincronização ligada, o cardápio inteiro. A pausa não entra: ela só vai quando
     * a chave muda, para ligar o iFood não fechar a loja de quem não usa o cardápio digital (que começa fechado).
     */
    @Transactional
    public int syncAll(UUID storeId, UUID connectionId) {
        MarketplaceConnection connection = connections.findByIdAndStoreId(connectionId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(ConnectionService.NOT_FOUND));
        int queued = enqueue(connection, MarketplaceSync.Kind.OPENING_HOURS, null) ? 1 : 0;
        if (connection.isCatalogSync()) {
            for (ProductResponse product : productService.list(storeId, null)) {
                queued += enqueue(connection, MarketplaceSync.Kind.ITEM, product.id()) ? 1 : 0;
            }
        }
        return queued;
    }

    @Transactional(readOnly = true)
    public List<UUID> due() {
        return syncs.findAllByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(MarketplaceSync.Status.PENDING,
                Instant.now(clock), PageRequest.of(0, BATCH)).stream().map(MarketplaceSync::getId).toList();
    }

    /** Um envio, na sua transação: o erro de um produto não segura os outros. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(UUID syncId) {
        MarketplaceSync sync = syncs.findById(syncId).orElseThrow();
        Instant now = Instant.now(clock);
        if (sync.getStatus() != MarketplaceSync.Status.PENDING) {
            return;
        }
        MarketplaceConnection connection = connections.findById(sync.getConnectionId()).orElseThrow();
        if (connection.getStatus() != MarketplaceConnection.Status.ACTIVE) {
            sync.skipped("Integração pausada na loja.", now);
            return;
        }
        if (!platforms.configured(connection.getProvider())) {
            if (platforms.simulator(connection.getProvider())) {
                sync.done(now);
            } else {
                sync.skipped("Integração com o iFood desligada no servidor.", now);
            }
            return;
        }
        try {
            switch (sync.getKind()) {
                case STORE_STATUS -> sendStatus(connection, now);
                case OPENING_HOURS -> {
                    if (!sendHours(connection)) {
                        sync.skipped("Sem horário no PedeAí: o horário do iFood fica como está.", now);
                        return;
                    }
                }
                case ITEM -> {
                    ProductResponse product = productService.get(connection.getStoreId(), sync.getReferenceId());
                    if (!sellable(product) && !syncs.existsByConnectionIdAndKindAndReferenceIdAndStatus(
                            connection.getId(), MarketplaceSync.Kind.ITEM, product.id(), MarketplaceSync.Status.DONE)) {
                        sync.skipped("Não vende no iFood e nunca foi para lá.", now);
                        return;
                    }
                    Optional<String> problem = sendItem(connection, product);
                    if (problem.isPresent()) {
                        sync.rejected(problem.get(), now);
                        connection.failed(problem.get(), now);
                        return;
                    }
                }
            }
            sync.done(now);
        } catch (IfoodClient.IfoodApiException e) {
            if (e.retryable()) {
                sync.retryLater(e.getMessage(), now);
            } else {
                sync.rejected(e.getMessage(), now);
                connection.failed("O iFood recusou uma atualização do cardápio: " + e.getMessage(), now);
            }
        }
    }

    /** Erro fora do iFood (foto no R2, rede): tenta de novo mais tarde, como um 5xx. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryLater(UUID syncId, String error) {
        syncs.findById(syncId).ifPresent(sync -> sync.retryLater(error, Instant.now(clock)));
    }

    private void sendStatus(MarketplaceConnection connection, Instant now) {
        StoreResponse store = storeService.get(connection.getStoreId());
        if (!store.menuOpen() && connection.getInterruptionId() == null) {
            connection.paused(ifood.pause(connection.getExternalMerchantId(), now), now);
        } else if (store.menuOpen() && connection.getInterruptionId() != null) {
            try {
                ifood.resume(connection.getExternalMerchantId(), connection.getInterruptionId());
            } catch (IfoodClient.IfoodApiException e) {
                if (e.status() != 404) { // 404: a pausa já tinha acabado sozinha (12 h)
                    throw e;
                }
            }
            connection.paused(null, now);
        }
    }

    private boolean sendHours(MarketplaceConnection connection) {
        List<OpeningHoursResponse> hours = storeService.get(connection.getStoreId()).openingHours();
        if (hours.isEmpty()) {
            return false;
        }
        ifood.openingHours(connection.getExternalMerchantId(), hours.stream().map(MarketplaceSyncService::shift)
                .toList());
        return true;
    }

    /** iFood: dia em inglês, início e duração em minutos (passar da meia-noite é só uma duração maior). */
    static Map<String, Object> shift(OpeningHoursResponse hours) {
        long minutes = Duration.between(hours.opensAt(), hours.closesAt()).toMinutes();
        if (minutes <= 0) {
            minutes += 24 * 60;
        }
        return Map.of("dayOfWeek", DayOfWeek.of(hours.dayOfWeek()).name(), "start",
                hours.opensAt().format(DateTimeFormatter.ISO_LOCAL_TIME),
                "duration", minutes);
    }

    /** Envia o produto. Devolve o motivo quando ele não dá para mandar (e repetir não resolve). */
    private static boolean sellable(ProductResponse product) {
        return product.active() && product.sellOnIfood();
    }

    private Optional<String> sendItem(MarketplaceConnection connection, ProductResponse product) {
        UUID storeId = connection.getStoreId();
        Map<UUID, OptionGroupResponse> groupsById = new java.util.HashMap<>();
        optionGroupService.list(storeId).forEach(group -> groupsById.put(group.id(), group));
        List<OptionGroupResponse> groups = product.optionGroupIds().stream().map(groupsById::get)
                .filter(group -> group != null && group.active()).toList();
        if (sellable(product) && groups.stream().anyMatch(group -> group.pricingRule() != PricingRule.SUM)) {
            return Optional.of(product.name() + ": " + PIZZA_RULE);
        }
        String categoryId = categoryId(connection, product.categoryId());
        String imagePath = imagePath(connection, product);
        int markup = storeService.get(storeId).ifoodMarkupBp();
        ifood.putItem(connection.getExternalMerchantId(), IfoodItemPayload.build(product, categoryId, groups, markup,
                imagePath));
        return Optional.empty();
    }

    /** A categoria no catálogo do iFood: criada na primeira vez e lembrada. */
    private String categoryId(MarketplaceConnection connection, UUID categoryId) {
        var key = new MarketplaceCategoryLink.Key(connection.getId(), categoryId);
        return categoryLinks.findById(key).map(MarketplaceCategoryLink::getExternalId).orElseGet(() -> {
            CategoryResponse category = categoryService.get(connection.getStoreId(), categoryId);
            String catalogId = ifood.defaultCatalogId(connection.getExternalMerchantId());
            String external = ifood.createCategory(connection.getExternalMerchantId(), catalogId, category.name(),
                    category.id().toString());
            categoryLinks.save(new MarketplaceCategoryLink(connection.getId(), categoryId, external));
            return external;
        });
    }

    /** A foto vai uma vez por foto; trocou, vai de novo. Sem foto, nada. */
    private String imagePath(MarketplaceConnection connection, ProductResponse product) {
        if (product.imageUrl() == null) {
            return null;
        }
        Optional<String> sent = productService.ifoodImagePath(connection.getStoreId(), product.id());
        if (sent.isPresent()) {
            return sent.get();
        }
        try {
            ImageStorage.Image image = images.download(product.imageUrl());
            String path = ifood.uploadImage(connection.getExternalMerchantId(), image.bytes(), image.contentType());
            if (path != null) {
                productService.rememberIfoodImagePath(connection.getStoreId(), product.id(), path);
            }
            return path;
        } catch (RuntimeException e) {
            // Preço e disponibilidade não esperam a foto: o item vai sem ela e a foto tenta de novo no próximo envio.
            log.warn("Foto do produto {} não foi para o iFood: {}", product.id(), e.getMessage());
            return null;
        }
    }

    private List<MarketplaceConnection> ifoodConnections(UUID storeId) {
        return connections.findAllByStoreIdOrderByCreatedAtAsc(storeId).stream()
                .filter(connection -> connection.getProvider() == OrderSource.IFOOD
                        && connection.getStatus() == MarketplaceConnection.Status.ACTIVE)
                .toList();
    }

    /**
     * Já tem um envio pendente do mesmo item: esse já vai ler o estado mais novo.
     * ponytail: a fila guarda todo envio feito (o DONE diz que o item já foi ao iFood); se crescer demais, apagar os
     * antigos mantendo o último DONE de cada item.
     */
    private boolean enqueue(MarketplaceConnection connection, MarketplaceSync.Kind kind, UUID referenceId) {
        boolean pending = referenceId == null
                ? syncs.existsByConnectionIdAndKindAndReferenceIdIsNullAndStatus(connection.getId(), kind,
                MarketplaceSync.Status.PENDING)
                : syncs.existsByConnectionIdAndKindAndReferenceIdAndStatus(connection.getId(), kind, referenceId,
                MarketplaceSync.Status.PENDING);
        if (pending) {
            return false;
        }
        syncs.save(new MarketplaceSync(connection.getStoreId(), connection.getId(), kind, referenceId,
                Instant.now(clock)));
        return true;
    }
}
