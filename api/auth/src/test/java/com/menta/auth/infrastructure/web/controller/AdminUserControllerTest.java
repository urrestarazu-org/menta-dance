package com.menta.auth.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.auth.application.dto.ListUsersQuery;
import com.menta.auth.application.dto.UserPage;
import com.menta.auth.application.dto.UserSummary;
import com.menta.auth.application.port.in.ListUsersUseCase;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.UserStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * MockMvc slice for the user administration listing endpoint (#378). The coarse ADMIN gate belongs to
 * {@code SecurityConfig}, not to this controller (see {@code SecurityConfigTest}).
 */
class AdminUserControllerTest {

    private static final String BASE_URL = "/api/v1/admin/users";
    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private ListUsersUseCase listUsersUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        listUsersUseCase = mock(ListUsersUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminUserController(listUsersUseCase))
            .setControllerAdvice(new AdminUserExceptionHandler())
            .build();
    }

    private static Authentication admin() {
        return new UsernamePasswordAuthenticationToken(
            ADMIN_ID.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    private static UserSummary summary(Role role) {
        return new UserSummary(
            USER_ID.toString(), "ada@example.com", "Ada", "Lovelace", role, UserStatus.ACTIVE, false,
            LocalDateTime.of(2026, 10, 1, 9, 30)
        );
    }

    private ListUsersQuery capturedQuery() {
        ArgumentCaptor<ListUsersQuery> captor = ArgumentCaptor.forClass(ListUsersQuery.class);
        verify(listUsersUseCase).list(captor.capture());
        return captor.getValue();
    }

    @Test
    void list_uses_the_default_paging_and_no_filter() throws Exception {
        when(listUsersUseCase.list(any())).thenReturn(new UserPage(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get(BASE_URL).principal(admin())).andExpect(status().isOk());

        assertThat(capturedQuery()).isEqualTo(new ListUsersQuery(null, null, 0, 20));
    }

    @Test
    void list_passes_role_search_and_paging_to_the_use_case() throws Exception {
        when(listUsersUseCase.list(any())).thenReturn(new UserPage(List.of(), 2, 10, 0L, 0));

        mockMvc.perform(get(BASE_URL).param("role", "INSTRUCTOR").param("q", "ada")
                .param("page", "2").param("size", "10").principal(admin()))
            .andExpect(status().isOk());

        assertThat(capturedQuery()).isEqualTo(new ListUsersQuery(Role.INSTRUCTOR, "ada", 2, 10));
    }

    @Test
    void list_treats_a_blank_search_as_no_filter() throws Exception {
        when(listUsersUseCase.list(any())).thenReturn(new UserPage(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get(BASE_URL).param("q", "   ").principal(admin())).andExpect(status().isOk());

        assertThat(capturedQuery().search()).isNull();
    }

    @Test
    void list_returns_the_page_with_the_summaries_and_never_credentials() throws Exception {
        when(listUsersUseCase.list(any())).thenReturn(new UserPage(List.of(summary(Role.INSTRUCTOR)), 0, 20, 1L, 1));

        mockMvc.perform(get(BASE_URL).principal(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page", is(0)))
            .andExpect(jsonPath("$.size", is(20)))
            .andExpect(jsonPath("$.totalElements", is(1)))
            .andExpect(jsonPath("$.totalPages", is(1)))
            .andExpect(jsonPath("$.items[0].id", is(USER_ID.toString())))
            .andExpect(jsonPath("$.items[0].email", is("ada@example.com")))
            .andExpect(jsonPath("$.items[0].firstName", is("Ada")))
            .andExpect(jsonPath("$.items[0].lastName", is("Lovelace")))
            .andExpect(jsonPath("$.items[0].role", is("INSTRUCTOR")))
            .andExpect(jsonPath("$.items[0].status", is("ACTIVE")))
            .andExpect(jsonPath("$.items[0].demo", is(false)))
            .andExpect(jsonPath("$.items[0].createdAt", is("2026-10-01T09:30:00")))
            .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist())
            .andExpect(jsonPath("$.items[0].tokenVersion").doesNotExist());
    }

    @Test
    void list_rejects_a_size_above_the_maximum_instead_of_clamping_it() throws Exception {
        mockMvc.perform(get(BASE_URL).param("size", "51").principal(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(listUsersUseCase);
    }

    @Test
    void list_rejects_a_search_longer_than_one_hundred_characters() throws Exception {
        mockMvc.perform(get(BASE_URL).param("q", "a".repeat(101)).principal(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"role=SUPERADMIN", "role=admin", "page=-1", "page=abc", "size=0", "size=abc"})
    void list_rejects_malformed_parameters(String parameter) throws Exception {
        String[] pair = parameter.split("=");

        mockMvc.perform(get(BASE_URL).param(pair[0], pair[1]).principal(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));
    }
}
