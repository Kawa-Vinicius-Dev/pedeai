package com.pedeai.printing.repository;

import com.pedeai.printing.domain.PrintAgent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrintAgentRepository extends JpaRepository<PrintAgent, UUID> {

    Optional<PrintAgent> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    Optional<PrintAgent> findByIdAndStoreIdAndRevokedAtIsNull(UUID id, UUID storeId);

    List<PrintAgent> findAllByStoreIdAndRevokedAtIsNullOrderByCreatedAtAsc(UUID storeId);
}
