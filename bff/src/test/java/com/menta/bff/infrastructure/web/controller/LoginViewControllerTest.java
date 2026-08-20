package com.menta.bff.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

@DisplayName("LoginViewController")
class LoginViewControllerTest {

    private final LoginViewController controller = new LoginViewController();

    @Test
    @DisplayName("returns the login view without extra attributes when no params are present")
    void returns_the_login_view_without_extra_attributes() {
        Model model = new ExtendedModelMap();

        String view = controller.loginForm(null, null, model);

        assertThat(view).isEqualTo("login");
        assertThat(model.containsAttribute("error")).isFalse();
        assertThat(model.containsAttribute("logout")).isFalse();
    }

    @Test
    @DisplayName("adds an error message when the error param is present")
    void adds_an_error_message_when_the_error_param_is_present() {
        Model model = new ExtendedModelMap();

        String view = controller.loginForm("true", null, model);

        assertThat(view).isEqualTo("login");
        assertThat(model.getAttribute("error")).isEqualTo("Email o contraseña inválidos");
    }

    @Test
    @DisplayName("adds a logout message when the logout param is present")
    void adds_a_logout_message_when_the_logout_param_is_present() {
        Model model = new ExtendedModelMap();

        String view = controller.loginForm(null, "true", model);

        assertThat(view).isEqualTo("login");
        assertThat(model.getAttribute("logout")).isEqualTo("Sesión cerrada exitosamente");
    }
}
