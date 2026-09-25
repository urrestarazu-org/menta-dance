package com.menta.physical.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** JPA persistence model for the append-only physical_device_audit table (#44, US-PHYSICAL-007). */
@Entity
@Table(name = "physical_device_audit")
public class PhysicalDeviceAuditJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "device_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID deviceId;

    @Column(name = "actor_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    @Column(name = "previous_value", columnDefinition = "TEXT", updatable = false)
    private String previousValue;

    @Column(name = "new_value", columnDefinition = "TEXT", updatable = false)
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PhysicalDeviceAuditJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public PhysicalDeviceAuditJpaEntity(
        UUID id, UUID deviceId, UUID actorId, String action, String previousValue, String newValue,
        Instant createdAt
    ) {
        this.id = id;
        this.deviceId = deviceId;
        this.actorId = actorId;
        this.action = action;
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
