package com.menta.bff.application.dto;

import java.util.List;
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

    /**
     * Roles an administrator can give to a new account, in the order the creation form lists them.
     * {@code STUDENT} is not one of them: students register themselves.
     *
     * @return the staff roles
     */
    public static List<UserRole> staffRoles() {
        return List.of(INSTRUCTOR, RECEPTIONIST, ADMIN);
    }
}
