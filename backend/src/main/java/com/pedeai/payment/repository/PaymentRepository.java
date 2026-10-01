package com.pedeai.payment.repository;

import com.pedeai.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findAllByStoreIdAndOrderIdOrderByCreatedAtAscIdAsc(UUID storeId, UUID orderId);

    Optional<Payment> findByIdAndStoreIdAndOrderId(UUID id, UUID storeId, UUID orderId);

    /** Recebido na loja (não pelo marketplace) no período, por forma de pagamento: o que deve estar no caixa. */
    @Query("""
            select new com.pedeai.payment.repository.MethodTotal(p.paymentMethodId, sum(p.amountCents), count(p))
            from Payment p
            where p.storeId = :storeId and p.status = com.pedeai.payment.domain.PaymentStatus.PAID
              and p.origin = com.pedeai.payment.domain.PaymentOrigin.LOCAL
              and p.paidAt >= :from and p.paidAt <= :to
            group by p.paymentMethodId""")
    List<MethodTotal> sumReceivedAtStore(@Param("storeId") UUID storeId, @Param("from") Instant from,
                                         @Param("to") Instant to);
}
