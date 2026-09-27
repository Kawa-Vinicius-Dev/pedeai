package com.pedeai.printing.repository;

import com.pedeai.printing.domain.Printer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrinterRepository extends JpaRepository<Printer, UUID> {

    List<Printer> findAllByStoreIdOrderByNameAsc(UUID storeId);

    Optional<Printer> findByIdAndStoreId(UUID id, UUID storeId);

    List<Printer> findAllByAgentIdAndActiveTrueOrderByNameAsc(UUID agentId);

    boolean existsByStoreIdAndNameIgnoreCaseAndIdNot(UUID storeId, String name, UUID id);
}
