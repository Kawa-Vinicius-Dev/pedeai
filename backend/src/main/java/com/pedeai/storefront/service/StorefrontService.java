package com.pedeai.storefront.service;

import com.pedeai.catalog.dto.CategoryResponse;
import com.pedeai.catalog.dto.OptionGroupResponse;
import com.pedeai.catalog.dto.PriceQuoteRequest;
import com.pedeai.catalog.dto.PriceQuoteResponse;
import com.pedeai.catalog.dto.ProductResponse;
import com.pedeai.catalog.service.CategoryService;
import com.pedeai.catalog.service.OptionGroupService;
import com.pedeai.catalog.service.ProductService;
import com.pedeai.customer.dto.AddressRequest;
import com.pedeai.customer.dto.DeliveryZoneResponse;
import com.pedeai.customer.service.DeliveryZoneService;
import com.pedeai.order.domain.ItemStatus;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.order.domain.OrderType;
import com.pedeai.order.dto.CreateOrderRequest;
import com.pedeai.order.dto.OrderCustomerRequest;
import com.pedeai.order.dto.OrderItemOptionResponse;
import com.pedeai.order.dto.OrderItemRequest;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.dto.TrackedOrder;
import com.pedeai.order.service.OrderService;
import com.pedeai.payment.domain.PaymentMethodType;
import com.pedeai.payment.dto.PaymentMethodResponse;
import com.pedeai.payment.service.PaymentMethodService;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.exception.TooManyRequestsException;
import com.pedeai.shared.security.AttemptLimiter;
import com.pedeai.store.dto.StoreResponse;
import com.pedeai.store.service.StoreService;
import com.pedeai.storefront.dto.MenuCategoryResponse;
import com.pedeai.storefront.dto.MenuDeliveryZoneResponse;
import com.pedeai.storefront.dto.MenuOrderRequest;
import com.pedeai.storefront.dto.MenuOrderResponse;
import com.pedeai.storefront.dto.MenuPaymentMethodResponse;
import com.pedeai.storefront.dto.MenuProductResponse;
import com.pedeai.storefront.dto.OrderTrackingResponse;
import com.pedeai.storefront.dto.PartnerOrderRequest;
import com.pedeai.storefront.dto.PartnerOrderResponse;
import com.pedeai.storefront.dto.StorefrontResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Cardápio digital PedeAí (docs/06-roadmap.md#etapa-8--cardápio-digital-pedeaí): o cliente vê o cardápio da loja,
 * monta o pedido e acompanha pelo código. Público, sem login. O pedido nasce recebido e a loja aceita no quadro.
 */
@Service
public class StorefrontService {
    static final String CLOSED = "A loja não está recebendo pedidos pelo cardápio agora.";
    static final String ONLY_DELIVERY_OR_TAKEOUT = "Escolha entrega ou retirada.";
    static final String ADDRESS_REQUIRED = "Informe o endereço de entrega.";
    static final String NEIGHBORHOOD_NOT_SERVED = "A loja não entrega neste bairro. Escolha outro bairro ou retire no "
            + "local.";
    static final String INVALID_PAYMENT = "Esta forma de pagamento não está disponível no cardápio.";
    static final String CHANGE_ONLY_CASH = "Troco só existe para pagamento em dinheiro.";
    static final String PRODUCT_UNAVAILABLE = "Um dos produtos não está mais no cardápio. Atualize a página.";
    static final String ORDER_NOT_FOUND = "Pedido não encontrado.";
    static final String TOO_MANY_ORDERS = "Muitos pedidos deste aparelho em pouco tempo. Tente de novo em alguns "
            + "minutos.";
    /** Contra pedido falso em massa. Generoso: vários clientes podem sair pelo mesmo IP da operadora. */
    static final int MAX_ORDERS_PER_ADDRESS = 15;
    static final Duration ORDER_WINDOW = Duration.ofMinutes(10);
    /** Um sistema de terceiros manda os pedidos de muitos clientes: o limite é por loja, e maior. */
    static final int MAX_PARTNER_ORDERS = 300;

    private final StoreService storeService;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final OptionGroupService optionGroupService;
    private final DeliveryZoneService deliveryZoneService;
    private final PaymentMethodService paymentMethodService;
    private final OrderService orderService;
    private final AttemptLimiter orders;
    private final AttemptLimiter partnerOrders;

    public StorefrontService(StoreService storeService, CategoryService categoryService, ProductService productService,
                             OptionGroupService optionGroupService, DeliveryZoneService deliveryZoneService,
                             PaymentMethodService paymentMethodService, OrderService orderService, Clock clock) {
        this.storeService = storeService;
        this.categoryService = categoryService;
        this.productService = productService;
        this.optionGroupService = optionGroupService;
        this.deliveryZoneService = deliveryZoneService;
        this.paymentMethodService = paymentMethodService;
        this.orderService = orderService;
        this.orders = new AttemptLimiter(MAX_ORDERS_PER_ADDRESS, ORDER_WINDOW, clock);
        this.partnerOrders = new AttemptLimiter(MAX_PARTNER_ORDERS, ORDER_WINDOW, clock);
    }

    @Transactional(readOnly = true)
    public StorefrontResponse menu(String slug) {
        return menu(storeService.getBySlug(slug));
    }

    /** O mesmo cardápio, para a API de pedidos (a loja vem da chave). */
    @Transactional(readOnly = true)
    public StorefrontResponse menu(UUID storeId) {
        return menu(storeService.get(storeId));
    }

    private StorefrontResponse menu(StoreResponse store) {
        UUID storeId = store.id();
        Map<UUID, List<ProductResponse>> byCategory = productService.list(storeId, null).stream()
                .filter(ProductResponse::active)
                .collect(Collectors.groupingBy(ProductResponse::categoryId));
        List<MenuCategoryResponse> categories = categoryService.list(storeId).stream()
                .filter(CategoryResponse::active)
                .map(category -> new MenuCategoryResponse(category.id(), category.name(),
                        byCategory.getOrDefault(category.id(), List.of()).stream()
                                .map(product -> new MenuProductResponse(product.id(), product.name(),
                                        product.description(), product.priceCents(), product.optionGroupIds(),
                                        product.available()))
                                .toList()))
                .filter(category -> !category.products().isEmpty())
                .toList();
        Set<UUID> usedGroups = categories.stream().flatMap(category -> category.products().stream())
                .flatMap(product -> product.optionGroupIds().stream()).collect(Collectors.toSet());
        List<OptionGroupResponse> groups = optionGroupService.list(storeId).stream()
                .filter(group -> group.active() && usedGroups.contains(group.id()))
                .map(group -> new OptionGroupResponse(group.id(), group.name(), group.minChoices(),
                        group.maxChoices(), group.pricingRule(), true,
                        group.options().stream().filter(option -> option.active()).toList()))
                .toList();
        return new StorefrontResponse(store.name(), store.slug(), store.phone(),
                storeService.acceptingMenuOrders(store), store.openingHours(),
                zones(storeId).stream().map(zone -> new MenuDeliveryZoneResponse(zone.neighborhood(), zone.feeCents()))
                        .toList(),
                methods(storeId).stream().map(method -> new MenuPaymentMethodResponse(method.id(), method.name(),
                        method.type())).toList(),
                categories, groups);
    }

    /** Preço do item montado, com as mesmas regras do pedido: o cliente vê o valor antes de pôr no carrinho. */
    @Transactional(readOnly = true)
    public PriceQuoteResponse quote(String slug, UUID productId, PriceQuoteRequest request) {
        StoreResponse store = storeService.getBySlug(slug);
        if (!visibleProducts(store.id()).contains(productId)) {
            throw new ResourceNotFoundException(PRODUCT_UNAVAILABLE);
        }
        return productService.quote(store.id(), productId, request);
    }

    /** {@code clientKey}: o IP de quem pede, para o limite contra pedido falso em massa. */
    @Transactional
    public MenuOrderResponse placeOrder(String slug, MenuOrderRequest request, String clientKey) {
        StoreResponse store = storeService.getBySlug(slug);
        limit(orders, store.id() + ":" + clientKey);
        OrderResponse placed = place(store, request, OrderSource.DIGITAL_MENU, null);
        return new MenuOrderResponse(placed.number(), placed.trackingCode(), placed.totalCents());
    }

    /** Pedido pela API de pedidos. {@code created = false}: o mesmo {@code externalId} já tinha virado pedido. */
    public record PartnerOrder(PartnerOrderResponse order, boolean created) {
    }

    @Transactional
    public PartnerOrder placePartnerOrder(UUID storeId, PartnerOrderRequest request) {
        String externalId = request.externalId() == null || request.externalId().isBlank() ? null
                : request.externalId();
        if (externalId != null) {
            var existing = orderService.findExternal(storeId, OrderSource.API, externalId);
            if (existing.isPresent()) {
                return new PartnerOrder(PartnerOrderResponse.from(existing.get()), false);
            }
        }
        limit(partnerOrders, storeId.toString());
        StoreResponse store = storeService.get(storeId);
        return new PartnerOrder(PartnerOrderResponse.from(place(store, request.toMenuOrder(), OrderSource.API,
                externalId)), true);
    }

    @Transactional(readOnly = true)
    public PartnerOrderResponse partnerOrder(UUID storeId, UUID orderId) {
        OrderResponse order = orderService.get(storeId, orderId);
        if (order.source() != OrderSource.API) {
            // A chave da API só enxerga os pedidos que ela mesma criou, não o balcão nem os marketplaces.
            throw new ResourceNotFoundException(ORDER_NOT_FOUND);
        }
        return PartnerOrderResponse.from(order);
    }

    private static void limit(AttemptLimiter limiter, String key) {
        if (limiter.blocked(key)) {
            throw new TooManyRequestsException(TOO_MANY_ORDERS);
        }
        limiter.failed(key);
    }

    private OrderResponse place(StoreResponse store, MenuOrderRequest request, OrderSource source,
                                String externalId) {
        if (!storeService.acceptingMenuOrders(store)) {
            throw new BusinessRuleException(CLOSED);
        }
        UUID storeId = store.id();
        Set<UUID> visible = visibleProducts(storeId);
        if (request.items().stream().map(OrderItemRequest::productId).anyMatch(id -> !visible.contains(id))) {
            throw new BusinessRuleException(PRODUCT_UNAVAILABLE);
        }
        PaymentMethodResponse method = methods(storeId).stream()
                .filter(candidate -> candidate.id().equals(request.paymentMethodId()))
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException(INVALID_PAYMENT));
        if (request.changeForCents() != null && method.type() != PaymentMethodType.CASH) {
            throw new BusinessRuleException(CHANGE_ONLY_CASH);
        }

        AddressRequest address = null;
        long deliveryFee = 0;
        if (request.type() == OrderType.DELIVERY) {
            if (request.deliveryAddress() == null) {
                throw new BusinessRuleException(ADDRESS_REQUIRED);
            }
            String neighborhood = request.deliveryAddress().neighborhood().trim();
            DeliveryZoneResponse zone = zones(storeId).stream()
                    .filter(candidate -> candidate.neighborhood().equalsIgnoreCase(neighborhood))
                    .findFirst()
                    .orElseThrow(() -> new BusinessRuleException(NEIGHBORHOOD_NOT_SERVED));
            AddressRequest given = request.deliveryAddress();
            address = new AddressRequest(null, given.street(), given.number(), given.complement(), zone.neighborhood(),
                    given.city(), given.state(), given.postalCode(), given.reference());
            deliveryFee = zone.feeCents();
        } else if (request.type() != OrderType.TAKEOUT) {
            throw new BusinessRuleException(ONLY_DELIVERY_OR_TAKEOUT);
        }

        CreateOrderRequest order = new CreateOrderRequest(request.type(),
                new OrderCustomerRequest(request.customerName().trim(), request.customerPhone().trim()), address,
                request.items(), request.notes(), 0L, deliveryFee, List.of());
        return orderService.placeFromChannel(storeId, source, externalId, order, method.id(),
                request.changeForCents(), store.menuAutoConfirm());
    }

    @Transactional(readOnly = true)
    public OrderTrackingResponse track(String trackingCode) {
        TrackedOrder tracked = orderService.findByTrackingCode(trackingCode)
                .filter(found -> found.order().trackingCode() != null)
                .orElseThrow(() -> new ResourceNotFoundException(ORDER_NOT_FOUND));
        OrderResponse order = tracked.order();
        StoreResponse store = storeService.get(tracked.storeId());
        List<OrderTrackingResponse.TrackingItemResponse> items = order.items().stream()
                .filter(item -> item.status() == ItemStatus.ACTIVE)
                .map(item -> new OrderTrackingResponse.TrackingItemResponse(item.quantity(), item.name(),
                        details(item.options(), item.notes())))
                .toList();
        return new OrderTrackingResponse(order.number(), order.type(), order.status(), store.name(), store.slug(),
                store.phone(), items, order.subtotalCents(), order.deliveryFeeCents(), order.totalCents(),
                order.createdAt(), order.cancelReason());
    }

    /** Produtos ativos em categorias ativas: só esses podem ser pedidos pelo cardápio. */
    private Set<UUID> visibleProducts(UUID storeId) {
        Set<UUID> activeCategories = categoryService.list(storeId).stream().filter(CategoryResponse::active)
                .map(CategoryResponse::id).collect(Collectors.toSet());
        return productService.list(storeId, null).stream()
                .filter(product -> product.active() && activeCategories.contains(product.categoryId()))
                .map(ProductResponse::id)
                .collect(Collectors.toSet());
    }

    private List<DeliveryZoneResponse> zones(UUID storeId) {
        return deliveryZoneService.list(storeId).stream().filter(DeliveryZoneResponse::active).toList();
    }

    /** Ativas e pagas na loja: "Online iFood" e afins não fazem sentido no cardápio próprio. */
    private List<PaymentMethodResponse> methods(UUID storeId) {
        return paymentMethodService.list(storeId).stream()
                .filter(method -> method.active() && method.type() != PaymentMethodType.ONLINE)
                .toList();
    }

    /** "Calabresa, Quatro queijos · sem cebola". */
    private static String details(List<OrderItemOptionResponse> options, String notes) {
        List<String> parts = new ArrayList<>();
        if (!options.isEmpty()) {
            parts.add(options.stream()
                    .map(option -> option.quantity() > 1 ? option.quantity() + "x " + option.name() : option.name())
                    .collect(Collectors.joining(", ")));
        }
        if (notes != null) {
            parts.add(notes);
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }
}
