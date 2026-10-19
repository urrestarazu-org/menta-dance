package com.menta.auth.domain.model;

/** Lifecycle state of an account activation token. */
public enum ActivationTokenStatus {
    ACTIVE,
    USED,
    INVALIDATED,
    EXPIRED
}
