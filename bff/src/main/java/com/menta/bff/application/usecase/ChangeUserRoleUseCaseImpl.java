package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.UserRole;
import com.menta.bff.application.dto.UserSummary;
import com.menta.bff.application.port.out.AccessTokenClaimsReader;
import com.menta.bff.application.port.out.AdminUsersApiClient;
import com.menta.bff.application.usecase.ChangeUserRoleResult.Outcome;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link ChangeUserRoleUseCase}. Whether the account exists and whether the change would
 * leave the system without an administrator is decided by the Auth API; this class only refuses what can never
 * be sent and translates the port's failures into results. A failure is logged by its class only: neither the
 * token nor the account (id, email, names) may reach a log.
 *
 * <p>It also tells the caller when the account is their own: the API closes all the sessions of an account
 * whose role changes, the one the caller is using included, so the screen must not keep it. Asking for the role
 * one already has changes nothing in the API and closes nothing. The identity comes from the claims of the
 * caller's own token; when they cannot be read the change is reported as someone else's, and the broken session
 * degrades through the token refresh like any revoked one.
 * </p>
 */
@Slf4j
@RequiredArgsConstructor
public class ChangeUserRoleUseCaseImpl implements ChangeUserRoleUseCase {

    private static final Pattern UUID_FORMAT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final AdminUsersApiClient adminUsersApiClient;
    private final AccessTokenClaimsReader claimsReader;

    @Override
    public ChangeUserRoleResult execute(String accessToken, String userId, String role) {
        if (accessToken == null || accessToken.isBlank()) {
            return ChangeUserRoleResult.of(Outcome.UNAVAILABLE);
        }
        String id = userId == null ? "" : userId.strip().toLowerCase(Locale.ROOT);
        UserRole newRole = parseRole(role);
        if (newRole == null || !UUID_FORMAT.matcher(id).matches()) {
            return ChangeUserRoleResult.of(Outcome.INVALID);
        }
        try {
            UserSummary account = adminUsersApiClient.changeRole(accessToken, id, newRole);
            Outcome outcome = endsTheCallersSession(accessToken, id, newRole)
                    ? Outcome.CHANGED_OWN_ROLE : Outcome.CHANGED;
            return ChangeUserRoleResult.changed(outcome, displayName(account), newRole);
        } catch (AdminUsersApiClient.UserNotFoundException e) {
            return ChangeUserRoleResult.of(Outcome.USER_NOT_FOUND);
        } catch (AdminUsersApiClient.LastAdminCannotBeDemotedException e) {
            return ChangeUserRoleResult.of(Outcome.LAST_ADMIN);
        } catch (AdminUsersApiClient.InvalidRequestException e) {
            log.warn("Role change refused by the Auth API: {}", e.getClass().getSimpleName());
            return ChangeUserRoleResult.of(Outcome.INVALID);
        } catch (AdminUsersApiClient.ServiceUnavailableException e) {
            log.warn("Role change unavailable: {}", e.getClass().getSimpleName());
            return ChangeUserRoleResult.of(Outcome.UNAVAILABLE);
        }
    }

    private static UserRole parseRole(String role) {
        if (role == null) {
            return null;
        }
        try {
            return UserRole.valueOf(role.strip());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean endsTheCallersSession(String accessToken, String id, UserRole newRole) {
        try {
            return id.equalsIgnoreCase(claimsReader.subjectOf(accessToken))
                    && !newRole.name().equals(claimsReader.roleOf(accessToken));
        } catch (AccessTokenClaimsReader.UnreadableTokenException e) {
            return false;
        }
    }

    private static String displayName(UserSummary account) {
        return Stream.of(account.firstName(), account.lastName())
                .filter(part -> part != null && !part.isBlank())
                .map(String::strip)
                .reduce((first, last) -> first + " " + last)
                .orElse(account.email());
    }
}
