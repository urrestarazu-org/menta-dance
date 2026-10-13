package com.menta.bff.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.bff.infrastructure.security.PendingActivationException;
import com.menta.bff.infrastructure.security.TooManyLoginAttemptsException;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.WebAttributes;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

@DisplayName("LoginViewController")
class LoginViewControllerTest {

    private final LoginViewController controller = new LoginViewController();

    @Test
    @DisplayName("returns the login view without extra attributes when no params are present")
    void returns_the_login_view_without_extra_attributes() {
        Model model = new ExtendedModelMap();

        String view = controller.loginForm(null, null, null, new MockHttpServletRequest(), model);

        assertThat(view).isEqualTo("login");
        assertThat(model.containsAttribute("error")).isFalse();
        assertThat(model.containsAttribute("logout")).isFalse();
    }

    @Test
    @DisplayName("adds an error message when the error param is present")
    void adds_an_error_message_when_the_error_param_is_present() {
        Model model = new ExtendedModelMap();

        String view = controller.loginForm("true", null, null, new MockHttpServletRequest(), model);

        assertThat(view).isEqualTo("login");
        assertThat(model.getAttribute("error")).isEqualTo("Email o contraseña inválidos");
    }

    @Test
    @DisplayName("adds a logout message when the logout param is present")
    void adds_a_logout_message_when_the_logout_param_is_present() {
        Model model = new ExtendedModelMap();

        String view = controller.loginForm(null, "true", null, new MockHttpServletRequest(), model);

        assertThat(view).isEqualTo("login");
        assertThat(model.getAttribute("logout")).isEqualTo("Sesión cerrada exitosamente");
    }

    @Test
    @DisplayName("adds a neutral notice after a password reset")
    void adds_a_notice_after_a_password_reset() {
        Model model = new ExtendedModelMap();

        String view = controller.loginForm(null, null, "", new MockHttpServletRequest(), model);

        assertThat(view).isEqualTo("login");
        assertThat(model.getAttribute("resetNotice"))
                .isEqualTo("Tu contraseña fue actualizada. Iniciá sesión con la nueva.");
    }

    private Model failedLoginWith(AuthenticationException failure) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(WebAttributes.AUTHENTICATION_EXCEPTION, failure);
        Model model = new ExtendedModelMap();

        controller.loginForm("true", null, null, request, model);

        assertThat(request.getSession().getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION))
                .as("the failure is shown once").isNull();
        return model;
    }

    @Test
    @DisplayName("keeps the generic message for bad credentials")
    void keeps_the_generic_message_for_bad_credentials() {
        Model model = failedLoginWith(new BadCredentialsException("x"));

        assertThat(model.getAttribute("error")).isEqualTo("Email o contraseña inválidos");
        assertThat(model.containsAttribute("resendActivationLink")).isFalse();
    }

    @Test
    @DisplayName("tells a locked account apart")
    void tells_a_locked_account_apart() {
        Model model = failedLoginWith(new LockedException("x"));

        assertThat(model.getAttribute("error"))
                .isEqualTo("Tu cuenta está bloqueada. Comunicate con la academia para recuperar el acceso.");
    }

    @Test
    @DisplayName("tells an account awaiting activation apart and offers the resend page")
    void tells_a_pending_activation_apart() {
        Model model = failedLoginWith(new PendingActivationException("x"));

        assertThat(model.getAttribute("error")).isEqualTo(
                "Tu cuenta todavía no está activada. Revisá tu correo o pedí un nuevo enlace de activación.");
        assertThat(model.getAttribute("resendActivationLink")).isEqualTo(true);
    }

    @Test
    @DisplayName("tells the user to wait, rounding the Retry-After up")
    void tells_the_user_to_wait() {
        assertThat(failedLoginWith(new TooManyLoginAttemptsException("x", Duration.ofSeconds(45)))
                .getAttribute("error"))
                .isEqualTo("Demasiados intentos de inicio de sesión. Probá de nuevo en 45 segundos.");
        assertThat(failedLoginWith(new TooManyLoginAttemptsException("x", Duration.ofSeconds(1)))
                .getAttribute("error"))
                .isEqualTo("Demasiados intentos de inicio de sesión. Probá de nuevo en 1 segundo.");
        assertThat(failedLoginWith(new TooManyLoginAttemptsException("x", Duration.ofSeconds(60)))
                .getAttribute("error"))
                .isEqualTo("Demasiados intentos de inicio de sesión. Probá de nuevo en 1 minuto.");
        assertThat(failedLoginWith(new TooManyLoginAttemptsException("x", Duration.ofSeconds(61)))
                .getAttribute("error"))
                .isEqualTo("Demasiados intentos de inicio de sesión. Probá de nuevo en 2 minutos.");
    }

    @Test
    @DisplayName("uses a generic wait when the API announced none")
    void uses_a_generic_wait_when_none_was_announced() {
        assertThat(failedLoginWith(new TooManyLoginAttemptsException("x", null)).getAttribute("error"))
                .isEqualTo("Demasiados intentos de inicio de sesión. Probá de nuevo en unos minutos.");
        assertThat(failedLoginWith(new TooManyLoginAttemptsException("x", Duration.ZERO)).getAttribute("error"))
                .isEqualTo("Demasiados intentos de inicio de sesión. Probá de nuevo en unos minutos.");
    }

    @Test
    @DisplayName("says the service is unavailable instead of blaming the credentials")
    void says_the_service_is_unavailable() {
        Model model = failedLoginWith(new InternalAuthenticationServiceException("x"));

        assertThat(model.getAttribute("error"))
                .isEqualTo("El servicio no está disponible por el momento. Probá de nuevo en unos minutos.");
    }

    @Test
    @DisplayName("ignores a stale failure when the error param is absent")
    void ignores_a_failure_without_the_error_param() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(WebAttributes.AUTHENTICATION_EXCEPTION, new LockedException("x"));
        Model model = new ExtendedModelMap();

        controller.loginForm(null, null, null, request, model);

        assertThat(model.containsAttribute("error")).isFalse();
    }
}
