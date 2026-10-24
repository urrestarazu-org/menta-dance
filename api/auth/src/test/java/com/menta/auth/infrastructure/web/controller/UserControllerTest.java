package com.menta.auth.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.menta.auth.application.dto.RegisterUserCommand;
import com.menta.auth.application.dto.UserResult;
import com.menta.auth.application.port.in.RegisterUserUseCase;
import com.menta.auth.domain.exception.DuplicateRegistrationException;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.UserStatus;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * UserController unit tests at the web boundary (MockMvc standalone setup).
 *
 * <p>Both registration URLs are a temporary compatibility pair and must use the
 * same input port rather than maintaining separate registration flows.
 */
class UserControllerTest {

    private MockMvc mockMvc;
    private RegisterUserUseCase registerUserUseCase;

    private static final String LEGACY_REGISTER_URL = "/api/v1/users/register";
    private static final String CANONICAL_REGISTER_URL = "/api/v1/auth/register";

    @BeforeEach
    void setUp() {
        this.registerUserUseCase = mock(RegisterUserUseCase.class);
        UserController controller = new UserController(
            registerUserUseCase, new ClientFingerprint("172.16.0.0/12")
        );
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ActivationExceptionHandler())
            .build();
    }

    private static String validBody() {
        return body("user@example.com", "SecurePass123!", "Ada", "Lovelace", "+5491100000000", "1990-05-17");
    }

    private static String body(
        String email, String password, String firstName, String lastName, String phone, String birthDate
    ) {
        StringBuilder json = new StringBuilder("{\"role\":\"STUDENT\"");
        appendString(json, "email", email);
        appendString(json, "password", password);
        appendString(json, "firstName", firstName);
        appendString(json, "lastName", lastName);
        appendString(json, "phone", phone);
        appendString(json, "birthDate", birthDate);
        return json.append('}').toString();
    }

    private static void appendString(StringBuilder json, String name, String value) {
        if (value != null) {
            json.append(",\"").append(name).append("\":\"").append(value).append('"');
        }
    }

    @Test
    void register_maps_the_profile_fields_into_the_command() throws Exception {
        mockMvc.perform(post(CANONICAL_REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
            .andExpect(status().isAccepted());

        ArgumentCaptor<RegisterUserCommand> command = ArgumentCaptor.forClass(RegisterUserCommand.class);
        verify(registerUserUseCase).register(command.capture());
        assertThat(command.getValue().firstName()).isEqualTo("Ada");
        assertThat(command.getValue().lastName()).isEqualTo("Lovelace");
        assertThat(command.getValue().phone()).isEqualTo("+5491100000000");
        assertThat(command.getValue().birthDate()).isEqualTo(LocalDate.of(1990, 5, 17));
    }

    @Test
    void register_accepts_a_request_without_birth_date() throws Exception {
        mockMvc.perform(post(CANONICAL_REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("user@example.com", "SecurePass123!", "Ada", "Lovelace", "+5491100000000", null)))
            .andExpect(status().isAccepted());

        ArgumentCaptor<RegisterUserCommand> command = ArgumentCaptor.forClass(RegisterUserCommand.class);
        verify(registerUserUseCase).register(command.capture());
        assertThat(command.getValue().birthDate()).isNull();
    }

    @Test
    void register_rejects_missing_personal_data_with_a_bad_request() throws Exception {
        mockMvc.perform(post(CANONICAL_REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"user@example.com\",\"password\":\"SecurePass123!\",\"role\":\"STUDENT\"}"))
            .andExpect(status().isBadRequest());

        verify(registerUserUseCase, never()).register(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"5491100000000", "+54 9 11 0000 0000", "+0123456789", "+12345", "+1234567890123456"})
    void register_rejects_a_phone_that_is_not_e164_with_a_bad_request(String phone) throws Exception {
        mockMvc.perform(post(CANONICAL_REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("user@example.com", "SecurePass123!", "Ada", "Lovelace", phone, null)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("INVALID_REQUEST")))
            .andExpect(content().string(not(containsString(phone))));

        verify(registerUserUseCase, never()).register(any());
    }

    @Test
    void register_rejects_a_future_birth_date_with_a_bad_request() throws Exception {
        String tomorrow = LocalDate.now().plusDays(1).toString();

        mockMvc.perform(post(CANONICAL_REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("user@example.com", "SecurePass123!", "Ada", "Lovelace", "+5491100000000", tomorrow)))
            .andExpect(status().isBadRequest());

        verify(registerUserUseCase, never()).register(any());
    }

    @Test
    void register_rejects_a_malformed_birth_date_with_a_bad_request() throws Exception {
        mockMvc.perform(post(CANONICAL_REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("user@example.com", "SecurePass123!", "Ada", "Lovelace", "+5491100000000", "17/05/1990")))
            .andExpect(status().isBadRequest());

        verify(registerUserUseCase, never()).register(any());
    }

    @Test
    void register_rejects_blank_and_over_long_names_with_a_bad_request() throws Exception {
        mockMvc.perform(post(CANONICAL_REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("user@example.com", "SecurePass123!", "  ", "Lovelace", "+5491100000000", null)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post(CANONICAL_REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("user@example.com", "SecurePass123!", "Ada", "a".repeat(101), "+5491100000000", null)))
            .andExpect(status().isBadRequest());

        verify(registerUserUseCase, never()).register(any());
    }

    @Test
    void canonical_and_legacy_registration_routes_delegate_to_the_same_input_port() throws Exception {
        String body = validBody();

        when(registerUserUseCase.register(any())).thenReturn(new UserResult(
            "id", "user@example.com", Role.STUDENT, UserStatus.PENDING_ACTIVATION, java.time.LocalDateTime.MIN
        ));

        for (String url : new String[] {CANONICAL_REGISTER_URL, LEGACY_REGISTER_URL}) {
            mockMvc.perform(post(url)
                    .with(request -> {
                        request.setRemoteAddr("203.0.113.11");
                        return request;
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isAccepted());
        }

        verify(registerUserUseCase, org.mockito.Mockito.times(2)).register(any());
    }

    @Test
    void duplicate_registration_has_the_same_uniform_acknowledgement_as_a_fresh_registration() throws Exception {
        when(registerUserUseCase.register(any())).thenThrow(new DuplicateRegistrationException());

        mockMvc.perform(post(CANONICAL_REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
            .andExpect(status().isAccepted())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
    }

    @Test
    void register_maps_bean_validation_failures_to_a_generic_rfc9457_problem_without_leaking_field_text()
        throws Exception {
        mockMvc.perform(post(CANONICAL_REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("not-an-email", "", "Ada", "Lovelace", "+5491100000000", null)))
            .andExpect(status().isBadRequest())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .jsonPath("$.code", org.hamcrest.Matchers.is("INVALID_REQUEST")))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .content().string(org.hamcrest.Matchers.not(
                    org.hamcrest.Matchers.containsString("Invalid email format")
                )));
    }

    @Test
    void semantic_registration_rejection_still_returns_the_uniform_acknowledgement() throws Exception {
        when(registerUserUseCase.register(any())).thenThrow(new IllegalArgumentException("email must not be exposed"));

        mockMvc.perform(post(CANONICAL_REGISTER_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBody()))
            .andExpect(status().isAccepted())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
    }
}
