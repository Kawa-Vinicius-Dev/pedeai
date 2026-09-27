package com.pedeai.printing.repository;

import com.pedeai.printing.domain.PrintJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrintJobRepository extends JpaRepository<PrintJob, UUID> {

    boolean existsByStoreIdAndIdempotencyKey(UUID storeId, String idempotencyKey);

    Optional<PrintJob> findByStoreIdAndIdempotencyKey(UUID storeId, String idempotencyKey);

    Optional<PrintJob> findByIdAndStoreId(UUID id, UUID storeId);

    Page<PrintJob> findAllByStoreIdAndCreatedAtAfter(UUID storeId, Instant since, Pageable pageable);

    long countByPrinterIdAndStatus(UUID printerId, PrintJob.Status status);

    long countByStoreIdAndStatusInAndCreatedAtAfter(UUID storeId, Collection<PrintJob.Status> statuses, Instant since);

    List<PrintJob> findAllByAgentIdAndStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            UUID agentId, PrintJob.Status status, Instant now);

    Optional<PrintJob> findByIdAndAgentId(UUID id, UUID agentId);

    List<PrintJob> findAllByOrderIdAndStatus(UUID orderId, PrintJob.Status status);

    List<PrintJob> findAllByOrderIdOrderByCreatedAtAsc(UUID orderId);

    List<PrintJob> findAllByStatusAndLeaseUntilBefore(PrintJob.Status status, Instant now);

    List<PrintJob> findAllByStatusAndExpiresAtBefore(PrintJob.Status status, Instant now);
}
