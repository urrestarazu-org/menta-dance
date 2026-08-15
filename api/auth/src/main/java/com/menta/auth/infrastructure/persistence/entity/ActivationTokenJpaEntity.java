package com.menta.auth.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for the auth_activation_tokens table. */
@Entity
@Table(name = "auth_activation_tokens")
public class ActivationTokenJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", columnDefinition = "CHAR(64)", length = 64, nullable = false, unique = true, updatable = false)
    private String tokenHash;

    @Column(name = "delivery_ciphertext", columnDefinition = "VARBINARY(1024)")
    private byte[] deliveryCiphertext;

    @Column(name = "delivery_nonce", columnDefinition = "BINARY(12)")
    private byte[] deliveryNonce;

    @Column(name = "delivery_key_version")
    private Short deliveryKeyVersion;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    protected ActivationTokenJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public ActivationTokenJpaEntity(
        UUID id, UUID userId, String tokenHash, byte[] deliveryCiphertext,
        byte[] deliveryNonce, Short deliveryKeyVersion, Instant expiresAt,
        Instant createdAt, Instant usedAt, Instant invalidatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.deliveryCiphertext = deliveryCiphertext;
        this.deliveryNonce = deliveryNonce;
        this.deliveryKeyVersion = deliveryKeyVersion;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.usedAt = usedAt;
        this.invalidatedAt = invalidatedAt;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public byte[] getDeliveryCiphertext() { return deliveryCiphertext; }
    public byte[] getDeliveryNonce() { return deliveryNonce; }
    public Short getDeliveryKeyVersion() { return deliveryKeyVersion; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getInvalidatedAt() { return invalidatedAt; }

    public void setUserId(UUID userId) { this.userId = userId; }
}
