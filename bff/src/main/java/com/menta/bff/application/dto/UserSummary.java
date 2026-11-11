package com.menta.bff.application.dto;

import java.time.LocalDateTime;

/**
 * What an administrator sees of an account, trimmed from the upstream {@code UserSummary} to what the
 * users list shows.
 *
 * @param id        account identifier
 * @param email     account email
 * @param firstName first name, {@code null} for accounts without a profile
 * @param lastName  last name, {@code null} for accounts without a profile
 * @param role      account role
 * @param status    account status
 * @param createdAt sign-up moment as the upstream reports it, without a zone
 */
public record UserSummary(
        String id,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        UserStatus status,
        LocalDateTime createdAt) {
}
