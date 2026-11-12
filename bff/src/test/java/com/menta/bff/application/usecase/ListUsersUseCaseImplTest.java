package com.menta.bff.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.bff.application.dto.UserListQuery;
import com.menta.bff.application.dto.UserPage;
import com.menta.bff.application.dto.UserRole;
import com.menta.bff.application.dto.UserStatus;
import com.menta.bff.application.dto.UserSummary;
import com.menta.bff.application.port.out.AdminUsersApiClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ListUsersUseCase")
class ListUsersUseCaseImplTest {

    private static final String TOKEN = "access-token-abc";

    @Mock
    private AdminUsersApiClient adminUsersApiClient;

    private ListUsersUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ListUsersUseCaseImpl(adminUsersApiClient);
    }

    private static UserSummary user(String email, String first, String last) {
        return new UserSummary("id-" + email, email, first, last, UserRole.INSTRUCTOR, UserStatus.ACTIVE,
                LocalDateTime.of(2026, 10, 1, 9, 30));
    }

    private static UserPage page(int index, int totalPages, long total, UserSummary... items) {
        return new UserPage(List.of(items), index, 20, total, totalPages);
    }

    private UserListQuery lastQuery() {
        ArgumentCaptor<UserListQuery> captor = ArgumentCaptor.forClass(UserListQuery.class);
        verify(adminUsersApiClient).listUsers(eq(TOKEN), captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("words each account and carries the paging metadata")
    void wordsTheRows() {
        when(adminUsersApiClient.listUsers(TOKEN, new UserListQuery(null, null, 0, 20)))
                .thenReturn(page(0, 2, 22, user("ada@example.com", "Ada", "Lovelace")));

        UsersListView view = useCase.execute(TOKEN, null, null, null);

        assertThat(view.unavailable()).isFalse();
        assertThat(view.rows()).containsExactly(new UserRow("id-ada@example.com", "ada@example.com", "Ada Lovelace",
                UserRole.INSTRUCTOR, UserStatus.ACTIVE, LocalDate.of(2026, 10, 1), "1 de octubre de 2026"));
        assertThat(view.page()).isZero();
        assertThat(view.totalPages()).isEqualTo(2);
        assertThat(view.totalElements()).isEqualTo(22L);
        assertThat(view.paginated()).isTrue();
        assertThat(view.hasPrevious()).isFalse();
        assertThat(view.hasNext()).isTrue();
        assertThat(view.pageNumber()).isEqualTo(1);
        assertThat(view.nextPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("a missing name part is left out and an account without profile gets a neutral label")
    void namesWithoutProfile() {
        when(adminUsersApiClient.listUsers(any(), any())).thenReturn(page(0, 1, 4,
                user("a@example.com", "Ada", null), user("b@example.com", null, "Lovelace"),
                user("c@example.com", " ", ""), user("d@example.com", null, null)));

        UsersListView view = useCase.execute(TOKEN, null, null, 0);

        assertThat(view.rows()).extracting(UserRow::displayName)
                .containsExactly("Ada", "Lovelace", "Sin nombre", "Sin nombre");
    }

    @Test
    @DisplayName("the role and the text are passed on and echoed in the view")
    void passesTheFilters() {
        when(adminUsersApiClient.listUsers(any(), any())).thenReturn(page(0, 1, 0));

        UsersListView view = useCase.execute(TOKEN, "INSTRUCTOR", "  ada  ", 0);

        assertThat(lastQuery()).isEqualTo(new UserListQuery(UserRole.INSTRUCTOR, "ada", 0, 20));
        assertThat(view.role()).isEqualTo(UserRole.INSTRUCTOR);
        assertThat(view.text()).isEqualTo("ada");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "WIZARD", "admin", "ADMIN,STUDENT", "STUDENT; DROP"})
    @DisplayName("a blank or unknown role means no role filter")
    void unknownRoleMeansNoFilter(String role) {
        when(adminUsersApiClient.listUsers(any(), any())).thenReturn(page(0, 1, 0));

        UsersListView view = useCase.execute(TOKEN, role, null, 0);

        assertThat(lastQuery().role()).isNull();
        assertThat(view.role()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   \t "})
    @DisplayName("a blank text means no text filter")
    void blankTextMeansNoFilter(String text) {
        when(adminUsersApiClient.listUsers(any(), any())).thenReturn(page(0, 1, 0));

        UsersListView view = useCase.execute(TOKEN, null, text, 0);

        assertThat(lastQuery().text()).isNull();
        assertThat(view.text()).isNull();
    }

    @Test
    @DisplayName("the text is cut to 100 characters before it reaches the API")
    void cutsTheText() {
        when(adminUsersApiClient.listUsers(any(), any())).thenReturn(page(0, 1, 0));

        UsersListView view = useCase.execute(TOKEN, null, "x".repeat(150), 0);

        assertThat(lastQuery().text()).isEqualTo("x".repeat(100));
        assertThat(view.text()).hasSize(100);
    }

    @Test
    @DisplayName("the cut never leaves half of a surrogate pair, and never ends in a space")
    void cutIsClean() {
        when(adminUsersApiClient.listUsers(any(), any())).thenReturn(page(0, 1, 0));
        String text = "x".repeat(99) + "😀" + "tail";

        UsersListView view = useCase.execute(TOKEN, null, text, 0);

        assertThat(view.text()).isEqualTo("x".repeat(99));
        assertThat(Character.isHighSurrogate(view.text().charAt(view.text().length() - 1))).isFalse();
    }

    @Test
    @DisplayName("a cut that lands after a space drops the space")
    void cutDropsTrailingSpace() {
        when(adminUsersApiClient.listUsers(any(), any())).thenReturn(page(0, 1, 0));

        UsersListView view = useCase.execute(TOKEN, null, "x".repeat(99) + " tail", 0);

        assertThat(view.text()).isEqualTo("x".repeat(99));
    }

    @Test
    @DisplayName("the page size is fixed at 20 and a negative or missing page reads as the first")
    void fixedSizeAndPageFloor() {
        when(adminUsersApiClient.listUsers(any(), any())).thenReturn(page(0, 1, 0));

        useCase.execute(TOKEN, null, null, -5);
        useCase.execute(TOKEN, null, null, null);

        verify(adminUsersApiClient, times(2))
                .listUsers(TOKEN, new UserListQuery(null, null, 0, 20));
    }

    @Test
    @DisplayName("a page past the end shows the last page instead of an empty one")
    void clampsToTheLastPage() {
        when(adminUsersApiClient.listUsers(TOKEN, new UserListQuery(null, null, 9, 20)))
                .thenReturn(page(9, 3, 41));
        when(adminUsersApiClient.listUsers(TOKEN, new UserListQuery(null, null, 2, 20)))
                .thenReturn(page(2, 3, 41, user("z@example.com", "Zoe", "Zeta")));

        UsersListView view = useCase.execute(TOKEN, null, null, 9);

        assertThat(view.page()).isEqualTo(2);
        assertThat(view.rows()).hasSize(1);
        assertThat(view.hasNext()).isFalse();
        assertThat(view.hasPrevious()).isTrue();
        assertThat(view.previousPage()).isEqualTo(1);
    }

    @Test
    @DisplayName("an empty result with no pages is an empty view, not a second request")
    void emptyResult() {
        when(adminUsersApiClient.listUsers(TOKEN, new UserListQuery(null, "zzz", 0, 20)))
                .thenReturn(page(0, 0, 0));

        UsersListView view = useCase.execute(TOKEN, null, "zzz", 0);

        assertThat(view.empty()).isTrue();
        assertThat(view.paginated()).isFalse();
        assertThat(view.unavailable()).isFalse();
        verify(adminUsersApiClient).listUsers(any(), any());
    }

    @Test
    @DisplayName("an unavailable API reads as an unavailable view that keeps the filters")
    void unavailableKeepsTheFilters() {
        when(adminUsersApiClient.listUsers(any(), any()))
                .thenThrow(new AdminUsersApiClient.ServiceUnavailableException("down"));

        UsersListView view = useCase.execute(TOKEN, "STUDENT", "ada", 3);

        assertThat(view.unavailable()).isTrue();
        assertThat(view.empty()).isFalse();
        assertThat(view.rows()).isEmpty();
        assertThat(view.role()).isEqualTo(UserRole.STUDENT);
        assertThat(view.text()).isEqualTo("ada");
    }

    @Test
    @DisplayName("a query the API rejects also reads as unavailable: the user cannot fix it")
    void rejectedQueryIsUnavailable() {
        when(adminUsersApiClient.listUsers(any(), any()))
                .thenThrow(new AdminUsersApiClient.InvalidRequestException("rejected"));

        assertThat(useCase.execute(TOKEN, null, null, 0).unavailable()).isTrue();
    }

    @Test
    @DisplayName("without an access token nothing is requested and the view is unavailable")
    void withoutToken() {
        assertThat(useCase.execute(null, null, null, 0).unavailable()).isTrue();
        assertThat(useCase.execute(" ", null, null, 0).unavailable()).isTrue();

        verify(adminUsersApiClient, never()).listUsers(any(), any());
    }
}
