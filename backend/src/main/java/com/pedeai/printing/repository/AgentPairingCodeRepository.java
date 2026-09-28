package com.pedeai.printing.repository;

import com.pedeai.printing.domain.AgentPairingCode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.UUID;

public interface AgentPairingCodeRepository extends JpaRepository<AgentPairingCode, UUID> {

    List<AgentPairingCode> findAllByCodeHash(String codeHash);

    @Modifying
    @Query("delete from AgentPairingCode c where c.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
