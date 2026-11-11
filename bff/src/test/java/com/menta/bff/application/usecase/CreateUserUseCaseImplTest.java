package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.menta.bff.application.dto.CreateUserCommand;
import com.menta.bff.application.dto.UserRole;
import com.menta.bff.application.port.out.AdminUsersApiClient;
import com.menta.bff.application.usecase.CreateUserResult.Outcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreateUserUseCase")
class CreateUserUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";

    @Mock
    private AdminUsersApiClient adminUsersApiClient;

    private CreateUserUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateUserUseCaseImpl(adminUsersApiClient);
    }

    @Test
    @DisplayName("creates the account with the data trimmed and names the owner in the result")
    void createsTheAccount() {
        CreateUserResult result =
                useCase.execute(TOKEN, "  Recepcion@Example.com ", " Ada ", "\tLovelace  ", UserRole.RECEPTIONIST);

        assertThat(result.outcome()).isEqualTo(Outcome.CREATED);
        assertThat(result.displayName()).isEqualTo("Ada Lovelace");
        ArgumentCaptor<CreateUserCommand> captor = ArgumentCaptor.forClass(CreateUserCommand.class);
        verify(adminUsersApiClient).createUser(org.mockito.ArgumentMatchers.eq(TOKEN), captor.capture());
        // The email keeps its case: the API compares it case-insensitively and registration does not fold it either.
        assertThat(captor.getValue()).isEqualTo(
                new CreateUserCommand("Recepcion@Example.com", "Ada", "Lovelace", UserRole.RECEPTIONIST));
    }

    @ParameterizedTest(name = "{0} is allowed")
    @EnumSource(value = UserRole.class, names = {"INSTRUCTOR", "RECEPTIONIST", "ADMIN"})
    @DisplayName("the three staff roles reach the API")
    void staffRolesAreAllowed(UserRole role) {
        CreateUserResult result = useCase.execute(TOKEN, "a@example.com", "Ada", "Lovelace", role);

        assertThat(result.outcome()).isEqualTo(Outcome.CREATED);
    }

    @Test
    @DisplayName("STUDENT and a missing role are refused before calling the API")
    void studentAndNoRoleAreRefusedLocally() {
        assertThat(useCase.execute(TOKEN, "a@example.com", "Ada", "Lovelace", UserRole.STUDENT).outcome())
                .isEqualTo(Outcome.INVALID);
        assertThat(useCase.execute(TOKEN, "a@example.com", "Ada", "Lovelace", null).outcome())
                .isEqualTo(Outcome.INVALID);

        verifyNoInteractions(adminUsersApiClient);
    }

    @ParameterizedTest(name = "[{index}] blank data is refused before calling the API")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("a blank email, first name or last name is refused before calling the API")
    void blankDataIsRefusedLocally(String blank) {
        assertThat(useCase.execute(TOKEN, blank, "Ada", "Lovelace", UserRole.ADMIN).outcome())
                .isEqualTo(Outcome.INVALID);
        assertThat(useCase.execute(TOKEN, "a@example.com", blank, "Lovelace", UserRole.ADMIN).outcome())
                .isEqualTo(Outcome.INVALID);
        assertThat(useCase.execute(TOKEN, "a@example.com", "Ada", blank, UserRole.ADMIN).outcome())
                .isEqualTo(Outcome.INVALID);

        verify(adminUsersApiClient, never()).createUser(any(), any());
    }

    @ParameterizedTest(name = "[{index}] no token is unavailable")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("without an access token the API is not called and the result is unavailable")
    void noTokenIsUnavailable(String token) {
        CreateUserResult result = useCase.execute(token, "a@example.com", "Ada", "Lovelace", UserRole.ADMIN);

        assertThat(result.outcome()).isEqualTo(Outcome.UNAVAILABLE);
        verifyNoInteractions(adminUsersApiClient);
    }

    @Test
    @DisplayName("an email that already has an account is its own result")
    void emailAlreadyRegistered() {
        doThrow(new AdminUsersApiClient.EmailAlreadyRegisteredException("taken"))
                .when(adminUsersApiClient).createUser(any(), any());

        CreateUserResult result = useCase.execute(TOKEN, "a@example.com", "Ada", "Lovelace", UserRole.ADMIN);

        assertThat(result.outcome()).isEqualTo(Outcome.EMAIL_ALREADY_REGISTERED);
        assertThat(result.displayName()).isNull();
    }

    @Test
    @DisplayName("a rejection by the API is an invalid result")
    void apiRejection() {
        doThrow(new AdminUsersApiClient.InvalidRequestException("rejected"))
                .when(adminUsersApiClient).createUser(any(), any());

        assertThat(useCase.execute(TOKEN, "a@example.com", "Ada", "Lovelace", UserRole.ADMIN).outcome())
                .isEqualTo(Outcome.INVALID);
    }

    @Test
    @DisplayName("any other failure is an unavailable result, never an exception")
    void unavailable() {
        doThrow(new AdminUsersApiClient.ServiceUnavailableException("down"))
                .when(adminUsersApiClient).createUser(any(), any());

        assertThat(useCase.execute(TOKEN, "a@example.com", "Ada", "Lovelace", UserRole.ADMIN).outcome())
                .isEqualTo(Outcome.UNAVAILABLE);
    }
}
