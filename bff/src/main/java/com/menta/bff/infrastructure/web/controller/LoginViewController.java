package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.infrastructure.security.PendingActivationException;
import com.menta.bff.infrastructure.security.TooManyLoginAttemptsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Login view controller.
 *
 * <p>Only handles GET /login to display the login form.
 * POST /login is handled automatically by Spring Security
 * via BffAuthenticationProvider.
 *
 * <p>When the login failed, Spring Security redirects to {@code /login?error} and leaves the
 * failure in the session; this controller turns it into the message shown, once. Anything it does
 * not recognise (including a request that carries only the parameter) is the generic
 * invalid-credentials message, so the page never says more than the failure did.
 */
@Controller
public class LoginViewController {

    private static final String INVALID_CREDENTIALS = "Email o contraseña inválidos";
    private static final String TOO_MANY_ATTEMPTS = "Demasiados intentos de inicio de sesión. Probá de nuevo en ";
    private static final long SECONDS_PER_MINUTE = 60;

    @GetMapping("/login")
    public String loginForm(
        @RequestParam(value = "error", required = false) String error,
        @RequestParam(value = "logout", required = false) String logout,
        @RequestParam(value = "reset", required = false) String reset,
        HttpServletRequest request,
        Model model
    ) {
        if (error != null) {
            describeFailure(takeFailure(request), model);
        }
        if (logout != null) {
            model.addAttribute("logout", "Sesión cerrada exitosamente");
        }
        if (reset != null) {
            model.addAttribute("resetNotice", "Tu contraseña fue actualizada. Iniciá sesión con la nueva.");
        }
        return "login";
    }

    private static AuthenticationException takeFailure(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object failure = session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        session.removeAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        return failure instanceof AuthenticationException exception ? exception : null;
    }

    private static void describeFailure(AuthenticationException failure, Model model) {
        if (failure instanceof LockedException) {
            model.addAttribute("error",
                "Tu cuenta está bloqueada. Comunicate con la academia para recuperar el acceso.");
        } else if (failure instanceof PendingActivationException) {
            model.addAttribute("error",
                "Tu cuenta todavía no está activada. Revisá tu correo o pedí un nuevo enlace de activación.");
            model.addAttribute("resendActivationLink", true);
        } else if (failure instanceof TooManyLoginAttemptsException tooMany) {
            model.addAttribute("error", TOO_MANY_ATTEMPTS + waitText(tooMany.retryAfter()) + ".");
        } else if (failure instanceof InternalAuthenticationServiceException) {
            model.addAttribute("error",
                "El servicio no está disponible por el momento. Probá de nuevo en unos minutos.");
        } else {
            model.addAttribute("error", INVALID_CREDENTIALS);
        }
    }

    /** Seconds below a minute, whole minutes (rounded up) from there on; vague when unknown. */
    private static String waitText(Duration retryAfter) {
        if (retryAfter == null || retryAfter.isZero() || retryAfter.isNegative()) {
            return "unos minutos";
        }
        long seconds = retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0);
        if (seconds < SECONDS_PER_MINUTE) {
            return seconds == 1 ? "1 segundo" : seconds + " segundos";
        }
        long minutes = (seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE;
        return minutes == 1 ? "1 minuto" : minutes + " minutos";
    }
}
