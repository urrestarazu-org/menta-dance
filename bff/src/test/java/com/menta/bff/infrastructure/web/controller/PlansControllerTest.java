package com.menta.bff.infrastructure.web.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.menta.bff.application.dto.PlanSummary;
import com.menta.bff.application.dto.PlansOffer;
import com.menta.bff.application.dto.PlansView;
import com.menta.bff.application.dto.PlansViewer;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.usecase.GetPlansViewUseCase;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.View;

/**
 * Unit tests for {@link PlansController}, standalone MockMvc, no security
 * context — anonymous reachability is proven by the integration tier (PR 5),
 * not here.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlansController")
class PlansControllerTest {

    @Mock
    private GetPlansViewUseCase getPlansViewUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // The view name "plans" collides with the request path "/plans": the
        // default InternalResourceView forward would loop back to this same
        // handler ("Circular view path"). A no-op View sidesteps rendering
        // entirely — this test only asserts the model attribute and the
        // resolved view name, never actual template output.
        View noOpView = (model, request, response) -> {};
        mockMvc = MockMvcBuilders.standaloneSetup(new PlansController(getPlansViewUseCase))
                .setSingleView(noOpView)
                .build();
    }

    @Test
    @DisplayName("renders the plans view with the model attribute equal to the use case's returned list")
    void shouldRenderPlansViewWithModelAttribute() throws Exception {
        List<PlanSummary> plans = List.of(
                new PlanSummary("plan-1", "Mensual", "Acceso mensual", BigDecimal.valueOf(1000), "ARS", 30, false),
                new PlanSummary("plan-2", "Anual", "Acceso anual", BigDecimal.valueOf(10000), "ARS", 365, true));
        when(getPlansViewUseCase.execute(PlansViewer.ANONYMOUS, null))
                .thenReturn(new PlansView(plans, null));

        mockMvc.perform(get("/plans"))
                .andExpect(status().isOk())
                .andExpect(view().name("plans"))
                .andExpect(model().attribute("plans", plans));
    }

    @Test
    @DisplayName("propagates NotFoundException, which resolves to HTTP 404 via @ResponseStatus")
    void shouldResolveNotFoundExceptionTo404() throws Exception {
        when(getPlansViewUseCase.execute(PlansViewer.ANONYMOUS, null))
                .thenThrow(new BillingApiClient.NotFoundException("no plans"));

        mockMvc.perform(get("/plans"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("propagates ServiceUnavailableException, which resolves to HTTP 503 via @ResponseStatus")
    void shouldResolveServiceUnavailableExceptionTo503() throws Exception {
        when(getPlansViewUseCase.execute(PlansViewer.ANONYMOUS, null))
                .thenThrow(new BillingApiClient.ServiceUnavailableException("unavailable"));

        mockMvc.perform(get("/plans"))
                .andExpect(status().isServiceUnavailable());
    }

    private static final String TOKEN = "access-token-abc";
    private static final PlansOffer NO_OFFER = new PlansOffer(
            PlansOffer.SubscribeAction.NONE, PlansOffer.OfferNotice.NONE, null, null, Map.of());

    private static UsernamePasswordAuthenticationToken user(String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                "user@example.com", "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    @Test
    @DisplayName("a student with a token is shown the offer, the token is forwarded and the page is no-store")
    void studentIsPersonalizedAndNotCacheable() throws Exception {
        PlansView view = new PlansView(List.of(), NO_OFFER);
        when(getPlansViewUseCase.execute(PlansViewer.STUDENT, TOKEN)).thenReturn(view);

        mockMvc.perform(get("/plans").principal(user("STUDENT"))
                        .requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(model().attribute("offer", NO_OFFER));
    }

    @Test
    @DisplayName("another authenticated role is a non-student viewer and the page is no-store")
    void otherRoleIsNotCacheable() throws Exception {
        when(getPlansViewUseCase.execute(PlansViewer.OTHER, TOKEN)).thenReturn(new PlansView(List.of(), NO_OFFER));

        mockMvc.perform(get("/plans").principal(user("INSTRUCTOR"))
                        .requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    @DisplayName("the anonymous page keeps the shared cache: the controller sends no Cache-Control")
    void anonymousKeepsSharedCache() throws Exception {
        when(getPlansViewUseCase.execute(PlansViewer.ANONYMOUS, null)).thenReturn(new PlansView(List.of(), NO_OFFER));

        mockMvc.perform(get("/plans"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Cache-Control"));
    }

    @ParameterizedTest(name = "a student whose token attribute is {0} fails closed")
    @ValueSource(strings = {"", " "})
    @DisplayName("a student without an access token fails closed with 503 and no use case call")
    void studentWithoutTokenFailsClosed(String blank) throws Exception {
        mockMvc.perform(get("/plans").principal(user("STUDENT")))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/plans").principal(user("STUDENT"))
                        .requestAttr(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE, blank))
                .andExpect(status().isServiceUnavailable());
        verifyNoInteractions(getPlansViewUseCase);
    }
}
