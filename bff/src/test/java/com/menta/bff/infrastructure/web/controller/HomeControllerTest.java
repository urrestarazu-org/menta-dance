package com.menta.bff.infrastructure.web.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.menta.bff.application.usecase.CatalogView;
import com.menta.bff.application.usecase.GetCatalogViewUseCase;
import com.menta.bff.application.usecase.GetMembershipLandingViewUseCase;
import com.menta.bff.application.usecase.MembershipLandingView;
import com.menta.bff.domain.model.CourseModality;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.View;

/**
 * Unit tests for {@link HomeController}, standalone MockMvc. Rendered output
 * and anonymous reachability are covered by the integration tier.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HomeController")
class HomeControllerTest {

    @Mock
    private GetCatalogViewUseCase getCatalogViewUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        View noOpView = (model, request, response) -> {};
        mockMvc = MockMvcBuilders.standaloneSetup(new HomeController(getCatalogViewUseCase))
                .setSingleView(noOpView)
                .build();
    }

    @Test
    @DisplayName("renders the index view with the catalog for the unfiltered home page")
    void rendersCatalogWithoutFilter() throws Exception {
        CatalogView catalog = new CatalogView(List.of(), null, false);
        when(getCatalogViewUseCase.execute(null)).thenReturn(catalog);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attribute("catalog", catalog));
    }

    @Test
    @DisplayName("forwards the modality query parameter to the use case")
    void forwardsModalityParameter() throws Exception {
        CatalogView catalog = new CatalogView(List.of(), CourseModality.PHYSICAL, false);
        when(getCatalogViewUseCase.execute("physical")).thenReturn(catalog);

        mockMvc.perform(get("/").param("modality", "physical"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("catalog", catalog));

        verify(getCatalogViewUseCase).execute("physical");
    }

    @Test
    @DisplayName("serves the membership landing instead of the catalog while the landing use case exists")
    void servesTheMembershipLandingWhenEnabled() throws Exception {
        GetMembershipLandingViewUseCase landingUseCase =
                org.mockito.Mockito.mock(GetMembershipLandingViewUseCase.class);
        MembershipLandingView landing = new MembershipLandingView(List.of(), false, "2026-11-01T15:30:00Z");
        when(landingUseCase.execute()).thenReturn(landing);
        View noOpView = (model, request, response) -> {};
        MockMvc landingMvc = MockMvcBuilders
                .standaloneSetup(new HomeController(getCatalogViewUseCase, Optional.of(landingUseCase)))
                .setSingleView(noOpView)
                .build();

        landingMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("membership-landing"))
                .andExpect(model().attribute("priceRows", landing.priceRows()))
                .andExpect(model().attribute("salesClosed", false))
                .andExpect(model().attribute("serverNow", "2026-11-01T15:30:00Z"));

        verifyNoInteractions(getCatalogViewUseCase);
    }

    @Test
    @DisplayName("keeps serving the catalog when there is no landing use case")
    void servesTheCatalogWhenThereIsNoLanding() throws Exception {
        CatalogView catalog = new CatalogView(List.of(), null, false);
        when(getCatalogViewUseCase.execute(null)).thenReturn(catalog);
        View noOpView = (model, request, response) -> {};
        MockMvc plainMvc = MockMvcBuilders
                .standaloneSetup(new HomeController(getCatalogViewUseCase, Optional.empty()))
                .setSingleView(noOpView)
                .build();

        plainMvc.perform(get("/"))
                .andExpect(view().name("index"))
                .andExpect(model().attribute("catalog", catalog));
    }
}
