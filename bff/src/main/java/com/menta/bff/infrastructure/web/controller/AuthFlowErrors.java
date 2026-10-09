package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.port.out.AuthApiClient;
import jakarta.servlet.http.HttpServletResponse;
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
            model.addAttribute("formError", RATE_LIMITED);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        } else if (failure instanceof AuthApiClient.InvalidRequestException) {
            model.addAttribute("formError", INVALID_REQUEST);
            response.setStatus(HttpStatus.BAD_REQUEST.value());
        } else {
            model.addAttribute("formError", UNAVAILABLE);
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
    }
}
