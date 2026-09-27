package com.pedeai.printing.repository;

import com.pedeai.printing.domain.AgentPairingCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentPairingCodeRepository extends JpaRepository<AgentPairingCode, UUID> {

    List<AgentPairingCode> findAllByCodeHash(String codeHash);
}
