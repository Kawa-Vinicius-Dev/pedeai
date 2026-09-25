package com.pedeai.store.repository;

import com.pedeai.store.domain.RefreshToken;
import com.pedeai.store.domain.RevokeReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :now, t.revokeReason = :reason
            where t.userId = :userId and t.revokedAt is null""")
    int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now,
                                @Param("reason") RevokeReason reason);
}
