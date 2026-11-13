package com.pedeai.storefront.service;

import com.pedeai.catalog.service.CategoryService;
import com.pedeai.catalog.service.OptionGroupService;
import com.pedeai.catalog.service.ProductService;
import com.pedeai.customer.service.DeliveryZoneService;
import com.pedeai.order.service.OrderService;
import com.pedeai.payment.service.PaymentMethodService;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.TooManyRequestsException;
import com.pedeai.store.dto.StoreResponse;
import com.pedeai.store.service.StoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorefrontServiceTest {
    @Mock
    private StoreService storeService;
    @Mock
    private CategoryService categoryService;
    @Mock
    private ProductService productService;
    @Mock
    private OptionGroupService optionGroupService;
    @Mock
    private DeliveryZoneService deliveryZoneService;
    @Mock
    private PaymentMethodService paymentMethodService;
    @Mock
    private OrderService orderService;
    @Mock
    private CouponService couponService;

    @Test
    void theStoreCapHoldsEvenWhenEveryOrderClaimsADifferentAddress() {
        StoreResponse store = mock(StoreResponse.class);
        when(store.id()).thenReturn(STORE_ID);
        when(storeService.getBySlug("bella")).thenReturn(store);
        StorefrontService service = new StorefrontService(storeService, categoryService, productService,
                optionGroupService, deliveryZoneService, paymentMethodService, orderService, couponService, CLOCK);
        // Cada tentativa diz vir de um IP novo (o cabeçalho dá para forjar) e para na loja fechada.
        for (int attempt = 0; attempt < StorefrontService.MAX_MENU_ORDERS_PER_STORE; attempt++) {
            String address = "10.0.0." + attempt;
            assertThatThrownBy(() -> service.placeOrder("bella", null, address))
                    .isInstanceOf(BusinessRuleException.class);
        }

        assertThatThrownBy(() -> service.placeOrder("bella", null, "10.9.9.9"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessage(StorefrontService.TOO_MANY_ORDERS);
    }
}
