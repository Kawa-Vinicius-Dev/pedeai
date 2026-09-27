package com.pedeai.integration.service;

import com.pedeai.integration.config.IfoodProperties;
import com.pedeai.integration.domain.MarketplaceConnection;
import com.pedeai.integration.dto.ConnectionRequest;
import com.pedeai.integration.ifood.IfoodClient;
import com.pedeai.integration.repository.MarketplaceConnectionRepository;
import com.pedeai.integration.repository.OutboundActionRepository;
import com.pedeai.order.domain.OrderSource;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConnectionServiceTest {
    private static final IfoodProperties CONFIGURED = new IfoodProperties(true, "http://ifood", "id", "segredo",
            "/p", "/a", false);

    private final MarketplaceConnectionRepository repository = mock(MarketplaceConnectionRepository.class);
    private final IfoodClient ifood = mock(IfoodClient.class);

    private ConnectionService service(IfoodProperties properties) {
        when(repository.save(any())).then(returnsFirstArg());
        return new ConnectionService(repository, mock(OutboundActionRepository.class), ifood, properties, CLOCK);
    }

    @Test
    void linksOnlyAMerchantThatGaveThePermission() {
        when(ifood.merchants()).thenReturn(List.of(new IfoodClient.Merchant("m-1", "Lanchonete da Ana")));

        assertThatThrownBy(() -> service(CONFIGURED).connect(STORE_ID, new ConnectionRequest("m-2", false)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(ConnectionService.MERCHANT_NOT_ALLOWED);
        assertThat(service(CONFIGURED).connect(STORE_ID, new ConnectionRequest(" m-1 ", true)).merchantName())
                .isEqualTo("Lanchonete da Ana");
    }

    @Test
    void aMerchantBelongsToOneStore() {
        when(ifood.merchants()).thenReturn(List.of(new IfoodClient.Merchant("m-1", "Lanchonete")));
        when(repository.findByProviderAndExternalMerchantId(OrderSource.IFOOD, "m-1")).thenReturn(Optional.of(
                new MarketplaceConnection(java.util.UUID.randomUUID(), OrderSource.IFOOD, "m-1", "x", false, NOW)));

        assertThatThrownBy(() -> service(CONFIGURED).connect(STORE_ID, new ConnectionRequest("m-1", false)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void withoutCredentialsOrSimulatorNothingIsLinked() {
        IfoodProperties off = new IfoodProperties(false, "http://ifood", null, null, "/p", "/a", false);

        assertThatThrownBy(() -> service(off).connect(STORE_ID, new ConnectionRequest("m-1", false)))
                .hasMessage(ConnectionService.NOT_CONFIGURED);
        assertThatThrownBy(() -> service(off).merchants()).hasMessage(ConnectionService.NOT_CONFIGURED);
        verify(ifood, never()).merchants();
    }
}
