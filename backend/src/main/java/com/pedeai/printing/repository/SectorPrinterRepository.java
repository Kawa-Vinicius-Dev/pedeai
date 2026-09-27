package com.pedeai.printing.repository;

import com.pedeai.printing.domain.SectorPrinter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SectorPrinterRepository extends JpaRepository<SectorPrinter, UUID> {

    List<SectorPrinter> findAllByStoreId(UUID storeId);

    Optional<SectorPrinter> findBySectorIdAndStoreId(UUID sectorId, UUID storeId);
}
