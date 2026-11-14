package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.UserListQuery;
import com.menta.bff.application.dto.UserPage;
import com.menta.bff.application.dto.UserRole;
import com.menta.bff.application.dto.UserSummary;
import com.menta.bff.application.port.out.AdminUsersApiClient;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link ListInstructorsUseCase}. A failed lookup is logged without token or rows (they hold
 * emails and names).
 */
@Slf4j
@RequiredArgsConstructor
public class ListInstructorsUseCaseImpl implements ListInstructorsUseCase {

    /** The API's maximum page size. */
    static final int PAGE_SIZE = 50;

    private final AdminUsersApiClient adminUsersApiClient;

    @Override
    public InstructorOptions execute(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return InstructorOptions.unavailableOptions();
        }
        try {
            UserPage page = adminUsersApiClient.listUsers(
                    accessToken, new UserListQuery(UserRole.INSTRUCTOR, null, 0, PAGE_SIZE));
            List<InstructorOptions.Option> options = page.items().stream()
                    .map(user -> new InstructorOptions.Option(user.id(), nameOf(user))).toList();
            return new InstructorOptions(options, page.totalElements() > options.size(), false);
        } catch (AdminUsersApiClient.ServiceUnavailableException | AdminUsersApiClient.InvalidRequestException e) {
            log.warn("Instructors unavailable, rendering the course form without them: {}", e.getMessage());
            return InstructorOptions.unavailableOptions();
        }
    }

    /** The name, or the email for an account without a profile, so the stored professor name is never blank. */
    private static String nameOf(UserSummary user) {
        return Stream.of(user.firstName(), user.lastName())
                .filter(part -> part != null && !part.isBlank())
                .map(String::strip)
                .reduce((first, last) -> first + " " + last)
                .orElse(user.email());
    }
}
