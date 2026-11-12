package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.UserRole;
import com.menta.bff.application.dto.UserStatus;
import java.time.LocalDate;

/**
 * One account of the users list, worded for the screen.
 *
 * @param id          account identifier, for the form that changes its role
 * @param email       account email
 * @param displayName first and last name, or a neutral label for accounts without a profile
 * @param role        account role
 * @param status      account status
 * @param createdDate sign-up day, for the machine-readable {@code <time>} attribute
 * @param createdLabel sign-up day in Spanish, e.g. {@code "1 de octubre de 2026"}
 */
public record UserRow(
        String id,
        String email,
        String displayName,
        UserRole role,
        UserStatus status,
        LocalDate createdDate,
        String createdLabel) {
}
