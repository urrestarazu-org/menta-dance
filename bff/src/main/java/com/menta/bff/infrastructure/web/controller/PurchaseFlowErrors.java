package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;

/**
 * Turns the failures of the physical purchase flow into a Spanish notice and a matching HTTP
 * status, never exposing API internals (bodies, codes, hosts, tokens). The wording is the flow's
 * own; only the generic "invalid" and "unavailable" sentences are shared with the auth flows.
 */
final class PurchaseFlowErrors {

    static final String NOTICE_VIEW = "purchase-notice";

    private PurchaseFlowErrors() {
    }

    /**
     * Fills the notice model and sets the response status.
     *
     * @param failure   an exception of the purchase ports
     * @param model     the view model receiving {@code title}, {@code message}, {@code linkHref}, {@code linkLabel}
     * @param response  the response whose status is set
     * @param linkHref  where the notice's single link leads
     * @param linkLabel the link's text
     * @return the notice view name
     */
    static String apply(
            RuntimeException failure, Model model, HttpServletResponse response, String linkHref, String linkLabel) {
        HttpStatus status;
        String title;
        String message;
        if (failure instanceof PhysicalPurchaseApiClient.NoScheduledSessionsException) {
            status = HttpStatus.UNPROCESSABLE_ENTITY;
            title = "Sin clases en el período";
            message = "No hay clases programadas en este período, así que no podemos cotizar.";
        } else if (failure instanceof BillingApiClient.NotFoundException) {
            status = HttpStatus.NOT_FOUND;
            title = "No se pudo cotizar";
            message = "Todavía no hay un precio publicado para esta clase, o la clase ya no está disponible.";
        } else if (failure instanceof PhysicalPurchaseApiClient.InvalidRequestException) {
            status = HttpStatus.BAD_REQUEST;
            title = "Datos inválidos";
            message = AuthFlowErrors.INVALID_REQUEST;
        } else {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            title = "Servicio no disponible";
            message = AuthFlowErrors.UNAVAILABLE;
        }
        model.addAttribute("title", title);
        model.addAttribute("message", message);
        model.addAttribute("linkHref", linkHref);
        model.addAttribute("linkLabel", linkLabel);
        response.setStatus(status.value());
        return NOTICE_VIEW;
    }
}
