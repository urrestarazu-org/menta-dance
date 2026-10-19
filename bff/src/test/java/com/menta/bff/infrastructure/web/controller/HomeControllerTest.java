package com.menta.bff.infrastructure.web.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.menta.bff.application.usecase.CatalogView;
import com.menta.bff.application.usecase.GetCatalogViewUseCase;
import com.menta.bff.domain.model.CourseModality;
import java.util.List;
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
}
