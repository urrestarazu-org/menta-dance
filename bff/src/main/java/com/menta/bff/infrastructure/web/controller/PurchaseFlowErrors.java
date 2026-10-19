package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import com.menta.bff.application.port.out.SubscriptionApiClient;
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
        return apply(failure, model, response, linkHref, linkLabel, null);
    }

    /**
     * Same as {@link #apply(RuntimeException, Model, HttpServletResponse, String, String)} for a
     * failure that may carry a date the caller has already formatted.
     *
     * @param endDateLabel the end of the blocking subscription as shown to the student, or {@code null}
     */
    static String apply(
            RuntimeException failure, Model model, HttpServletResponse response, String linkHref, String linkLabel,
            String endDateLabel) {
        HttpStatus status;
        String title;
        String message;
        if (failure instanceof PhysicalPurchaseApiClient.NoScheduledSessionsException) {
            status = HttpStatus.UNPROCESSABLE_ENTITY;
            title = "Sin clases en el período";
            message = "No hay clases programadas en este período, así que no podemos cotizar.";
        } else if (failure instanceof PhysicalPurchaseApiClient.NoNewSessionsException) {
            status = HttpStatus.CONFLICT;
            title = "Ya tenés estas clases";
            message = "Ya tenés lugar en todas las sesiones que cubriría esta compra, "
                    + "así que no hace falta pagar de nuevo. No se realizó ningún cobro.";
        } else if (failure instanceof PhysicalPurchaseApiClient.CapacityUnavailableException) {
            status = HttpStatus.CONFLICT;
            title = "Sin lugares";
            message = "Ya no hay lugares disponibles para esta cotización. No se realizó ningún cobro.";
        } else if (failure instanceof PhysicalPurchaseApiClient.QuoteExpiredException) {
            status = HttpStatus.GONE;
            title = "Cotización vencida";
            message = "La cotización venció, pedí una nueva.";
        } else if (failure instanceof PhysicalPurchaseApiClient.ProofRejectedException) {
            status = HttpStatus.BAD_REQUEST;
            title = "Comprobante inválido";
            message = "No pudimos aceptar el archivo. Subí una imagen PNG o JPG, o un PDF, de hasta 5 MB.";
        } else if (failure instanceof PhysicalPurchaseApiClient.TooManyProofsException) {
            status = HttpStatus.TOO_MANY_REQUESTS;
            title = "Demasiados comprobantes";
            message = "Alcanzaste el máximo de comprobantes para este pago. Intentá de nuevo más tarde.";
        } else if (failure instanceof BillingApiClient.NotFoundException) {
            status = HttpStatus.NOT_FOUND;
            title = "No se pudo cotizar";
            message = "Todavía no hay un precio publicado para esta clase, o la clase ya no está disponible.";
        } else if (failure instanceof SubscriptionApiClient.SubscriptionAlreadyActiveException) {
            status = HttpStatus.CONFLICT;
            title = "Ya tenés una suscripción";
            message = endDateLabel == null
                    ? "Ya tenés una suscripción vigente."
                    : "Ya tenés una suscripción vigente hasta el " + endDateLabel + ".";
        } else if (failure instanceof SubscriptionApiClient.SubscriptionConflictException) {
            status = HttpStatus.CONFLICT;
            title = "La suscripción cambió";
            message = "La suscripción cambió mientras la procesábamos. Volvé a Planes e intentá de nuevo.";
        } else if (failure instanceof SubscriptionApiClient.PlanNotAvailableException) {
            status = HttpStatus.UNPROCESSABLE_ENTITY;
            title = "Plan no disponible";
            message = "Este plan no está disponible para suscribirse en este momento.";
        } else if (failure instanceof PhysicalPurchaseApiClient.InvalidRequestException
                || failure instanceof SubscriptionApiClient.InvalidRequestException) {
            status = HttpStatus.BAD_REQUEST;
            title = "Datos inválidos";
            message = AuthFlowErrors.INVALID_REQUEST;
        } else {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            title = "Servicio no disponible";
            message = AuthFlowErrors.UNAVAILABLE;
        }
        response.setStatus(status.value());
        model.addAttribute("failure", true);
        return notice(model, title, message, linkHref, linkLabel);
    }

    /** Fills the notice model without touching the response status. */
    static String notice(Model model, String title, String message, String linkHref, String linkLabel) {
        model.addAttribute("title", title);
        model.addAttribute("message", message);
        model.addAttribute("linkHref", linkHref);
        model.addAttribute("linkLabel", linkLabel);
        return NOTICE_VIEW;
    }
}
