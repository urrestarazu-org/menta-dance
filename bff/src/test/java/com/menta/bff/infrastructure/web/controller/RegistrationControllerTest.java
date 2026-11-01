package com.menta.bff.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.menta.bff.application.dto.RegisterCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.usecase.RegisterAccountUseCase;
import com.menta.bff.infrastructure.security.ClientOriginResolver;
import com.menta.bff.infrastructure.web.dto.RegisterForm;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.View;

/**
 * Unit tests for {@link RegistrationController}, standalone MockMvc. Rendered
 * output, CSRF and anonymous reachability live in the integration tier.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RegistrationController")
class RegistrationControllerTest {

    @Mock
    private RegisterAccountUseCase registerAccountUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        View noOpView = (model, request, response) -> {};
        mockMvc = MockMvcBuilders
                .standaloneSetup(new RegistrationController(registerAccountUseCase, new ClientOriginResolver("")))
                .setSingleView(noOpView)
                .build();
    }

    /** A valid submission with fake personal data; each override replaces the default of that parameter. */
    private static MockHttpServletRequestBuilder registration(String... overrides) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("firstName", "Ana");
        params.put("lastName", "Prueba");
        params.put("email", "ana@example.com");
        params.put("phone", "+54 9 11 0000-0000");
        params.put("password", "Secreta123");
        params.put("passwordConfirmation", "Secreta123");
        for (int i = 0; i < overrides.length; i += 2) {
            params.put(overrides[i], overrides[i + 1]);
        }
        MockHttpServletRequestBuilder request = post("/registro");
        params.forEach(request::param);
        return request;
    }

    static Stream<Arguments> apiFailures() {
        return Stream.of(
                Arguments.of(new AuthApiClient.RateLimitedException("limit", null), 429),
                Arguments.of(new AuthApiClient.InvalidRequestException("bad"), 400),
                Arguments.of(new AuthApiClient.ServiceUnavailableException("down"), 503));
    }

    @Test
    @DisplayName("GET /registro renders an empty form")
    void rendersTheForm() throws Exception {
        mockMvc.perform(get("/registro"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("form"));
    }

    @Test
    @DisplayName("invalid email and weak password re-render the form with field errors and skip the API")
    void invalidInputNeverCallsTheApi() throws Exception {
        mockMvc.perform(post("/registro").param("email", "no-es-un-email").param("password", "corta"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors(
                        "form", "email", "password", "firstName", "lastName", "phone"));

        verifyNoInteractions(registerAccountUseCase);
    }

    @Test
    @DisplayName("a valid submission registers a STUDENT and redirects to the pending screen")
    void validSubmissionRegistersAStudent() throws Exception {
        mockMvc.perform(registration("email", " ana@example.com "))
                .andExpect(view().name("redirect:/registro/pendiente"));

        verify(registerAccountUseCase).execute(RegisterCommand.student(
                "ana@example.com", "Secreta123", "Ana", "Prueba", "+5491100000000", null, "127.0.0.1"));
    }

    @Test
    @DisplayName("names are trimmed, the phone is normalized to E.164 and the birth date is parsed")
    void mapsPersonalDataToTheCommand() throws Exception {
        mockMvc.perform(registration("firstName", "  Ana María ", "lastName", " Prueba ",
                        "phone", " +54 (9) 11 0000.0000 ", "birthDate", "1990-05-17"))
                .andExpect(view().name("redirect:/registro/pendiente"));

        verify(registerAccountUseCase).execute(RegisterCommand.student("ana@example.com", "Secreta123",
                "Ana María", "Prueba", "+5491100000000", LocalDate.of(1990, 5, 17), "127.0.0.1"));
    }

    @Test
    @DisplayName("an empty birth date is sent as no birth date")
    void emptyBirthDateIsOmitted() throws Exception {
        mockMvc.perform(registration("birthDate", ""))
                .andExpect(view().name("redirect:/registro/pendiente"));

        verify(registerAccountUseCase).execute(RegisterCommand.student(
                "ana@example.com", "Secreta123", "Ana", "Prueba", "+5491100000000", null, "127.0.0.1"));
    }

    @Test
    @DisplayName("a bad phone or a future birth date re-render the form with field errors and skip the API")
    void invalidPersonalDataNeverCallsTheApi() throws Exception {
        mockMvc.perform(registration("phone", "11 5555 0000",
                        "birthDate", LocalDate.now().plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("form", "phone", "birthDate"));

        verifyNoInteractions(registerAccountUseCase);
    }

    @Test
    @DisplayName("on error the form keeps what was typed except the passwords")
    void errorKeepsTypedValuesButNeverThePasswords() throws Exception {
        mockMvc.perform(registration("phone", "11 5555 0000", "birthDate", "1990-05-17"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(result -> {
                    RegisterForm form = (RegisterForm) result.getModelAndView().getModel().get("form");
                    assertThat(form.getFirstName()).isEqualTo("Ana");
                    assertThat(form.getLastName()).isEqualTo("Prueba");
                    assertThat(form.getEmail()).isEqualTo("ana@example.com");
                    assertThat(form.getPhone()).isEqualTo("11 5555 0000");
                    assertThat(form.getBirthDate()).isEqualTo("1990-05-17");
                });
    }

    @Test
    @DisplayName("a confirmation that differs from the password re-renders the form and skips the API")
    void mismatchedConfirmationNeverCallsTheApi() throws Exception {
        mockMvc.perform(registration("passwordConfirmation", "Otra12345"))
                .andExpect(status().isOk())
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("form", "passwordConfirmed"));

        verifyNoInteractions(registerAccountUseCase);
    }

    @ParameterizedTest
    @MethodSource("apiFailures")
    @DisplayName("API failures re-render the form with a Spanish error and the matching status")
    void apiFailuresReRenderTheForm(RuntimeException failure, int expectedStatus) throws Exception {
        doThrow(failure).when(registerAccountUseCase).execute(any());

        mockMvc.perform(registration())
                .andExpect(status().is(expectedStatus))
                .andExpect(view().name("register"))
                .andExpect(model().attributeExists("formError"));
    }

    @Test
    @DisplayName("GET /registro/pendiente renders the neutral check-your-email screen")
    void rendersThePendingScreen() throws Exception {
        mockMvc.perform(get("/registro/pendiente"))
                .andExpect(status().isOk())
                .andExpect(view().name("check-email"));
    }
}
