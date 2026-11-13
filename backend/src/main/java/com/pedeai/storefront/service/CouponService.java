package com.pedeai.storefront.service;

import com.pedeai.shared.exception.BusinessRuleException;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.money.Money;
import com.pedeai.storefront.domain.Coupon;
import com.pedeai.storefront.dto.CouponRequest;
import com.pedeai.storefront.dto.CouponResponse;
import com.pedeai.storefront.repository.CouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/** Cupons de desconto do cardápio digital. */
@Service
public class CouponService {
    static final String NOT_FOUND = "Cupom não encontrado.";
    static final String DUPLICATE = "Já existe um cupom com este código.";

    private final CouponRepository repository;
    private final Clock clock;

    public CouponService(CouponRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> list(UUID storeId) {
        return repository.findAllByStoreIdOrderByCreatedAtDesc(storeId).stream().map(CouponResponse::from).toList();
    }

    @Transactional
    public CouponResponse create(UUID storeId, CouponRequest request) {
        rejectDuplicate(storeId, request.code(), null);
        Coupon coupon = new Coupon(storeId, request.code(), request.type(), request.amount(),
                request.minOrderCents(), request.maxUses(), request.validUntil(), request.active(),
                Instant.now(clock));
        return CouponResponse.from(repository.save(coupon));
    }

    @Transactional
    public CouponResponse update(UUID storeId, UUID id, CouponRequest request) {
        Coupon coupon = repository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        rejectDuplicate(storeId, request.code(), id);
        coupon.update(request.code(), request.type(), request.amount(), request.minOrderCents(), request.maxUses(),
                request.validUntil(), request.active(), Instant.now(clock));
        return CouponResponse.from(coupon);
    }

    /** Quanto o cupom tiraria deste pedido, sem gastar um uso. Cupom que não vale: 422 com o motivo. */
    @Transactional(readOnly = true)
    public long quote(UUID storeId, ZoneId storeZone, String code, long subtotalCents) {
        return discount(find(storeId, code), storeZone, subtotalCents);
    }

    /** Aplica o cupom num pedido que está sendo criado: confere de novo e conta o uso, na mesma transação. */
    @Transactional
    public long redeem(UUID storeId, ZoneId storeZone, String code, long subtotalCents) {
        Coupon coupon = find(storeId, code);
        long discount = discount(coupon, storeZone, subtotalCents);
        coupon.used();
        return discount;
    }

    private long discount(Coupon coupon, ZoneId storeZone, long subtotalCents) {
        return coupon.discountFor(subtotalCents, LocalDate.now(clock.withZone(storeZone)),
                Money.format(coupon.getMinOrderCents()));
    }

    private Coupon find(UUID storeId, String code) {
        // Código que não existe responde igual a cupom vencido: não dá para descobrir códigos por tentativa.
        return repository.findByStoreIdAndCode(storeId, Coupon.normalize(code))
                .orElseThrow(() -> new BusinessRuleException(Coupon.NOT_AVAILABLE));
    }

    private void rejectDuplicate(UUID storeId, String code, UUID currentId) {
        repository.findByStoreIdAndCode(storeId, Coupon.normalize(code))
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new ConflictException(DUPLICATE);
                });
    }
}
