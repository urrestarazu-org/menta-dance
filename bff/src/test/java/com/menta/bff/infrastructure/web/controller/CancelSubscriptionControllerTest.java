package com.menta.bff.infrastructure.web.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.menta.bff.application.dto.DashboardView;
import com.menta.bff.application.usecase.CancelSubscriptionResult;
import com.menta.bff.application.usecase.CancelSubscriptionResult.Outcome;
import com.menta.bff.application.usecase.CancelSubscriptionUseCase;
import com.menta.bff.application.usecase.GetDashboardViewUseCase;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

/**
 * Unit tests of {@link CancelSubscriptionController} (#363): who may reach the routes and the CSRF check are
 * decided by the security chain, which the integration test covers.
 */
class CancelSubscriptionControllerTest {

    private static final String PATH = "/suscripciones/cancelar";
    private static final String TOKEN = "access-token-abc";

    private final GetDashboardViewUseCase dashboard = mock(GetDashboardViewUseCase.class);
    private final CancelSubscriptionUseCase cancel = mock(CancelSubscriptionUseCase.class);

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new CancelSubscriptionController(dashboard, cancel))
            .setViewResolvers(new InternalResourceViewResolver("/templates/", ".html"))
            .build();

    @Test
    @DisplayName("the confirmation names the plan and the date the access lasts until, and is never cached")
    void confirmationWithDate() throws Exception {
        when(dashboard.execute(TOKEN)).thenReturn(new DashboardView.Active("Plan Mensual", "31/12/2026", null, false));

        mockMvc.perform(get(PATH).requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("cancel-subscription"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(model().attribute("planName", "Plan Mensual"))
                .andExpect(model().attribute("endDateLabel", "31/12/2026"));
    }

    @Test
    @DisplayName("when the subscription cannot be read the confirmation still renders, without a date")
    void confirmationWithoutDate() throws Exception {
        when(dashboard.execute(TOKEN)).thenReturn(new DashboardView.Unavailable());

        mockMvc.perform(get(PATH).requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, TOKEN))
                .andExpect(status().isOk())
                .andExpect(view().name("cancel-subscription"))
                .andExpect(model().attribute("planName", (Object) null))
                .andExpect(model().attribute("endDateLabel", (Object) null));
    }

    @Test
    @DisplayName("a request without a token renders the confirmation without a date and reads nothing")
    void confirmationWithoutToken() throws Exception {
        mockMvc.perform(get(PATH))
                .andExpect(status().isOk())
                .andExpect(view().name("cancel-subscription"))
                .andExpect(model().attribute("endDateLabel", (Object) null));
        verifyNoInteractions(dashboard);
    }

    @ParameterizedTest(name = "with {0} there is nothing to cancel, so the student goes back to the dashboard")
    @MethodSource("notCancellable")
    void nothingToConfirm(DashboardView subscription) throws Exception {
        when(dashboard.execute(TOKEN)).thenReturn(subscription);

        mockMvc.perform(get(PATH).requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    static Stream<Arguments> notCancellable() {
        return Stream.of(
                Arguments.of(new DashboardView.None()),
                Arguments.of(new DashboardView.Pending("Plan Mensual")),
                Arguments.of(new DashboardView.Expired("Plan Mensual", "31/10/2026")),
                Arguments.of(new DashboardView.Cancelled("Plan Mensual", "31/12/2026")));
    }

    static Stream<Arguments> outcomes() {
        return Stream.of(
                Arguments.of(new CancelSubscriptionResult(Outcome.CANCELLED, "31/12/2026"), "success",
                        "Cancelamos tu suscripción: no se va a renovar. Mantenés el acceso hasta el 31/12/2026."),
                Arguments.of(CancelSubscriptionResult.of(Outcome.NOTHING_TO_CANCEL), "info",
                        "No tenés una suscripción activa para cancelar."),
                Arguments.of(CancelSubscriptionResult.of(Outcome.CONFLICT), "error",
                        "Tu suscripción cambió mientras la cancelábamos. Intentá de nuevo."),
                Arguments.of(CancelSubscriptionResult.of(Outcome.UNAVAILABLE), "error",
                        "No pudimos confirmar la cancelación de tu suscripción. "
                                + "Revisá su estado en el panel o probá de nuevo en unos minutos."));
    }

    @ParameterizedTest(name = "{0} redirects to the dashboard with a {1} notice")
    @MethodSource("outcomes")
    void postRedirectsWithNotice(CancelSubscriptionResult result, String kind, String message) throws Exception {
        when(cancel.execute(TOKEN)).thenReturn(result);

        mockMvc.perform(post(PATH).requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, TOKEN))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(flash().attribute("subscriptionNoticeKind", kind))
                .andExpect(flash().attribute("subscriptionNotice", message));
        verify(cancel).execute(TOKEN);
    }
}
