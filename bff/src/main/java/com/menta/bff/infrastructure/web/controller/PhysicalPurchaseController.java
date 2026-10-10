package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.PhysicalCheckout;
import com.menta.bff.application.dto.PhysicalPurchaseType;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.CatalogApiClient;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import com.menta.bff.application.usecase.PayPhysicalPurchaseUseCase;
import com.menta.bff.application.usecase.QuotePhysicalPurchaseUseCase;
import com.menta.bff.infrastructure.web.dto.PhysicalPurchaseForm;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

/**
 * Web purchase of a physical class (#365): quote, then pay with Mercado Pago. The return page comes
 * later. Every route needs a signed-in student: the security config lists none of them as public.
 *
 * <p>Before the browser leaves for Mercado Pago, the payment id of the checkout is kept in the
 * server-side session; the return page will ask the Billing API about that id and never trust the
 * URL the provider redirects to.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class PhysicalPurchaseController {

    /** Session attribute holding the id of the payment the buyer is about to pay. */
    static final String PAYMENT_ID_SESSION_ATTRIBUTE = "com.menta.bff.PENDING_PAYMENT_ID";

    private static final String CLASSES_PATH = "/classes";
    private static final String BACK_TO_CLASS = "Volver a la clase";

    private final QuotePhysicalPurchaseUseCase quotePhysicalPurchaseUseCase;
    private final PayPhysicalPurchaseUseCase payPhysicalPurchaseUseCase;

    /**
     * Quotes a class and renders the quote. No Post/Redirect/Get: a quote is informative and
     * harmless to repeat (the API issues a fresh one each time).
     */
    @PostMapping("/classes/{courseId}/cotizar")
    public String quote(
            @PathVariable String courseId,
            @RequestParam PhysicalPurchaseType purchaseType,
            @RequestParam(required = false) String selectedSessionId,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            model.addAttribute("quote", quotePhysicalPurchaseUseCase.execute(
                    accessToken(request), courseId, purchaseType, selectedSessionId));
            return "purchase-quote";
        } catch (PhysicalPurchaseApiClient.PurchaseFailureException
                 | BillingApiClient.NotFoundException
                 | BillingApiClient.ServiceUnavailableException
                 | CatalogApiClient.ServiceUnavailableException e) {
            return PurchaseFlowErrors.apply(e, model, response, classPath(courseId), BACK_TO_CLASS);
        }
    }

    /** Starts the Mercado Pago payment and sends the browser to the provider checkout. */
    @PostMapping("/compras")
    public String pay(
            @Valid @ModelAttribute("form") PhysicalPurchaseForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpSession session,
            Model model
    ) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String back = classPath(form.getCourseId());
        if (bindingResult.hasErrors()) {
            return PurchaseFlowErrors.apply(
                    new PhysicalPurchaseApiClient.InvalidRequestException("invalid purchase form"),
                    model, response, back, BACK_TO_CLASS);
        }
        PhysicalCheckout checkout;
        try {
            checkout = payPhysicalPurchaseUseCase.execute(
                    accessToken(request), form.getQuoteId(), form.getIdempotencyKey());
        } catch (PhysicalPurchaseApiClient.PurchaseFailureException
                 | BillingApiClient.NotFoundException
                 | BillingApiClient.ServiceUnavailableException e) {
            return PurchaseFlowErrors.apply(e, model, response, back, BACK_TO_CLASS);
        }
        if (!CheckoutUrlPolicy.isAllowed(checkout.checkoutUrl())) {
            // Never log the URL itself: it can carry provider identifiers.
            log.error("Billing API returned a checkout URL that is not allowed as a redirect target");
            return PurchaseFlowErrors.apply(
                    new BillingApiClient.ServiceUnavailableException("unusable checkout URL"),
                    model, response, back, BACK_TO_CLASS);
        }
        session.setAttribute(PAYMENT_ID_SESSION_ATTRIBUTE, checkout.paymentId());
        return "redirect:" + checkout.checkoutUrl();
    }

    private static String accessToken(HttpServletRequest request) {
        return (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
    }

    private static String classPath(String courseId) {
        if (courseId == null || courseId.isBlank()) {
            return CLASSES_PATH;
        }
        return CLASSES_PATH + "/" + UriUtils.encodePathSegment(courseId, StandardCharsets.UTF_8);
    }
}
