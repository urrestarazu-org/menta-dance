package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException;
import com.menta.bff.application.port.out.AdminBillingApiClient.PaymentRefusalException.Code;
import com.menta.bff.application.port.out.AdminBillingApiClient.ServiceUnavailableException;
import com.menta.bff.application.usecase.ExceptionPurchasesView;
import com.menta.bff.application.usecase.ListExceptionPurchasesUseCase;
import com.menta.bff.application.usecase.ResolveExceptionPurchaseUseCase;
import com.menta.bff.infrastructure.web.dto.ResolvePurchaseForm;
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

/**
 * The administrator's purchases in exception (#370): the list {@code GET /gestion/compras-con-excepcion} and the
 * resolution {@code POST /gestion/compras-con-excepcion/{purchaseId}/resolver}. The API has no detail of a
 * purchase, so each row of the list carries its own resolution form. Access is restricted to {@code ADMIN} in
 * {@code BffSecurityConfig}; the Billing API makes the real decision and decides whether the purchase may be closed.
 *
 * <p>Every answer is {@code no-store}. The resolution is Post/Redirect/Get with the outcome in a flash attribute
 * (success in one, failure in another), always back to the list. A form that does not validate renders the list
 * again with the errors next to the fields of that row and never reaches the API. Each refusal is worded here from
 * its code; the API's body and detail, and the note, never reach the browser or a log.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ManagementExceptionPurchasesController {

    static final String RESOLVED = "Compra resuelta.";
    static final String ALREADY_RESOLVED = "La compra ya fue resuelta con otra decisión o nota.";
    static final String NO_LONGER_IN_EXCEPTION = "La compra ya no está en excepción.";
    static final String NOT_FOUND = "No encontramos la compra.";
    static final String INVALID = "Revisá los datos ingresados.";
    static final String UNAVAILABLE = "No pudimos comunicarnos con el servicio de pagos. Intentá de nuevo.";

    private static final String LIST_PATH = "/gestion/compras-con-excepcion";
    private static final String RESOLVE_PATH = LIST_PATH + "/{purchaseId}/resolver";
    private static final String LIST_VIEW = "gestion/compras-con-excepcion/list";
    private static final String SAVED = "purchasesSaved";
    private static final String ERROR = "purchasesError";

    private final ListExceptionPurchasesUseCase listExceptionPurchasesUseCase;
    private final ResolveExceptionPurchaseUseCase resolveExceptionPurchaseUseCase;

    @GetMapping(LIST_PATH)
    public String list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        model.addAttribute("resolveForm", new ResolvePurchaseForm());
        return listView(page, size, request, response, model);
    }

    @PostMapping(RESOLVE_PATH)
    public String resolve(
            @PathVariable String purchaseId,
            @Valid @ModelAttribute("resolveForm") ResolvePurchaseForm form,
            BindingResult bindingResult,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("resolvingPurchaseId", purchaseId);
            return listView(page, size, request, response, model);
        }
        noStore(response);
        try {
            resolveExceptionPurchaseUseCase.execute(accessToken(request), purchaseId, form.toResolution(),
                    form.getNote());
        } catch (PaymentRefusalException e) {
            redirectAttributes.addFlashAttribute(ERROR, refusalMessage(e.getCode()));
            return "redirect:" + LIST_PATH;
        } catch (ServiceUnavailableException e) {
            log.warn("Purchase resolution unavailable: {}", e.getMessage());
            redirectAttributes.addFlashAttribute(ERROR, UNAVAILABLE);
            return "redirect:" + LIST_PATH;
        }
        redirectAttributes.addFlashAttribute(SAVED, RESOLVED);
        return "redirect:" + LIST_PATH;
    }

    // ---- helpers ----

    /** Reads one page into the model; when the API cannot answer, fills the degraded view with a 503. */
    private String listView(Integer page, Integer size, HttpServletRequest request, HttpServletResponse response,
            Model model) {
        noStore(response);
        ExceptionPurchasesView view;
        try {
            view = listExceptionPurchasesUseCase.execute(accessToken(request), page, size);
        } catch (PaymentRefusalException | ServiceUnavailableException e) {
            log.warn("Purchases in exception unavailable, rendering the screen without them: {}", e.getMessage());
            view = ExceptionPurchasesView.unavailableView();
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        model.addAttribute("purchases", view);
        model.addAttribute("unavailableMessage", UNAVAILABLE);
        return LIST_VIEW;
    }

    private static String refusalMessage(Code code) {
        return switch (code) {
            case PURCHASE_ALREADY_RESOLVED -> ALREADY_RESOLVED;
            case ILLEGAL_PURCHASE_STATE_TRANSITION -> NO_LONGER_IN_EXCEPTION;
            case PURCHASE_NOT_FOUND -> NOT_FOUND;
            case INVALID_REQUEST -> INVALID;
            // The purchase endpoints never answer the payment codes: if one did, it is not one this screen words.
            case PAYMENT_NOT_FOUND, PROOF_NOT_FOUND, ILLEGAL_PAYMENT_STATE_TRANSITION -> UNAVAILABLE;
        };
    }

    private static String accessToken(HttpServletRequest request) {
        return (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
    }

    private static void noStore(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
