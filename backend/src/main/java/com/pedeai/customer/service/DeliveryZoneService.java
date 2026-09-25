package com.pedeai.customer.service;

import com.pedeai.customer.domain.DeliveryZone;
import com.pedeai.customer.dto.DeliveryZoneRequest;
import com.pedeai.customer.dto.DeliveryZoneResponse;
import com.pedeai.customer.repository.DeliveryZoneRepository;
import com.pedeai.shared.exception.ConflictException;
import com.pedeai.shared.exception.ResourceNotFoundException;
import com.pedeai.shared.text.Texts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Taxa de entrega por bairro. O atendente ainda pode digitar outra taxa no pedido. */
@Service
public class DeliveryZoneService {
    static final String NOT_FOUND = "Taxa de entrega não encontrada.";

    private final DeliveryZoneRepository repository;
    private final Clock clock;

    public DeliveryZoneService(DeliveryZoneRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DeliveryZoneResponse> list(UUID storeId) {
        return repository.findAllByStoreIdOrderByNeighborhoodAsc(storeId).stream()
                .map(DeliveryZoneResponse::from)
                .toList();
    }

    @Transactional
    public DeliveryZoneResponse create(UUID storeId, DeliveryZoneRequest request) {
        String neighborhood = clean(request.neighborhood());
        rejectDuplicate(storeId, neighborhood, null);
        DeliveryZone zone = new DeliveryZone(storeId, neighborhood, request.feeCents(), request.active(),
                Instant.now(clock));
        return DeliveryZoneResponse.from(repository.save(zone));
    }

    @Transactional
    public DeliveryZoneResponse update(UUID storeId, UUID id, DeliveryZoneRequest request) {
        DeliveryZone zone = repository.findByIdAndStoreId(id, storeId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND));
        String neighborhood = clean(request.neighborhood());
        rejectDuplicate(storeId, neighborhood, id);
        zone.update(neighborhood, request.feeCents(), request.active(), Instant.now(clock));
        return DeliveryZoneResponse.from(zone);
    }

    /** "  São   José " vira "São José". */
    private static String clean(String neighborhood) {
        return neighborhood.trim().replaceAll("\\s+", " ");
    }

    /** O aviso usa o nome já cadastrado: quem digitou "centro" vê que é o "Centro" da lista. */
    private void rejectDuplicate(UUID storeId, String neighborhood, UUID currentId) {
        repository.findByStoreIdAndNeighborhoodKey(storeId, Texts.normalizeKey(neighborhood))
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new ConflictException(
                            "Já existe uma taxa para o bairro " + existing.getNeighborhood() + ".");
                });
    }
}
