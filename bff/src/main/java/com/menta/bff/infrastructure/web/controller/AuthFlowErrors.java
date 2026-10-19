package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.port.out.AuthApiClient;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;

/**
 * Turns Auth API failures of the public onboarding flows into a Spanish message
 * and a matching HTTP status, never exposing API internals (bodies, codes, hosts).
 *
 * <p>The message goes into the {@code formError} model attribute that every
 * onboarding template renders.
 * </p>
 */
final class AuthFlowErrors {

    static final String RATE_LIMITED = "Hiciste demasiados intentos. Esperá unos minutos e intentá de nuevo.";
    private static final long SECONDS_PER_MINUTE = 60;
    static final String INVALID_REQUEST = "No pudimos procesar los datos ingresados. Revisalos e intentá de nuevo.";
    static final String UNAVAILABLE =
            "No pudimos completar la operación en este momento. Intentá nuevamente en unos minutos.";

    private AuthFlowErrors() {
    }

    /**
     * Records the failure in the model and on the response.
     *
     * @param failure  an exception thrown by the Auth API port
     * @param model    the view model receiving {@code formError}
     * @param response the response whose status is set
     */
    static void apply(RuntimeException failure, Model model, HttpServletResponse response) {
        if (failure instanceof AuthApiClient.RateLimitedException) {
            model.addAttribute("formError", rateLimitedMessage(
                    ((AuthApiClient.RateLimitedException) failure).retryAfter()));
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        } else if (failure instanceof AuthApiClient.InvalidRequestException) {
            model.addAttribute("formError", INVALID_REQUEST);
            response.setStatus(HttpStatus.BAD_REQUEST.value());
        } else {
            model.addAttribute("formError", UNAVAILABLE);
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
    }

    private static String rateLimitedMessage(Duration retryAfter) {
        if (retryAfter == null || retryAfter.isZero() || retryAfter.isNegative()) {
            return RATE_LIMITED;
        }
        long seconds = retryAfter.toSeconds();
        String wait;
        if (seconds < SECONDS_PER_MINUTE) {
            wait = seconds + (seconds == 1 ? " segundo" : " segundos");
        } else {
            long minutes = (seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE;
            wait = minutes + (minutes == 1 ? " minuto" : " minutos");
        }
        return "Hiciste demasiados intentos. Esperá " + wait + " e intentá de nuevo.";
    }
}
