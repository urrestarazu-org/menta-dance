package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CreateUserCommand;
import com.menta.bff.application.dto.UserRole;
import com.menta.bff.application.port.out.AdminUsersApiClient;
import com.menta.bff.application.usecase.CreateUserResult.Outcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link CreateUserUseCase}. Whether the email is free and what the data may look like is
 * decided by the Auth API; this class only refuses what can never be sent (a role that is not staff, blank
 * data) and translates the port's failures into results. A failure is logged by its class only: neither the
 * token nor the data of the account (email, names) may reach a log.
 */
@Slf4j
@RequiredArgsConstructor
public class CreateUserUseCaseImpl implements CreateUserUseCase {

    private final AdminUsersApiClient adminUsersApiClient;

    @Override
    public CreateUserResult execute(
            String accessToken, String email, String firstName, String lastName, UserRole role) {
        if (accessToken == null || accessToken.isBlank()) {
            return CreateUserResult.of(Outcome.UNAVAILABLE);
        }
        String cleanEmail = strip(email);
        String cleanFirstName = strip(firstName);
        String cleanLastName = strip(lastName);
        if (role == null || !UserRole.staffRoles().contains(role)
                || cleanEmail.isEmpty() || cleanFirstName.isEmpty() || cleanLastName.isEmpty()) {
            return CreateUserResult.of(Outcome.INVALID);
        }
        try {
            adminUsersApiClient.createUser(
                    accessToken, new CreateUserCommand(cleanEmail, cleanFirstName, cleanLastName, role));
            return CreateUserResult.created(cleanFirstName + " " + cleanLastName);
        } catch (AdminUsersApiClient.EmailAlreadyRegisteredException e) {
            return CreateUserResult.of(Outcome.EMAIL_ALREADY_REGISTERED);
        } catch (AdminUsersApiClient.InvalidRequestException e) {
            log.warn("Account creation refused by the Auth API: {}", e.getClass().getSimpleName());
            return CreateUserResult.of(Outcome.INVALID);
        } catch (AdminUsersApiClient.ServiceUnavailableException e) {
            log.warn("Account creation unavailable: {}", e.getClass().getSimpleName());
            return CreateUserResult.of(Outcome.UNAVAILABLE);
        }
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }
}
