package com.menta.auth.infrastructure.persistence.repository;

import com.menta.auth.infrastructure.persistence.entity.PasswordResetTokenJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Queries that preserve password-reset-token single-use semantics. */
public interface PasswordResetTokenJpaRepository
    extends JpaRepository<PasswordResetTokenJpaEntity, UUID> {

    /**
     * Pessimistic lock, mirroring the activation lookup: two concurrent
     * resets presenting the same token must serialise here, so exactly one
     * wins the {@code consumeIfActive} race below.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetTokenJpaEntity> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE PasswordResetTokenJpaEntity token
           SET token.invalidatedAt = :now
         WHERE token.userId = :userId
           AND token.usedAt IS NULL
           AND token.invalidatedAt IS NULL
           AND token.expiresAt > :now
        """)
    int invalidateActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Consumes the token only while every active-state predicate still holds,
     * so the check and the write cannot be split by a concurrent request.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE PasswordResetTokenJpaEntity token
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
        UPDATE PasswordResetTokenJpaEntity token
           SET token.deliveryCiphertext = NULL,
               token.deliveryNonce = NULL,
               token.deliveryKeyVersion = NULL
         WHERE token.id = :id
        """)
    int clearDeliveryEnvelope(@Param("id") UUID id);
}
