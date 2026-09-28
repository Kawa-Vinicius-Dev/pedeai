package com.pedeai.printing.repository;

import com.pedeai.printing.domain.PrintJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

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

    /** Trabalho encerrado há tempo: os bytes do ticket não servem mais. O que ainda está na fila fica. */
    @Modifying
    @Query("delete from PrintJob j where j.createdAt < :cutoff and j.status not in :open")
    int deleteClosedBefore(@Param("cutoff") Instant cutoff, @Param("open") Collection<PrintJob.Status> open);

    List<PrintJob> findAllByStatusAndLeaseUntilBefore(PrintJob.Status status, Instant now);

    List<PrintJob> findAllByStatusAndExpiresAtBefore(PrintJob.Status status, Instant now);
}
