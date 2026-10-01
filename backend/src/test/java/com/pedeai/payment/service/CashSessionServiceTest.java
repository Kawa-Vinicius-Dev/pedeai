package com.pedeai.payment.service;

import com.pedeai.payment.domain.CashMovement;
import com.pedeai.payment.domain.CashSession;
import com.pedeai.payment.domain.PaymentMethod;
import com.pedeai.payment.domain.PaymentMethodType;
import com.pedeai.payment.dto.CashMovementRequest;
import com.pedeai.payment.dto.CashSessionResponse;
import com.pedeai.payment.dto.CloseCashSessionRequest;
import com.pedeai.payment.dto.OpenCashSessionRequest;
import com.pedeai.payment.repository.CashMovementRepository;
import com.pedeai.payment.repository.CashSessionRepository;
import com.pedeai.payment.repository.MethodTotal;
import com.pedeai.payment.repository.PaymentMethodRepository;
import com.pedeai.payment.repository.PaymentRepository;
import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.security.CurrentUser;
import com.pedeai.shared.security.Role;
import com.pedeai.store.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.pedeai.support.TestSecurity.CLOCK;
import static com.pedeai.support.TestSecurity.NOW;
import static com.pedeai.support.TestSecurity.STORE_ID;
import static com.pedeai.support.TestSecurity.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CashSessionServiceTest {
    private static final CurrentUser CASHIER = new CurrentUser(USER_ID, STORE_ID, Role.CASHIER, "Ana");

    @Mock
    private CashSessionRepository sessions;
    @Mock
    private CashMovementRepository movements;
    @Mock
    private PaymentRepository payments;
    @Mock
    private PaymentMethodRepository methods;
    @Mock
    private UserService users;

    private CashSessionService service;
    private CashSession session;
    private PaymentMethod cash;
    private PaymentMethod pix;
    private PaymentMethod online;
    private final List<CashMovement> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new CashSessionService(sessions, movements, payments, methods, users, CLOCK);
        cash = new PaymentMethod(STORE_ID, "Dinheiro", PaymentMethodType.CASH, 0, NOW);
        pix = new PaymentMethod(STORE_ID, "Pix", PaymentMethodType.PIX, 1, NOW);
        online = new PaymentMethod(STORE_ID, "Online iFood", PaymentMethodType.ONLINE, 2, NOW);
        session = new CashSession(STORE_ID, 10_000, USER_ID, NOW.minusSeconds(3_600));
        when(methods.findAllByStoreIdOrderBySortOrderAscNameAsc(STORE_ID)).thenReturn(List.of(cash, pix, online));
        when(sessions.findByIdAndStoreId(session.getId(), STORE_ID)).thenReturn(Optional.of(session));
        when(payments.sumReceivedAtStore(eq(STORE_ID), any(), any())).thenReturn(List.of(
                new MethodTotal(cash.getId(), 5_000, 2), new MethodTotal(pix.getId(), 3_000, 1)));
        when(movements.findAllByCashSessionIdOrderByCreatedAtAscIdAsc(session.getId())).thenReturn(saved);
        when(movements.save(any())).thenAnswer(invocation -> {
            saved.add(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
        when(users.list(STORE_ID)).thenReturn(List.of());
    }

    @Test
    void secondOpenCashIsAConflict() {
        when(sessions.findByStoreIdAndStatus(STORE_ID, CashSession.Status.OPEN)).thenReturn(Optional.of(session));

        assertThatThrownBy(() -> service.open(CASHIER, new OpenCashSessionRequest(0L)))
                .isInstanceOf(ConflictException.class);
        verify(sessions, never()).saveAndFlush(any());
    }

    @Test
    void drawerExpectsOpeningPlusCashMinusWithdrawalsAndOnlineStaysOut() {
        service.addMovement(CASHIER, session.getId(), new CashMovementRequest(CashMovement.Type.WITHDRAWAL, 2_000L,
                "Banco"));
        CashSessionResponse response = service.addMovement(CASHIER, session.getId(),
                new CashMovementRequest(CashMovement.Type.DEPOSIT, 500L, " Troco "));

        assertThat(response.lines()).extracting(line -> line.name() + "=" + line.expectedCents())
                .containsExactly("Dinheiro=13500", "Pix=3000");
        assertThat(response.lines().getFirst().paymentsCents()).isEqualTo(5_000);
        assertThat(response.movements()).extracting(movement -> movement.reason()).containsExactly("Banco", "Troco");
    }

    @Test
    void withdrawalCannotTakeMoreThanTheDrawerHas() {
        assertThatThrownBy(() -> service.addMovement(CASHIER, session.getId(),
                new CashMovementRequest(CashMovement.Type.WITHDRAWAL, 15_001L, "Tudo")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("150,00");
    }

    @Test
    void closeRecordsTheCountAndRefusesAnotherStoresMethod() {
        assertThatThrownBy(() -> service.close(CASHIER, session.getId(), new CloseCashSessionRequest(
                List.of(new CloseCashSessionRequest.Count(UUID.randomUUID(), 1L)), null)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(CashSessionService.UNKNOWN_METHOD);

        CashSessionResponse closed = service.close(CASHIER, session.getId(), new CloseCashSessionRequest(
                List.of(new CloseCashSessionRequest.Count(cash.getId(), 14_800L)), " "));

        assertThat(closed.status()).isEqualTo(CashSession.Status.CLOSED);
        assertThat(closed.expectedCents()).isEqualTo(15_000 + 3_000);
        assertThat(closed.countedCents()).isEqualTo(14_800);
        assertThat(closed.differenceCents()).isEqualTo(14_800 - 18_000);
        assertThat(closed.notes()).isNull();
        assertThatThrownBy(() -> service.addMovement(CASHIER, session.getId(),
                new CashMovementRequest(CashMovement.Type.DEPOSIT, 1L, "Depois")))
                .isInstanceOf(BusinessRuleException.class);
    }
}
