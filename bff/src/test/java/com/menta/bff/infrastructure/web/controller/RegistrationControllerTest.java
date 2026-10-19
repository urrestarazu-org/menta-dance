package com.menta.bff.infrastructure.web.controller;

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
                .andExpect(model().attributeHasFieldErrors("form", "email", "password"));

        verifyNoInteractions(registerAccountUseCase);
    }

    @Test
    @DisplayName("a valid submission registers a STUDENT and redirects to the pending screen")
    void validSubmissionRegistersAStudent() throws Exception {
        mockMvc.perform(post("/registro").param("email", " ana@example.com ").param("password", "Secreta123"))
                .andExpect(view().name("redirect:/registro/pendiente"));

        verify(registerAccountUseCase)
                .execute(RegisterCommand.student("ana@example.com", "Secreta123", "127.0.0.1"));
    }

    @ParameterizedTest
    @MethodSource("apiFailures")
    @DisplayName("API failures re-render the form with a Spanish error and the matching status")
    void apiFailuresReRenderTheForm(RuntimeException failure, int expectedStatus) throws Exception {
        doThrow(failure).when(registerAccountUseCase).execute(any());

        mockMvc.perform(post("/registro").param("email", "ana@example.com").param("password", "Secreta123"))
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
