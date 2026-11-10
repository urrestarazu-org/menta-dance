package com.menta.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.menta.auth.application.dto.ListUsersQuery;
import com.menta.auth.application.dto.UserPage;
import com.menta.auth.application.port.out.UserAdminRepository;
import com.menta.auth.domain.model.Role;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListUsersUseCaseImplTest {

    @Mock private UserAdminRepository userAdminRepository;

    @Test
    void returns_the_page_the_repository_finds_for_the_query() {
        ListUsersQuery query = new ListUsersQuery(Role.STUDENT, "ada", 1, 20);
        UserPage page = new UserPage(List.of(), 1, 20, 0L, 0);
        when(userAdminRepository.search(query)).thenReturn(page);

        UserPage result = new ListUsersUseCaseImpl(userAdminRepository).list(query);

        assertThat(result).isSameAs(page);
        verify(userAdminRepository).search(query);
    }
}
