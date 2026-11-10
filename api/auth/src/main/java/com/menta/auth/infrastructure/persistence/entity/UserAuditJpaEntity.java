package com.menta.auth.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** JPA persistence model for the append-only auth_user_audit table (V29, #378). Every column is insert-only. */
@Entity
@Table(name = "auth_user_audit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class UserAuditJpaEntity {

    @Id
    @Column(name = "id", columnDefinition = "BINARY(16)", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "actor_id", columnDefinition = "BINARY(16)", nullable = false, updatable = false)
    private UUID actorId;

    @Column(name = "action", nullable = false, updatable = false, length = 50)
    private String action;

    @Column(name = "previous_value", updatable = false)
    private String previousValue;

    @Column(name = "new_value", updatable = false)
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
