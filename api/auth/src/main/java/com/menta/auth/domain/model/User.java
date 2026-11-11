package com.menta.auth.domain.model;

import com.menta.shared.domain.vo.Email;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

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
    /** Personal data; absent for accounts that predate the profile (demo, admin, E2E and legacy rows). */
    private UserProfile profile;
    /** Marks demo/test accounts so no outgoing email is addressed to them. Never set by registration. */
    private boolean demo;
    /**
     * True while an account created by an administrator has not yet had its holder choose a password: its stored
     * hash is a random, unusable placeholder and activation must collect the real one.
     */
    private boolean mustSetPassword;

    public User(
        UserId id,
        Email email,
        String passwordHash,
        Role role,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        this(id, email, passwordHash, role, status, createdAt, updatedAt, DEFAULT_TOKEN_VERSION, null, false, false);
    }

    private User(
        UserId id,
        Email email,
        String passwordHash,
        Role role,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long tokenVersion,
        UserProfile profile,
        boolean demo,
        boolean mustSetPassword
    ) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.tokenVersion = tokenVersion;
        this.profile = profile;
        this.demo = demo;
        this.mustSetPassword = mustSetPassword;
    }

    /** Default token_version for newly-minted auth_users rows (ADR-0025 V2 DDL). */
    private static final long DEFAULT_TOKEN_VERSION = 1L;

    /**
     * Registers a public user that must prove email ownership before login.
     *
     * @param profile the personal data given at registration, never null
     */
    public static User register(Email email, String passwordHash, Role role, UserProfile profile) {
        Objects.requireNonNull(profile, "profile cannot be null");
        LocalDateTime now = LocalDateTime.now();
        return new User(
            UserId.generate(),
            email,
            passwordHash,
            role,
            UserStatus.PENDING_ACTIVATION,
            now,
            now,
            DEFAULT_TOKEN_VERSION,
            profile,
            false,
            false
        );
    }

    /**
     * Creates an already-active user for trusted provisioning flows.
     */
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
            DEFAULT_TOKEN_VERSION,
            null,
            false,
            false
        );
    }

    /**
     * Rebuilds a user from persisted state, trusting the stored status and timestamps.
     *
     * <p>Unlike {@link #register} and {@link #create}, nothing is generated here: the id, the status, the
     * timestamps and the token version come from storage as they are.
     *
     * @return the user as it was persisted
     */
    public static User rehydrate(
        UserId id,
        Email email,
        String passwordHash,
        Role role,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long tokenVersion
    ) {
        return new User(id, email, passwordHash, role, status, createdAt, updatedAt, tokenVersion, null, false, false);
    }

    /**
     * Rebuilds a user from persisted state, including the personal data given at registration.
     *
     * @param profile the stored profile, or {@code null} for accounts that never had one
     * @return the user as it was persisted
     */
    public static User rehydrate(
        UserId id,
        Email email,
        String passwordHash,
        Role role,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long tokenVersion,
        UserProfile profile
    ) {
        return new User(
            id, email, passwordHash, role, status, createdAt, updatedAt, tokenVersion, profile, false, false
        );
    }

    /**
     * Rebuilds a user from persisted state, including the demo/test marker.
     *
     * @param profile the stored profile, or {@code null} for accounts that never had one
     * @param demo whether the account is a demo/test account that must receive no outgoing email
     * @return the user as it was persisted
     */
    public static User rehydrate(
        UserId id,
        Email email,
        String passwordHash,
        Role role,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long tokenVersion,
        UserProfile profile,
        boolean demo
    ) {
        return new User(
            id, email, passwordHash, role, status, createdAt, updatedAt, tokenVersion, profile, demo, false
        );
    }

    /**
     * Rebuilds a user from persisted state, including whether its holder still has to choose a password.
     *
     * @param profile the stored profile, or {@code null} for accounts that never had one
     * @param demo whether the account is a demo/test account that must receive no outgoing email
     * @param mustSetPassword whether the stored hash is an unusable placeholder awaiting the holder's choice
     * @return the user as it was persisted
     */
    public static User rehydrate(
        UserId id,
        Email email,
        String passwordHash,
        Role role,
        UserStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long tokenVersion,
        UserProfile profile,
        boolean demo,
        boolean mustSetPassword
    ) {
        return new User(
            id, email, passwordHash, role, status, createdAt, updatedAt, tokenVersion, profile, demo,
            mustSetPassword
        );
    }

    /**
     * Moves a pending account to {@code ACTIVE}.
     *
     * @throws IllegalStateException when the account is not pending activation, or when its holder still has to
     *     choose a password (use {@link #activateWithPassword})
     */
    public void activate() {
        if (this.status != UserStatus.PENDING_ACTIVATION) {
            throw new IllegalStateException("Only a user pending activation can be activated");
        }
        if (this.mustSetPassword) {
            throw new IllegalStateException("A user that must choose a password cannot be activated without one");
        }
        this.status = UserStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Activates an account created by an administrator, replacing the unusable placeholder with the password its
     * holder chose. Storing the password, clearing the marker and activating are one business event.
     *
     * @param chosenPasswordHash the hash of the password the holder chose, never null
     * @throws IllegalStateException when the account is not pending activation or did not need a password
     */
    public void activateWithPassword(String chosenPasswordHash) {
        Objects.requireNonNull(chosenPasswordHash, "chosenPasswordHash cannot be null");
        if (this.status != UserStatus.PENDING_ACTIVATION || !this.mustSetPassword) {
            throw new IllegalStateException("Only a pending user that must choose a password takes one on activation");
        }
        this.passwordHash = chosenPasswordHash;
        this.mustSetPassword = false;
        this.status = UserStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Marks the account as {@code INACTIVE}.
     *
     * <p>Unlike {@link #activate()}, this transition has no precondition on the current status. The change is
     * persisted by the caller through the repository.
     */
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

    /**
     * Replaces the password hash after a successful reset (US-AUTH-006) and
     * bumps tokenVersion in the same call, so a caller cannot persist a
     * changed password without also invalidating existing refresh tokens —
     * changing the credential and closing prior sessions are one business
     * event, not two independent calls a caller might forget to pair.
     */
    public void resetPassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash cannot be null");
        this.tokenVersion += 1;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Replaces the role and bumps tokenVersion in the same call: the role travels in the access token, so a
     * caller cannot persist a role change without also invalidating the tokens that carry the old one.
     *
     * <p>Assigning the role the user already has changes nothing (no bump, no timestamp update).
     * Persisted by the caller via UserRepository#save.
     *
     * @param newRole the role to assign, never null
     */
    public void changeRole(Role newRole) {
        Objects.requireNonNull(newRole, "newRole cannot be null");
        if (this.role == newRole) {
            return;
        }
        this.role = newRole;
        this.tokenVersion += 1;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Whether the holder still has to choose a password, because an administrator created the account and its
     * stored hash is an unusable placeholder.
     */
    public boolean isMustSetPassword() {
        return mustSetPassword;
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

    /**
     * Whether this is a demo/test account. Demo accounts are never addressed by outgoing emails.
     */
    public boolean isDemo() {
        return demo;
    }

    /**
     * Personal data given at registration.
     *
     * @return the profile, or empty for accounts that never had one
     */
    public Optional<UserProfile> getProfile() {
        return Optional.ofNullable(profile);
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
