package com.menta.auth.infrastructure.persistence.entity;

import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity for User.
 * This is infrastructure layer - contains framework annotations.
 */
@Entity
@Table(name = "auth_users")
public class UserJpaEntity {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    // JPA requires default constructor
    protected UserJpaEntity() {
    }

    public UserJpaEntity(
        UUID id,
        String email,
        String passwordHash,
        Role role,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long tokenVersion
    ) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.tokenVersion = tokenVersion;
    }

    // Getters and setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(long tokenVersion) {
        this.tokenVersion = tokenVersion;
    }
}
