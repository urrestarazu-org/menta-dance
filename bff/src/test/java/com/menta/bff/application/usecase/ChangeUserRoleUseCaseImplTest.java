package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.UserRole;
import com.menta.bff.application.dto.UserStatus;
import com.menta.bff.application.dto.UserSummary;
import com.menta.bff.application.port.out.AccessTokenClaimsReader;
import com.menta.bff.application.port.out.AdminUsersApiClient;
import com.menta.bff.application.usecase.ChangeUserRoleResult.Outcome;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChangeUserRoleUseCase")
class ChangeUserRoleUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";
    private static final String USER_ID = "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f";
    private static final String OWN_ID = "0a1b2c3d-1d2a-4e3b-9c4d-5b6f7c8e9f00";

    @Mock
    private AdminUsersApiClient adminUsersApiClient;

    @Mock
    private AccessTokenClaimsReader claimsReader;

    private ChangeUserRoleUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ChangeUserRoleUseCaseImpl(adminUsersApiClient, claimsReader);
        lenient().when(claimsReader.subjectOf(TOKEN)).thenReturn(OWN_ID);
        lenient().when(claimsReader.roleOf(TOKEN)).thenReturn("ADMIN");
    }

    private static UserSummary account(String id, String first, String last, UserRole role) {
        return new UserSummary(id, "ada@example.com", first, last, role, UserStatus.ACTIVE,
                LocalDateTime.parse("2026-11-10T09:30:00"));
    }

    private void apiAnswers(UserSummary summary, UserRole role) {
        when(adminUsersApiClient.changeRole(TOKEN, summary.id(), role)).thenReturn(summary);
    }

    @Test
    @DisplayName("changes the role and names the account's owner and the new role in the result")
    void changesTheRole() {
        apiAnswers(account(USER_ID, "Ada", "Lovelace", UserRole.INSTRUCTOR), UserRole.INSTRUCTOR);

        ChangeUserRoleResult result = useCase.execute(TOKEN, USER_ID, "INSTRUCTOR");

        assertThat(result.outcome()).isEqualTo(Outcome.CHANGED);
        assertThat(result.displayName()).isEqualTo("Ada Lovelace");
        assertThat(result.role()).isEqualTo(UserRole.INSTRUCTOR);
    }

    @ParameterizedTest(name = "{0} is allowed")
    @EnumSource(UserRole.class)
    @DisplayName("the four roles reach the API, STUDENT included")
    void everyRoleIsAllowed(UserRole role) {
        apiAnswers(account(USER_ID, "Ada", "Lovelace", role), role);

        assertThat(useCase.execute(TOKEN, USER_ID, role.name()).outcome()).isEqualTo(Outcome.CHANGED);
    }

    @Test
    @DisplayName("the id is trimmed and lower-cased before it reaches the API; the role is trimmed")
    void idAndRoleAreNormalized() {
        apiAnswers(account(USER_ID, "Ada", "Lovelace", UserRole.ADMIN), UserRole.ADMIN);

        ChangeUserRoleResult result = useCase.execute(TOKEN, "  " + USER_ID.toUpperCase() + " ", " ADMIN ");

        assertThat(result.outcome()).isEqualTo(Outcome.CHANGED);
        verify(adminUsersApiClient).changeRole(TOKEN, USER_ID, UserRole.ADMIN);
    }

    @Test
    @DisplayName("an account without a name is called by its email")
    void nameFallsBackToEmail() {
        apiAnswers(account(USER_ID, null, "  ", UserRole.ADMIN), UserRole.ADMIN);

        assertThat(useCase.execute(TOKEN, USER_ID, "ADMIN").displayName()).isEqualTo("ada@example.com");
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" is not an account id")
    @NullAndEmptySource
    @ValueSource(strings = {
        "  ", "1", "not-a-uuid", "1-1-1-1-1", "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5", "../admin",
        "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f/role", "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f?x=1",
        "5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5g"})
    @DisplayName("an id that is not a UUID is refused before calling the API")
    void malformedIdIsRefusedLocally(String id) {
        assertThat(useCase.execute(TOKEN, id, "ADMIN").outcome()).isEqualTo(Outcome.INVALID);

        verifyNoInteractions(adminUsersApiClient);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" is not a role")
    @NullAndEmptySource
    @ValueSource(strings = {" ", "WIZARD", "admin", "ROLE_ADMIN", "ADMIN,STUDENT"})
    @DisplayName("a missing or unknown role is refused before calling the API")
    void unknownRoleIsRefusedLocally(String role) {
        assertThat(useCase.execute(TOKEN, USER_ID, role).outcome()).isEqualTo(Outcome.INVALID);

        verifyNoInteractions(adminUsersApiClient);
    }

    @ParameterizedTest(name = "[{index}] no token is unavailable")
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("without an access token the API is not called and the result is unavailable")
    void noTokenIsUnavailable(String token) {
        assertThat(useCase.execute(token, USER_ID, "ADMIN").outcome()).isEqualTo(Outcome.UNAVAILABLE);

        verifyNoInteractions(adminUsersApiClient);
    }

    @Test
    @DisplayName("an administrator who changes their own role gets a result of its own: their session is gone")
    void ownRoleChange() {
        apiAnswers(account(OWN_ID, "Root", "Admin", UserRole.INSTRUCTOR), UserRole.INSTRUCTOR);

        ChangeUserRoleResult result = useCase.execute(TOKEN, OWN_ID.toUpperCase(), "INSTRUCTOR");

        assertThat(result.outcome()).isEqualTo(Outcome.CHANGED_OWN_ROLE);
        assertThat(result.role()).isEqualTo(UserRole.INSTRUCTOR);
    }

    @Test
    @DisplayName("asking for the role one already has changes nothing in the API, so the session stays")
    void ownRoleUnchanged() {
        apiAnswers(account(OWN_ID, "Root", "Admin", UserRole.ADMIN), UserRole.ADMIN);

        assertThat(useCase.execute(TOKEN, OWN_ID, "ADMIN").outcome()).isEqualTo(Outcome.CHANGED);
    }

    @Test
    @DisplayName("a token whose identity cannot be read is treated as another person's change")
    void unreadableTokenIsNotOwn() {
        when(claimsReader.subjectOf(TOKEN)).thenThrow(new AccessTokenClaimsReader.UnreadableTokenException("no sub"));
        apiAnswers(account(OWN_ID, "Root", "Admin", UserRole.INSTRUCTOR), UserRole.INSTRUCTOR);

        assertThat(useCase.execute(TOKEN, OWN_ID, "INSTRUCTOR").outcome()).isEqualTo(Outcome.CHANGED);
    }

    @Test
    @DisplayName("a missing account is its own result")
    void userNotFound() {
        doThrow(new AdminUsersApiClient.UserNotFoundException("missing"))
                .when(adminUsersApiClient).changeRole(any(), any(), any());

        ChangeUserRoleResult result = useCase.execute(TOKEN, USER_ID, "ADMIN");

        assertThat(result.outcome()).isEqualTo(Outcome.USER_NOT_FOUND);
        assertThat(result.displayName()).isNull();
    }

    @Test
    @DisplayName("demoting the only active administrator is its own result")
    void lastAdmin() {
        doThrow(new AdminUsersApiClient.LastAdminCannotBeDemotedException("last"))
                .when(adminUsersApiClient).changeRole(any(), any(), any());

        assertThat(useCase.execute(TOKEN, OWN_ID, "STUDENT").outcome()).isEqualTo(Outcome.LAST_ADMIN);
    }

    @Test
    @DisplayName("a rejection by the API is an invalid result")
    void apiRejection() {
        doThrow(new AdminUsersApiClient.InvalidRequestException("rejected"))
                .when(adminUsersApiClient).changeRole(any(), any(), any());

        assertThat(useCase.execute(TOKEN, USER_ID, "ADMIN").outcome()).isEqualTo(Outcome.INVALID);
    }

    @Test
    @DisplayName("any other failure is an unavailable result, never an exception")
    void unavailable() {
        doThrow(new AdminUsersApiClient.ServiceUnavailableException("down"))
                .when(adminUsersApiClient).changeRole(any(), any(), any());

        assertThat(useCase.execute(TOKEN, USER_ID, "ADMIN").outcome()).isEqualTo(Outcome.UNAVAILABLE);
    }
}
