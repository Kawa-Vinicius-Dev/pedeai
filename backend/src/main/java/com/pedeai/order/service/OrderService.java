package com.pedeai.order.service;

import com.pedeai.catalog.dto.PriceQuoteRequest;
import com.pedeai.catalog.dto.PriceQuoteResponse;
import com.pedeai.catalog.service.ProductService;
import com.pedeai.customer.dto.AddressRequest;
import com.pedeai.customer.dto.CustomerLink;
import com.pedeai.customer.service.CustomerService;
import com.pedeai.order.domain.Actor;
import com.pedeai.order.domain.BusinessDay;
import com.pedeai.order.domain.DeliveryAddress;
import com.pedeai.order.domain.Order;
import com.pedeai.order.domain.OrderCustomer;
import com.pedeai.order.domain.OrderItem;
import com.pedeai.order.domain.OrderItemOption;
import com.pedeai.order.domain.OrderStatus;
import com.pedeai.order.domain.OrderStatusHistory;
import com.pedeai.order.domain.OrderType;
import com.pedeai.order.dto.CreateOrderRequest;
import com.pedeai.order.dto.OrderCustomerRequest;
import com.pedeai.order.dto.OrderItemRequest;
import com.pedeai.order.dto.OrderResponse;
import com.pedeai.order.dto.OrderStatusHistoryResponse;
import com.pedeai.order.dto.OrderSummaryResponse;
import com.pedeai.order.event.OrderCreated;
import com.pedeai.order.repository.OrderRepository;
import com.pedeai.order.repository.OrderSpecifications;
import com.pedeai.order.repository.OrderStatusHistoryRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ForbiddenOperationException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Role;
import com.pedeai.shared.text.Texts;
import com.pedeai.shared.web.PageResponse;
import com.pedeai.store.dto.StoreResponse;
import com.pedeai.store.service.StoreService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/** Lançamento e consulta de pedidos. As mudanças de status ficam no {@link OrderStatusService}. */
@Service
public class OrderService {
    static final String NOT_FOUND = "Pedido não encontrado.";
    private static final Set<OrderStatus> KITCHEN_QUEUE = EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.IN_PREPARATION);
    static final String DINE_IN_BY_TAB = "Pedido de mesa é lançado pela comanda.";
    static final String DELIVERY_NEEDS_CUSTOMER = "Para delivery, informe o nome e o telefone do cliente.";
    static final String DELIVERY_NEEDS_ADDRESS = "Para delivery, informe o endereço de entrega.";
    static final String TAKEOUT_WITHOUT_ADDRESS = "Pedido para retirada não tem endereço nem taxa de entrega.";
    static final String DISCOUNT_NEEDS_MANAGER = "Só gerente ou dono pode dar desconto.";
    static final String PRODUCT_NOT_FOUND = "Um dos produtos não está mais no cardápio.";

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final OrderNumberService numberService;
    private final ProductService productService;
    private final CustomerService customerService;
    private final StoreService storeService;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public OrderService(OrderRepository orderRepository, OrderStatusHistoryRepository historyRepository,
                        OrderNumberService numberService, ProductService productService,
                        CustomerService customerService, StoreService storeService,
                        ApplicationEventPublisher events, Clock clock) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.numberService = numberService;
        this.productService = productService;
        this.customerService = customerService;
        this.storeService = storeService;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public OrderResponse create(CurrentUser user, CreateOrderRequest request) {
        UUID storeId = user.storeId();
        validateShape(user, request);
        List<OrderItem> items = priceItems(storeId, request.items());
        OrderCustomer customer = recordCustomer(storeId, request);
        DeliveryAddress address = toDeliveryAddress(request.deliveryAddress());

        StoreResponse store = storeService.get(storeId);
        Instant now = Instant.now(clock);
        LocalDate businessDate = BusinessDay.of(now, ZoneId.of(store.timezone()), store.businessDayCutoff());
        int number = numberService.next(storeId, businessDate);

        Order order = Order.placeOwn(storeId, businessDate, number, request.type(), customer, address,
                Texts.trimToNull(request.notes()), items, request.discountCents(), request.deliveryFeeCents(),
                user.userId(), now);
        // O status inicial é aplicado antes de gravar: o pedido nasce com um INSERT só, na versão 0.
        List<OrderStatusHistory> timeline = new ArrayList<>();
        Actor actor = Actor.user(user.userId(), user.name());
        if (store.autoConfirmOwnOrders()) {
            order.advanceTo(OrderStatus.CONFIRMED, now);
            timeline.add(new OrderStatusHistory(order, null, OrderStatus.CONFIRMED, actor, null, now));
            if (store.startPreparationOnConfirm()) {
                order.advanceTo(OrderStatus.IN_PREPARATION, now);
                timeline.add(new OrderStatusHistory(order, OrderStatus.CONFIRMED, OrderStatus.IN_PREPARATION,
                        Actor.system(), null, now));
            }
        } else {
            timeline.add(new OrderStatusHistory(order, null, OrderStatus.RECEIVED, actor, null, now));
        }
        orderRepository.save(order);
        historyRepository.saveAll(timeline);
        orderRepository.flush();
        events.publishEvent(new OrderCreated(storeId, order.getId(), order.getNumber(), order.getStatus(),
                order.getVersion(), order.getTotalCents(), request.payments(), user.userId()));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID storeId, UUID id) {
        return OrderResponse.from(find(storeId, id));
    }

    /** Pedidos em andamento, do mais antigo para o mais novo: é o quadro de pedidos. */
    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> listActive(UUID storeId) {
        return orderRepository.findAllByStoreIdAndStatusInOrderByCreatedAtAsc(storeId, OrderStatus.ACTIVE).stream()
                .map(OrderSummaryResponse::from)
                .toList();
    }

    /**
     * Tela da cozinha: o que falta preparar, do mais antigo para o mais novo. Com {@code sectorId}, só os itens
     * daquele setor, e some o pedido que não tem nada para ele.
     */
    @Transactional(readOnly = true)
    public List<OrderResponse> listForKitchen(UUID storeId, UUID sectorId) {
        Predicate<OrderItem> forScreen = item -> item.isActive()
                && (sectorId == null || sectorId.equals(item.getSectorId()));
        return orderRepository.findAllByStoreIdAndStatusInOrderByCreatedAtAsc(storeId, KITCHEN_QUEUE).stream()
                .map(order -> OrderResponse.from(order, forScreen))
                .filter(order -> !order.items().isEmpty())
                .toList();
    }

    /** Histórico, do mais novo para o mais antigo. Filtros nulos não restringem. */
    @Transactional(readOnly = true)
    public PageResponse<OrderSummaryResponse> search(UUID storeId, LocalDate businessDate, OrderStatus status,
                                                     OrderType type, String term, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.from(orderRepository.findAll(
                OrderSpecifications.matching(storeId, businessDate, status, type, Texts.trimToNull(term)), pageable)
                .map(OrderSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public List<OrderStatusHistoryResponse> history(UUID storeId, UUID orderId) {
        find(storeId, orderId);
        return historyRepository.findAllByStoreIdAndOrderIdOrderByCreatedAtAscIdAsc(storeId, orderId).stream()
                .map(OrderStatusHistoryResponse::from)
                .toList();
    }

    private void validateShape(CurrentUser user, CreateOrderRequest request) {
        if (request.type() == OrderType.DINE_IN) {
            throw new BusinessRuleException(DINE_IN_BY_TAB);
        }
        if (request.discountCents() > 0 && user.role() != Role.OWNER && user.role() != Role.MANAGER) {
            throw new ForbiddenOperationException(DISCOUNT_NEEDS_MANAGER);
        }
        if (request.type() == OrderType.DELIVERY) {
            OrderCustomerRequest customer = request.customer();
            if (customer == null || Texts.trimToNull(customer.phone()) == null) {
                throw new BusinessRuleException(DELIVERY_NEEDS_CUSTOMER);
            }
            if (request.deliveryAddress() == null) {
                throw new BusinessRuleException(DELIVERY_NEEDS_ADDRESS);
            }
        } else if (request.deliveryAddress() != null || request.deliveryFeeCents() > 0) {
            throw new BusinessRuleException(TAKEOUT_WITHOUT_ADDRESS);
        }
    }

    /** Preço de cada item pelo cardápio, com a mesma conta do simulador. Nada do preço vem da tela. */
    private List<OrderItem> priceItems(UUID storeId, List<OrderItemRequest> requests) {
        List<OrderItem> items = new ArrayList<>();
        for (OrderItemRequest request : requests) {
            PriceQuoteResponse quote;
            try {
                quote = productService.quote(storeId, request.productId(),
                        new PriceQuoteRequest(request.quantity(), request.options()));
            } catch (ResourceNotFoundException missing) {
                throw new BusinessRuleException(PRODUCT_NOT_FOUND);
            }
            List<OrderItemOption> options = new ArrayList<>();
            for (PriceQuoteResponse.QuotedOptionResponse option : quote.options()) {
                options.add(new OrderItemOption(storeId, option.optionId(), option.groupName(), option.name(),
                        option.code(), option.quantity(), option.unitPriceCents(), options.size()));
            }
            items.add(new OrderItem(storeId, quote.productId(), quote.code(), quote.name(), quote.sectorId(),
                    quote.quantity(), quote.basePriceCents(), quote.optionsPriceCents(),
                    Texts.trimToNull(request.notes()), options, items.size()));
        }
        return items;
    }

    /** Com telefone, o cliente fica gravado na loja (e o endereço, na entrega). Só com nome, fica só no pedido. */
    private OrderCustomer recordCustomer(UUID storeId, CreateOrderRequest request) {
        OrderCustomerRequest customer = request.customer();
        if (customer == null) {
            return null;
        }
        String name = customer.name().trim();
        if (Texts.trimToNull(customer.phone()) == null) {
            return new OrderCustomer(null, name, null);
        }
        CustomerLink link = customerService.recordFromOrder(storeId, name, customer.phone(),
                request.deliveryAddress());
        return new OrderCustomer(link.customerId(), name, link.phone());
    }

    private static DeliveryAddress toDeliveryAddress(AddressRequest request) {
        if (request == null) {
            return null;
        }
        var draft = request.toDraft();
        return new DeliveryAddress(draft.street(), draft.number(), draft.complement(), draft.neighborhood(),
                draft.city(), draft.state(), draft.postalCode(), draft.reference());
    }

    private Order find(UUID storeId, UUID id) {
        return orderRepository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
    }
}
