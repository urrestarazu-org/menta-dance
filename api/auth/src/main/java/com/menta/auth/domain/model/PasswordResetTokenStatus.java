package com.menta.auth.domain.model;

/**
 * Lifecycle of a password reset credential.
 *
 * <p>Unlike activation — where every terminal state collapses into one generic
 * response — these are kept distinct on purpose: a reset token is a
 * high-entropy secret that cannot be enumerated, so telling the user "the link
 * expired, request a new one" is useful guidance rather than an information
 * leak (US-AUTH-006).</p>
 */
public enum PasswordResetTokenStatus {

    /** Usable: not consumed, not superseded, not past its expiry. */
    ACTIVE,

    /** Already consumed by a successful reset. */
    USED,

    /** Superseded by a newer reset request for the same user. */
    INVALIDATED,

    /** Past its expiry instant. */
    EXPIRED
}
