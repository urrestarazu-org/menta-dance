package com.menta.bff.infrastructure.web.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.menta.bff.application.usecase.ClassesView;
import com.menta.bff.application.usecase.GetClassesViewUseCase;
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

/** Standalone MockMvc tests for {@link ClassesController}; rendering is covered by the integration tier. */
@ExtendWith(MockitoExtension.class)
@DisplayName("ClassesController")
class ClassesControllerTest {

    @Mock
    private GetClassesViewUseCase getClassesViewUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        View noOpView = (model, request, response) -> {};
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ClassesController(getClassesViewUseCase))
                .setSingleView(noOpView)
                .build();
    }

    @Test
    @DisplayName("renders the classes view with the list")
    void rendersList() throws Exception {
        ClassesView classes = new ClassesView(List.of(), false);
        when(getClassesViewUseCase.execute()).thenReturn(classes);

        mockMvc.perform(get("/classes"))
                .andExpect(status().isOk())
                .andExpect(view().name("classes"))
                .andExpect(model().attribute("classes", classes));
    }

    @Test
    @DisplayName("answers 503 with the degraded list when the catalog is down")
    void listAnswers503WhenUnavailable() throws Exception {
        when(getClassesViewUseCase.execute()).thenReturn(ClassesView.unavailableView());

        mockMvc.perform(get("/classes")).andExpect(status().isServiceUnavailable());
    }
}
