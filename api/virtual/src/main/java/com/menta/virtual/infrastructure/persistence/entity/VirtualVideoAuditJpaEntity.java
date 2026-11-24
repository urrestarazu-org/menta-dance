package com.menta.virtual.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** JPA persistence model for the append-only virtual_video_audit table (#841). Never updated after insert. */
@Entity
@Table(name = "virtual_video_audit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VirtualVideoAuditJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "video_id", length = 128, nullable = false, updatable = false)
    private String videoId;

    @Column(name = "actor_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "action", length = 50, nullable = false, updatable = false)
    private String action;

    @Column(name = "previous_value", columnDefinition = "TEXT", updatable = false)
    private String previousValue;

    @Column(name = "new_value", columnDefinition = "TEXT", updatable = false)
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public VirtualVideoAuditJpaEntity(
        UUID id, String videoId, UUID actorId, String action, String previousValue, String newValue,
        Instant createdAt
    ) {
        this.id = id;
        this.videoId = videoId;
        this.actorId = actorId;
        this.action = action;
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.createdAt = createdAt;
    }
}
