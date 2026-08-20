package com.menta.bff.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

@DisplayName("HomeController")
class HomeControllerTest {

    private final HomeController controller = new HomeController();

    @Test
    @DisplayName("returns the index view with the app name")
    void returns_the_index_view_with_the_app_name() {
        Model model = new ExtendedModelMap();

        String view = controller.home(model);

        assertThat(view).isEqualTo("index");
        assertThat(model.getAttribute("appName")).isEqualTo("Menta Dance");
    }

    @Test
    @DisplayName("returns the health view")
    void returns_the_health_view() {
        assertThat(controller.health()).isEqualTo("health");
    }
}
