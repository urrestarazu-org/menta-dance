package com.menta.physical.application.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Why a QR check-in device authentication was rejected (#266). The {@link #code()} value is part of
 * the locked rejection log and metric contract, so it must stay stable.
 */
@RequiredArgsConstructor
@Getter
@Accessors(fluent = true)
public enum DeviceAuthenticationRejectionReason {

    /** Unknown id, non-UUID id, absent secret, or wrong secret: indistinguishable to the caller. */
    UNKNOWN_OR_INVALID("unknown_or_invalid"),

    /** The device proved its secret but is {@code REVOKED}. */
    REVOKED("revoked"),

    /** The device proved its secret but its {@code expiresAt} has been reached. */
    EXPIRED("expired");

    private final String code;
}
