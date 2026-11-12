package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.UserListQuery;
import com.menta.bff.application.dto.UserPage;
import com.menta.bff.application.dto.UserRole;
import com.menta.bff.application.dto.UserSummary;
import com.menta.bff.application.port.out.AdminUsersApiClient;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link ListUsersUseCase}. Which accounts match, and in which order, is decided by the
 * Auth API; this class only normalizes what comes from the query string and words the answer. A failed
 * lookup is logged without token, filters or rows (they hold emails and names) and reads as an
 * unavailable view.
 */
@Slf4j
@RequiredArgsConstructor
public class ListUsersUseCaseImpl implements ListUsersUseCase {

    /** Fixed page size: the user never chooses it, and it stays under the API's maximum of 50. */
    static final int PAGE_SIZE = 20;

    /** The API rejects a longer search text, so it is cut here. */
    static final int MAX_TEXT_LENGTH = 100;

    private static final String NO_NAME = "Sin nombre";
    private static final DateTimeFormatter DAY_FORMAT =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-AR"));

    private final AdminUsersApiClient adminUsersApiClient;

    @Override
    public UsersListView execute(String accessToken, String role, String text, Integer page) {
        UserRole roleFilter = parseRole(role);
        String textFilter = normalizeText(text);
        if (accessToken == null || accessToken.isBlank()) {
            return UsersListView.unavailableView(roleFilter, textFilter);
        }
        int pageIndex = page == null ? 0 : Math.max(page, 0);
        try {
            UserPage result = adminUsersApiClient.listUsers(
                    accessToken, new UserListQuery(roleFilter, textFilter, pageIndex, PAGE_SIZE));
            if (result.items().isEmpty() && result.totalPages() > 0 && pageIndex >= result.totalPages()) {
                result = adminUsersApiClient.listUsers(
                        accessToken, new UserListQuery(roleFilter, textFilter, result.totalPages() - 1, PAGE_SIZE));
            }
            return toView(roleFilter, textFilter, result);
        } catch (AdminUsersApiClient.ServiceUnavailableException | AdminUsersApiClient.InvalidRequestException e) {
            log.warn("Users list unavailable, rendering the screen without it: {}", e.getMessage());
            return UsersListView.unavailableView(roleFilter, textFilter);
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

    /** Trims, treats blank as no filter and cuts to the API's limit without splitting a surrogate pair. */
    private static String normalizeText(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.strip();
        if (trimmed.length() > MAX_TEXT_LENGTH) {
            int end = Character.isHighSurrogate(trimmed.charAt(MAX_TEXT_LENGTH - 1))
                    ? MAX_TEXT_LENGTH - 1 : MAX_TEXT_LENGTH;
            trimmed = trimmed.substring(0, end).strip();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static UsersListView toView(UserRole role, String text, UserPage result) {
        List<UserRow> rows = result.items().stream().map(ListUsersUseCaseImpl::toRow).toList();
        return new UsersListView(role, text, rows, result.page(), result.totalPages(), result.totalElements(), false);
    }

    private static UserRow toRow(UserSummary user) {
        return new UserRow(user.id(), user.email(), displayName(user), user.role(), user.status(),
                user.createdAt().toLocalDate(), DAY_FORMAT.format(user.createdAt()));
    }

    private static String displayName(UserSummary user) {
        return Stream.of(user.firstName(), user.lastName())
                .filter(part -> part != null && !part.isBlank())
                .map(String::strip)
                .reduce((first, last) -> first + " " + last)
                .orElse(NO_NAME);
    }
}
