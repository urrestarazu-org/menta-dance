package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException;
import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException.Code;
import com.menta.bff.application.port.out.AdminBillingApiClient.ServiceUnavailableException;
import com.menta.bff.application.usecase.ApprovePaymentUseCase;
import com.menta.bff.application.usecase.CorrectPaymentUseCase;
import com.menta.bff.application.usecase.GetPaymentDetailUseCase;
import com.menta.bff.application.usecase.ListPendingPaymentsUseCase;
import com.menta.bff.application.usecase.PendingPaymentsView;
import com.menta.bff.application.usecase.RejectPaymentUseCase;
import com.menta.bff.infrastructure.web.dto.CorrectPaymentForm;
import com.menta.bff.infrastructure.web.dto.RejectPaymentForm;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The administrator's manual payment verification (#370): the inbox {@code GET /gestion/pagos}, the detail
 * {@code GET /gestion/pagos/{paymentId}} and the three actions on it, {@code POST .../aprobar},
 * {@code .../rechazar} and {@code .../corregir}. The proof itself is served by
 * {@link ManagementPaymentProofController}. Access is restricted to {@code ADMIN} in {@code BffSecurityConfig}; the
 * Billing API makes the real decision with the session's access token and decides which transitions are legal.
 *
 * <p>Every answer is {@code no-store}: the pages hold payment data. Each action is Post/Redirect/Get with the
 * outcome in a flash attribute (success in one, failure in another): an approval or rejection goes back to the
 * inbox, a correction to the detail, and a failure to the detail, where the payment shows its current state (a
 * payment that does not exist goes to the inbox). A form that does not validate renders the detail again with the
 * errors next to their fields and never reaches the API. Each refusal is worded here from its code; the API's
 * body, its detail and its signed links never reach the browser.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ManagementPaymentsController {

    static final String APPROVED = "Pago aprobado.";
    static final String REJECTED = "Pago rechazado.";
    static final String CORRECTED = "Corrección registrada.";
    static final String ILLEGAL_TRANSITION =
            "El pago ya fue resuelto o ya no admite esta operación. Revisá su estado actual.";
    static final String NOT_FOUND = "No encontramos el pago.";
    static final String INVALID = "Revisá los datos ingresados.";
    static final String UNAVAILABLE = "No pudimos comunicarnos con el servicio de pagos. Intentá de nuevo.";

    private static final String LIST_PATH = "/gestion/pagos";
    private static final String DETAIL_PATH = LIST_PATH + "/{paymentId}";
    private static final String LIST_VIEW = "gestion/pagos/list";
    private static final String DETAIL_VIEW = "gestion/pagos/detail";
    private static final String PROBLEM_VIEW = "gestion/pagos/problem";
    private static final String SAVED = "paymentsSaved";
    private static final String ERROR = "paymentsError";

    private final ListPendingPaymentsUseCase listPendingPaymentsUseCase;
    private final GetPaymentDetailUseCase getPaymentDetailUseCase;
    private final ApprovePaymentUseCase approvePaymentUseCase;
    private final RejectPaymentUseCase rejectPaymentUseCase;
    private final CorrectPaymentUseCase correctPaymentUseCase;

    @GetMapping(LIST_PATH)
    public String list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        noStore(response);
        PendingPaymentsView view;
        try {
            view = listPendingPaymentsUseCase.execute(accessToken(request), page, size);
        } catch (PaymentRefusalException | ServiceUnavailableException e) {
            log.warn("Pending payments unavailable, rendering the screen without them: {}", e.getMessage());
            view = PendingPaymentsView.unavailableView();
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        model.addAttribute("payments", view);
        model.addAttribute("unavailableMessage", UNAVAILABLE);
        return LIST_VIEW;
    }

    @GetMapping(DETAIL_PATH)
    public String detail(
            @PathVariable String paymentId, HttpServletRequest request, HttpServletResponse response, Model model) {
        noStore(response);
        return detailView(paymentId, request, response, model);
    }

    @PostMapping(DETAIL_PATH + "/aprobar")
    public String approve(
            @PathVariable String paymentId, HttpServletRequest request, HttpServletResponse response,
            RedirectAttributes redirectAttributes) {
        noStore(response);
        return perform(paymentId, () -> approvePaymentUseCase.execute(accessToken(request), paymentId), APPROVED,
                LIST_PATH, redirectAttributes);
    }

    @PostMapping(DETAIL_PATH + "/rechazar")
    public String reject(
            @PathVariable String paymentId,
            @Valid @ModelAttribute("rejectForm") RejectPaymentForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        noStore(response);
        if (bindingResult.hasErrors()) {
            return detailView(paymentId, request, response, model);
        }
        return perform(paymentId,
                () -> rejectPaymentUseCase.execute(accessToken(request), paymentId, form.getReason()), REJECTED,
                LIST_PATH, redirectAttributes);
    }

    @PostMapping(DETAIL_PATH + "/corregir")
    public String correct(
            @PathVariable String paymentId,
            @Valid @ModelAttribute("correctForm") CorrectPaymentForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        noStore(response);
        if (bindingResult.hasErrors()) {
            return detailView(paymentId, request, response, model);
        }
        return perform(paymentId, () -> correctPaymentUseCase.execute(accessToken(request), paymentId,
                form.toDecision(), form.getReason(), form.getEvidence()), CORRECTED, detailPath(paymentId),
                redirectAttributes);
    }

    // ---- helpers ----

    /** Reads the payment into the model, with the two forms of the detail; when it cannot, fills the notice page. */
    private String detailView(String paymentId, HttpServletRequest request, HttpServletResponse response,
            Model model) {
        try {
            model.addAttribute("payment", getPaymentDetailUseCase.execute(accessToken(request), paymentId));
        } catch (PaymentRefusalException e) {
            boolean missing = e.getCode() == Code.PAYMENT_NOT_FOUND;
            return problem(missing ? HttpStatus.NOT_FOUND : HttpStatus.SERVICE_UNAVAILABLE,
                    missing ? NOT_FOUND : UNAVAILABLE, response, model);
        } catch (ServiceUnavailableException e) {
            log.warn("Payment unavailable, rendering the notice: {}", e.getMessage());
            return problem(HttpStatus.SERVICE_UNAVAILABLE, UNAVAILABLE, response, model);
        }
        if (!model.containsAttribute("rejectForm")) {
            model.addAttribute("rejectForm", new RejectPaymentForm());
        }
        if (!model.containsAttribute("correctForm")) {
            model.addAttribute("correctForm", new CorrectPaymentForm());
        }
        return DETAIL_VIEW;
    }

    private static String problem(HttpStatus status, String message, HttpServletResponse response, Model model) {
        response.setStatus(status.value());
        model.addAttribute("problemTitle", status == HttpStatus.NOT_FOUND ? "Pago no encontrado" : "Pagos");
        model.addAttribute("problemMessage", message);
        return PROBLEM_VIEW;
    }

    /** Runs an action and words its outcome in a flash attribute; returns the redirect for it. */
    private String perform(String paymentId, Runnable action, String success, String successTarget,
            RedirectAttributes redirectAttributes) {
        try {
            action.run();
        } catch (PaymentRefusalException e) {
            redirectAttributes.addFlashAttribute(ERROR, refusalMessage(e.getCode()));
            return "redirect:" + (e.getCode() == Code.PAYMENT_NOT_FOUND ? LIST_PATH : detailPath(paymentId));
        } catch (ServiceUnavailableException e) {
            log.warn("Payment action unavailable: {}", e.getMessage());
            redirectAttributes.addFlashAttribute(ERROR, UNAVAILABLE);
            return "redirect:" + detailPath(paymentId);
        }
        redirectAttributes.addFlashAttribute(SAVED, success);
        return "redirect:" + successTarget;
    }

    private static String refusalMessage(Code code) {
        return switch (code) {
            case ILLEGAL_PAYMENT_STATE_TRANSITION -> ILLEGAL_TRANSITION;
            case PAYMENT_NOT_FOUND, PROOF_NOT_FOUND -> NOT_FOUND;
            case INVALID_REQUEST -> INVALID;
        };
    }

    /** The detail's address; the id is expanded as a URI variable, so whatever it holds is encoded as one segment. */
    private static String detailPath(String paymentId) {
        return UriComponentsBuilder.fromPath(DETAIL_PATH).encode().build(paymentId).toString();
    }

    private static String accessToken(HttpServletRequest request) {
        return (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
    }

    private static void noStore(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
