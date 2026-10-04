package com.menta.auth.infrastructure.persistence.repository;

import com.menta.auth.infrastructure.persistence.entity.ActivationTokenJpaEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Queries that preserve activation-token single-use semantics. */
public interface ActivationTokenJpaRepository extends JpaRepository<ActivationTokenJpaEntity, UUID> {

    /**
     * Finds a token by hash under a pessimistic write lock.
     *
     * <p>The lock serializes concurrent requests presenting the same token until the surrounding transaction ends.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ActivationTokenJpaEntity> findByTokenHash(String tokenHash);

    /**
     * Invalidates every still-active token of the user.
     *
     * <p>A token is active while it is unused, not invalidated and not yet expired at {@code now}.
     *
     * @return the number of tokens invalidated
     */
    @Modifying(clearAutomatically = true)
    @Query(
        """
        UPDATE ActivationTokenJpaEntity token
           SET token.invalidatedAt = :now
         WHERE token.userId = :userId
           AND token.usedAt IS NULL
           AND token.invalidatedAt IS NULL
           AND token.expiresAt > :now
        """)
    int invalidateActiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Marks the token as used only if it is still active, in a single conditional update.
     *
     * @return 1 when the token was consumed, 0 when a concurrent request already consumed, invalidated or expired it
     */
    @Modifying(clearAutomatically = true)
    @Query(
        """
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

    /**
     * Erases the encrypted delivery material of the token once it has been delivered.
     *
     * @return the number of rows updated
     */
    @Modifying(clearAutomatically = true)
    @Query(
        """
        UPDATE ActivationTokenJpaEntity token
           SET token.deliveryCiphertext = NULL,
               token.deliveryNonce = NULL,
               token.deliveryKeyVersion = NULL
         WHERE token.id = :id
        """)
    int clearDeliveryEnvelope(@Param("id") UUID id);
}
