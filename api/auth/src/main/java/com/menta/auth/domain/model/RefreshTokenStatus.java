package com.menta.auth.domain.model;

/**
 * Lifecycle status of an auth refresh token (ADR-0025).
 *
 * - ACTIVE  : freshly minted, can be rotated or revoked.
 * - USED    : was rotated out; presenting again triggers family revocation.
 *             (Spec uses "ROTATED" interchangeably for the same condition; we
 *             consolidate into USED at detection time.)
 * - REVOKED : terminal — logout, family revocation, or token_version bump.
 *             Immutable: no endpoint can reactive a REVOKED refresh.
 */
public enum RefreshTokenStatus {
    ACTIVE,
    USED,
    REVOKED
}
