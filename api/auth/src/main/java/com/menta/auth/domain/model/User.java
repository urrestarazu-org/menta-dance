package com.menta.auth.domain.model;

import com.menta.shared.domain.vo.Email;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * User domain entity.
 * Pure POJO without framework annotations (no JPA, no Spring).
 */
public class User {

    private UserId id;
    private Email email;
    private String passwordHash;
    private Role role;
    private UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private long tokenVersion;

    public User(
        UserId id,
        Email email,
        String passwordHash,
        Role role,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        this(id, email, passwordHash, role, status, createdAt, updatedAt, DEFAULT_TOKEN_VERSION);
    }

    private User(
        UserId id,
        Email email,
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

    /** Default token_version for newly-minted auth_users rows (ADR-0025 V2 DDL). */
    private static final long DEFAULT_TOKEN_VERSION = 1L;

    public static User create(Email email, String passwordHash, Role role) {
        LocalDateTime now = LocalDateTime.now();
        return new User(
            UserId.generate(),
            email,
            passwordHash,
            role,
            UserStatus.ACTIVE,
            now,
            now,
            DEFAULT_TOKEN_VERSION
        );
    }

    public void activate() {
        this.status = UserStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.status = UserStatus.INACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Compromise path: increment tokenVersion by 1. All previously-issued
     * refresh tokens carrying the OLD token_version will fail their family
     * check on next presentation (auth-refresh spec: tokenVersion viejo).
     * Persisted by the caller via UserRepository#save.
     */
    public void bumpTokenVersion() {
        this.tokenVersion += 1;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

    // Getters
    public UserId getId() {
        return id;
    }

    public Email getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        User user = (User) o;
        return Objects.equals(id, user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "User{id=" + id + ", email=" + email + ", role=" + role
            + ", status=" + status + ", tokenVersion=" + tokenVersion + "}";
    }
}
