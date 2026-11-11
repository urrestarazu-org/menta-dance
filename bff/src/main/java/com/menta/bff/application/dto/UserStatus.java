package com.menta.bff.application.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Lifecycle status of an account, as the Auth API names it ({@code UserStatus} in {@code auth-v1.yaml}),
 * with the Spanish label the management screens show.
 */
@Getter
@RequiredArgsConstructor
public enum UserStatus {
    PENDING_ACTIVATION("Pendiente de activación"),
    ACTIVE("Activo"),
    INACTIVE("Inactivo"),
    SUSPENDED("Suspendido"),
    LOCKED("Bloqueado");

    private final String label;
}
