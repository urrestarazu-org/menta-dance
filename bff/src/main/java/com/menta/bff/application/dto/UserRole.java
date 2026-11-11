package com.menta.bff.application.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Role of an account, as the Auth API names it ({@code UserRole} in {@code auth-v1.yaml}), with the
 * Spanish label the management screens show.
 */
@Getter
@RequiredArgsConstructor
public enum UserRole {
    ADMIN("Administrador"),
    INSTRUCTOR("Profesor/a"),
    RECEPTIONIST("Recepcionista"),
    STUDENT("Alumno/a");

    private final String label;
}
