package com.menta.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.menta.auth.application.dto.UserSummary;
import com.menta.auth.application.port.out.UserAdminRepository;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResolveUserDisplayNamesUseCaseImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 10, 0);

    @Mock private UserAdminRepository userAdminRepository;
    @Captor private ArgumentCaptor<Collection<UserId>> requestedIds;

    private ResolveUserDisplayNamesUseCaseImpl useCase() {
        return new ResolveUserDisplayNamesUseCaseImpl(userAdminRepository);
    }

    private static UserSummary summary(UUID id, String email, String firstName, String lastName) {
        return new UserSummary(id.toString(), email, firstName, lastName, Role.STUDENT, UserStatus.ACTIVE, false, NOW);
    }

    @Test
    void an_empty_request_resolves_nothing_without_reading_the_repository() {
        Map<UUID, String> names = useCase().resolve(List.of());

        assertThat(names).isEmpty();
        verifyNoInteractions(userAdminRepository);
    }

    @Test
    void a_request_of_only_null_ids_resolves_nothing_without_reading_the_repository() {
        Map<UUID, String> names = useCase().resolve(Arrays.asList(null, null));

        assertThat(names).isEmpty();
        verifyNoInteractions(userAdminRepository);
    }

    @Test
    void a_null_request_is_rejected() {
        assertThatNullPointerException().isThrownBy(() -> useCase().resolve(null));
    }

    @Test
    void the_name_of_a_user_with_a_profile_is_the_first_and_last_name() {
        UUID id = UUID.randomUUID();
        when(userAdminRepository.findSummariesByIds(anyCollection()))
            .thenReturn(List.of(summary(id, "ana@example.com", "Ana", "Perez")));

        Map<UUID, String> names = useCase().resolve(List.of(id));

        assertThat(names).containsExactly(Map.entry(id, "Ana Perez"));
    }

    @Test
    void a_user_without_a_profile_is_named_by_its_email() {
        UUID id = UUID.randomUUID();
        when(userAdminRepository.findSummariesByIds(anyCollection()))
            .thenReturn(List.of(summary(id, "admin@example.com", null, null)));

        Map<UUID, String> names = useCase().resolve(List.of(id));

        assertThat(names).containsExactly(Map.entry(id, "admin@example.com"));
    }

    @Test
    void an_unknown_user_is_absent_from_the_result_instead_of_failing_it() {
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        when(userAdminRepository.findSummariesByIds(anyCollection()))
            .thenReturn(List.of(summary(known, "ana@example.com", "Ana", "Perez")));

        Map<UUID, String> names = useCase().resolve(List.of(known, unknown));

        assertThat(names).containsOnlyKeys(known).doesNotContainKey(unknown);
    }

    @Test
    void every_user_is_asked_in_one_single_read_with_the_distinct_non_null_ids() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<UUID> ids = new ArrayList<>(Arrays.asList(first, second, first, null));
        when(userAdminRepository.findSummariesByIds(anyCollection())).thenReturn(List.of());

        useCase().resolve(ids);

        verify(userAdminRepository).findSummariesByIds(requestedIds.capture());
        assertThat(requestedIds.getValue()).extracting(UserId::getValue).containsExactlyInAnyOrder(first, second);
        verifyNoMoreInteractions(userAdminRepository);
    }

    @Test
    void the_result_cannot_be_modified() {
        UUID id = UUID.randomUUID();
        when(userAdminRepository.findSummariesByIds(anyCollection()))
            .thenReturn(List.of(summary(id, "ana@example.com", "Ana", "Perez")));

        Map<UUID, String> names = useCase().resolve(List.of(id));

        assertThatThrownBy(() -> names.put(UUID.randomUUID(), "Otro"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
