package com.menta.physical.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA persistence model for the physical_attendances table.
 *
 * <p>{@code uniqueConstraints} mirrors {@code V15__physical_attendances.sql}'s
 * {@code uq_physical_attendances_session_user} — same pattern as {@code
 * SubscriptionCourseJpaEntity}/{@code PlanPaymentMethodJpaEntity}: without it,
 * a {@code ddl-auto=create-drop} test schema (Hibernate generating from these
 * annotations, not from Flyway) would never see the real constraint.</p>
 */
@Entity
@Table(
    name = "physical_attendances",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_physical_attendances_session_user", columnNames = {"session_id", "user_id"}
    )
)
public class AttendanceJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "device_id", nullable = false, updatable = false)
    private String deviceId;

    @Column(name = "kind", nullable = false, updatable = false, columnDefinition = "VARCHAR(20)")
    private String kind;

    protected AttendanceJpaEntity() {
        // JPA requires a no-arg constructor.
    }

    public AttendanceJpaEntity(
        UUID id, UUID sessionId, UUID userId, Instant recordedAt, String deviceId, String kind
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.userId = userId;
        this.recordedAt = recordedAt;
        this.deviceId = deviceId;
        this.kind = kind;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getKind() {
        return kind;
    }
}
