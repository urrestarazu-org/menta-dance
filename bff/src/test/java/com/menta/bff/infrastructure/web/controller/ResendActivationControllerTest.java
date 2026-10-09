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

import com.menta.bff.application.dto.ResendActivationCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.usecase.ResendActivationUseCase;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("ResendActivationController")
class ResendActivationControllerTest {

    @Mock
    private ResendActivationUseCase resendActivationUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        View noOpView = (model, request, response) -> {};
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ResendActivationController(
                        resendActivationUseCase, new ClientOriginResolver("")))
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
    @DisplayName("GET /reenviar-activacion renders an empty form")
    void rendersTheForm() throws Exception {
        mockMvc.perform(get("/reenviar-activacion"))
                .andExpect(status().isOk())
                .andExpect(view().name("resend-activation"))
                .andExpect(model().attributeExists("form"));
    }

    @Test
    @DisplayName("a blank or malformed email re-renders the form and skips the API")
    void invalidEmailNeverCallsTheApi() throws Exception {
        mockMvc.perform(post("/reenviar-activacion").param("email", "no-es-un-email"))
                .andExpect(status().isOk())
                .andExpect(view().name("resend-activation"))
                .andExpect(model().attributeHasFieldErrors("form", "email"));

        verifyNoInteractions(resendActivationUseCase);
    }

    @Test
    @DisplayName("a valid email is resent and redirects to the pending screen without carrying the email")
    void validEmailRedirectsToPending() throws Exception {
        mockMvc.perform(post("/reenviar-activacion").param("email", " user@example.com "))
                .andExpect(view().name("redirect:/registro/pendiente"));

        verify(resendActivationUseCase).execute(new ResendActivationCommand("user@example.com", "127.0.0.1"));
    }

    @ParameterizedTest
    @MethodSource("apiFailures")
    @DisplayName("API failures re-render the form with a Spanish error and the matching status")
    void apiFailuresReRenderTheForm(RuntimeException failure, int expectedStatus) throws Exception {
        doThrow(failure).when(resendActivationUseCase).execute(any());

        mockMvc.perform(post("/reenviar-activacion").param("email", "user@example.com"))
                .andExpect(status().is(expectedStatus))
                .andExpect(view().name("resend-activation"))
                .andExpect(model().attributeExists("formError"));
    }
}
