package com.menta.bff.infrastructure.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.usecase.AccountActivationUseCase;
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
 * Unit tests for {@link ActivationController}, standalone MockMvc. Rendered
 * output, CSRF and anonymous reachability live in the integration tier.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ActivationController")
class ActivationControllerTest {

    private static final String TOKEN = "activation-token-abc";

    @Mock
    private AccountActivationUseCase accountActivationUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        View noOpView = (model, request, response) -> {};
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ActivationController(accountActivationUseCase))
                .setSingleView(noOpView)
                .build();
    }

    static Stream<Arguments> retryableFailures() {
        return Stream.of(
                Arguments.of(new AuthApiClient.RateLimitedException("limit", null), 429),
                Arguments.of(new AuthApiClient.ServiceUnavailableException("down"), 503));
    }

    @Test
    @DisplayName("GET /activar/{token} with a valid token renders the button page and forbids caching and Referer")
    void validTokenRendersTheButtonPage() throws Exception {
        when(accountActivationUseCase.isTokenValid(TOKEN)).thenReturn(true);

        mockMvc.perform(get("/activar/" + TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("activate"))
                .andExpect(model().attribute("token", TOKEN))
                .andExpect(model().attributeDoesNotExist("invalidToken", "formError"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    @DisplayName("GET /activar/{token} with an invalid token renders the invalid state without echoing the token")
    void invalidTokenRendersTheInvalidState() throws Exception {
        when(accountActivationUseCase.isTokenValid(TOKEN)).thenReturn(false);

        mockMvc.perform(get("/activar/" + TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("activate"))
                .andExpect(model().attribute("invalidToken", true))
                .andExpect(model().attributeDoesNotExist("token"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @ParameterizedTest
    @MethodSource("retryableFailures")
    @DisplayName("GET /activar/{token} keeps the button available when the API cannot answer")
    void validationFailureKeepsTheButton(RuntimeException failure, int expectedStatus) throws Exception {
        when(accountActivationUseCase.isTokenValid(TOKEN)).thenThrow(failure);

        mockMvc.perform(get("/activar/" + TOKEN))
                .andExpect(status().is(expectedStatus))
                .andExpect(view().name("activate"))
                .andExpect(model().attribute("token", TOKEN))
                .andExpect(model().attributeExists("formError"))
                .andExpect(model().attributeDoesNotExist("invalidToken"));
    }

    @Test
    @DisplayName("POST /activar activates the account and redirects without carrying anything in the URL")
    void successRedirectsToTheResultScreen() throws Exception {
        mockMvc.perform(post("/activar").param("token", TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("redirect:/cuenta-activada"))
                .andExpect(model().size(0));

        verify(accountActivationUseCase).activate(TOKEN);
    }

    @Test
    @DisplayName("POST /activar with an invalid token renders the invalid state")
    void invalidTokenOnSubmitRendersTheInvalidState() throws Exception {
        doThrow(new AuthApiClient.InvalidActivationTokenException("no")).when(accountActivationUseCase)
                .activate(any());

        mockMvc.perform(post("/activar").param("token", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("activate"))
                .andExpect(model().attribute("invalidToken", true))
                .andExpect(model().attributeDoesNotExist("token"));
    }

    @ParameterizedTest
    @MethodSource("retryableFailures")
    @DisplayName("POST /activar re-renders the button page with a Spanish error so the user can retry")
    void submitFailureReRendersTheButtonPage(RuntimeException failure, int expectedStatus) throws Exception {
        doThrow(failure).when(accountActivationUseCase).activate(TOKEN);

        mockMvc.perform(post("/activar").param("token", TOKEN))
                .andExpect(status().is(expectedStatus))
                .andExpect(view().name("activate"))
                .andExpect(model().attribute("token", TOKEN))
                .andExpect(model().attributeExists("formError"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    @DisplayName("POST /activar without a token is handled as an invalid link")
    void missingTokenIsAnInvalidLink() throws Exception {
        doThrow(new AuthApiClient.InvalidActivationTokenException("no")).when(accountActivationUseCase)
                .activate("");

        mockMvc.perform(post("/activar"))
                .andExpect(status().isBadRequest())
                .andExpect(model().attribute("invalidToken", true));
    }

    @Test
    @DisplayName("GET /cuenta-activada renders the success screen")
    void rendersTheSuccessScreen() throws Exception {
        mockMvc.perform(get("/cuenta-activada"))
                .andExpect(status().isOk())
                .andExpect(view().name("account-activated"));
    }
}
