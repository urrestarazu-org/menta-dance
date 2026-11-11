package com.menta.auth.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.auth.application.dto.ChangeUserRoleCommand;
import com.menta.auth.application.dto.CreateUserCommand;
import com.menta.auth.application.dto.ListUsersQuery;
import com.menta.auth.application.dto.UserPage;
import com.menta.auth.application.dto.UserSummary;
import com.menta.auth.application.port.in.ChangeUserRoleUseCase;
import com.menta.auth.application.port.in.CreateUserUseCase;
import com.menta.auth.application.port.in.ListUsersUseCase;
import com.menta.auth.domain.exception.EmailAlreadyRegisteredException;
import com.menta.auth.domain.exception.LastAdminCannotBeDemotedException;
import com.menta.auth.domain.exception.UserNotFoundException;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.UserId;
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
 * MockMvc slice for the user administration endpoints (#378). The coarse ADMIN gate belongs to
 * {@code SecurityConfig}, not to this controller (see {@code SecurityConfigTest}).
 */
class AdminUserControllerTest {

    private static final String BASE_URL = "/api/v1/admin/users";
    private static final UUID ADMIN_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private ListUsersUseCase listUsersUseCase;
    private ChangeUserRoleUseCase changeUserRoleUseCase;
    private CreateUserUseCase createUserUseCase;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        listUsersUseCase = mock(ListUsersUseCase.class);
        changeUserRoleUseCase = mock(ChangeUserRoleUseCase.class);
        createUserUseCase = mock(CreateUserUseCase.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AdminUserController(listUsersUseCase, changeUserRoleUseCase, createUserUseCase))
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

    @Test
    void change_role_returns_the_updated_user_and_acts_on_behalf_of_the_authenticated_admin() throws Exception {
        when(changeUserRoleUseCase.changeRole(any())).thenReturn(summary(Role.INSTRUCTOR));

        mockMvc.perform(patch(BASE_URL + "/{userId}/role", USER_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"INSTRUCTOR\"}")
                .principal(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(USER_ID.toString())))
            .andExpect(jsonPath("$.role", is("INSTRUCTOR")))
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.tokenVersion").doesNotExist());

        verify(changeUserRoleUseCase).changeRole(new ChangeUserRoleCommand(ADMIN_ID, USER_ID, Role.INSTRUCTOR));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"role\":null}", "{\"role\":\"SUPERADMIN\"}", "{\"role\":\"instructor\"}",
        "{\"role\":", "not json", ""})
    void change_role_rejects_an_invalid_body(String body) throws Exception {
        mockMvc.perform(patch(BASE_URL + "/{userId}/role", USER_ID)
                .contentType(MediaType.APPLICATION_JSON).content(body).principal(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(changeUserRoleUseCase);
    }

    @Test
    void change_role_rejects_a_user_id_that_is_not_a_uuid() throws Exception {
        mockMvc.perform(patch(BASE_URL + "/not-a-uuid/role")
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"STUDENT\"}").principal(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(changeUserRoleUseCase);
    }

    @Test
    void change_role_maps_an_unknown_user_to_404() throws Exception {
        when(changeUserRoleUseCase.changeRole(any())).thenThrow(new UserNotFoundException(UserId.of(USER_ID)));

        mockMvc.perform(patch(BASE_URL + "/{userId}/role", USER_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"STUDENT\"}").principal(admin()))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("USER_NOT_FOUND")))
            .andExpect(jsonPath("$.detail", not(containsString(USER_ID.toString()))));
    }

    @Test
    void change_role_maps_the_last_admin_rule_to_409() throws Exception {
        when(changeUserRoleUseCase.changeRole(any())).thenThrow(new LastAdminCannotBeDemotedException());

        mockMvc.perform(patch(BASE_URL + "/{userId}/role", USER_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"STUDENT\"}").principal(admin()))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("LAST_ADMIN_CANNOT_BE_DEMOTED")));
    }

    private static final String CREATE_BODY =
        "{\"email\":\"staff@example.com\",\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"role\":\"RECEPTIONIST\"}";

    @Test
    void create_returns_201_with_the_pending_account_and_acts_on_behalf_of_the_authenticated_admin()
        throws Exception {
        when(createUserUseCase.create(any())).thenReturn(new UserSummary(
            USER_ID.toString(), "staff@example.com", "Ada", "Lovelace", Role.RECEPTIONIST,
            UserStatus.PENDING_ACTIVATION, false, LocalDateTime.of(2026, 11, 10, 9, 30)
        ));

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY).principal(admin()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", is(USER_ID.toString())))
            .andExpect(jsonPath("$.email", is("staff@example.com")))
            .andExpect(jsonPath("$.role", is("RECEPTIONIST")))
            .andExpect(jsonPath("$.status", is("PENDING_ACTIVATION")))
            .andExpect(jsonPath("$.demo", is(false)))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());

        verify(createUserUseCase).create(
            new CreateUserCommand(ADMIN_ID, "staff@example.com", "Ada", "Lovelace", Role.RECEPTIONIST)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{}",
        "{\"email\":\"staff@example.com\",\"firstName\":\"Ada\",\"lastName\":\"L\"}",
        "{\"email\":\"not-an-email\",\"firstName\":\"Ada\",\"lastName\":\"L\",\"role\":\"ADMIN\"}",
        "{\"email\":\"\",\"firstName\":\"Ada\",\"lastName\":\"L\",\"role\":\"ADMIN\"}",
        "{\"email\":\"a@b.com\",\"firstName\":\" \",\"lastName\":\"L\",\"role\":\"ADMIN\"}",
        "{\"email\":\"a@b.com\",\"firstName\":\"Ada\",\"lastName\":\"\",\"role\":\"ADMIN\"}",
        "{\"email\":\"a@b.com\",\"firstName\":\"Ada\",\"lastName\":\"L\",\"role\":\"SUPERADMIN\"}",
        "{\"email\":\"a@b.com\",\"firstName\":\"Ada\",\"lastName\":\"L\",\"role\":null}",
        "not json", ""})
    void create_rejects_an_invalid_body(String body) throws Exception {
        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(body).principal(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(createUserUseCase);
    }

    @Test
    void create_rejects_names_longer_than_one_hundred_characters() throws Exception {
        String body = "{\"email\":\"a@b.com\",\"firstName\":\"" + "a".repeat(101)
            + "\",\"lastName\":\"L\",\"role\":\"ADMIN\"}";

        mockMvc.perform(post(BASE_URL).contentType(MediaType.APPLICATION_JSON).content(body).principal(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")));

        verifyNoInteractions(createUserUseCase);
    }

    @Test
    void create_maps_a_semantic_rejection_such_as_the_student_role_to_invalid_request() throws Exception {
        when(createUserUseCase.create(any())).thenThrow(new IllegalArgumentException("STUDENT is not allowed"));

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY).principal(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")))
            .andExpect(jsonPath("$.detail", not(containsString("STUDENT"))));
    }

    @Test
    void create_maps_a_duplicated_email_to_409_with_a_stable_code() throws Exception {
        when(createUserUseCase.create(any())).thenThrow(new EmailAlreadyRegisteredException());

        mockMvc.perform(post(BASE_URL)
                .contentType(MediaType.APPLICATION_JSON).content(CREATE_BODY).principal(admin()))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code", is("EMAIL_ALREADY_REGISTERED")))
            .andExpect(jsonPath("$.detail", not(containsString("staff@example.com"))));
    }
}
