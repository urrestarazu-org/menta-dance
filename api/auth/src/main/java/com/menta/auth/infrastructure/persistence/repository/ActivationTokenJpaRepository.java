package com.menta.auth.infrastructure.persistence.repository;

import com.menta.auth.infrastructure.persistence.entity.ActivationTokenJpaEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

/** Queries that preserve activation-token single-use semantics. */
public interface ActivationTokenJpaRepository extends JpaRepository<ActivationTokenJpaEntity, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ActivationTokenJpaEntity> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE ActivationTokenJpaEntity token
           SET token.invalidatedAt = :now
         WHERE token.userId = :userId
           AND token.usedAt IS NULL
           AND token.invalidatedAt IS NULL
           AND token.expiresAt > :now
        """)
    int invalidateActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE ActivationTokenJpaEntity token
           SET token.usedAt = :usedAt
         WHERE token.id = :id
           AND token.usedAt IS NULL
           AND token.invalidatedAt IS NULL
           AND token.expiresAt > :now
        """)
    int consumeIfActive(
        @Param("id") UUID id, @Param("usedAt") Instant usedAt, @Param("now") Instant now
    );

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE ActivationTokenJpaEntity token
           SET token.deliveryCiphertext = NULL,
               token.deliveryNonce = NULL,
               token.deliveryKeyVersion = NULL
         WHERE token.id = :id
        """)
    int clearDeliveryEnvelope(@Param("id") UUID id);
}
