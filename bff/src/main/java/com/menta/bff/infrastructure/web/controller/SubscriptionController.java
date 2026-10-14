package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.SubscriptionCheckout;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.SubscriptionApiClient;
import com.menta.bff.application.usecase.SubscribeToPlanUseCase;
import com.menta.bff.infrastructure.config.ClassesProperties;
import com.menta.bff.infrastructure.web.dto.SubscriptionForm;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Plan subscription entry points (#361).
 *
 * <p>{@code POST /suscripciones} is for students only (the security config says so). Before the
 * browser leaves for Mercado Pago, the payment id and the purchase kind are kept in the
 * server-side session; the shared return page reads them. The redirect target is only ever a URL
 * the {@link CheckoutUrlPolicy} accepts.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class SubscriptionController {

    /** Value of the purchase kind attribute for a subscription payment. */
    static final String SUBSCRIPTION_KIND = "SUBSCRIPTION";

    static final String PLANS_PATH = "/plans";
    static final String BACK_TO_PLANS = "Ver planes";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SubscribeToPlanUseCase subscribeToPlanUseCase;
    private final ClassesProperties classesProperties;

    /**
     * Landing point of the anonymous "Suscribirme" link. The route is authenticated, so a visitor
     * is sent to login first and the saved request brings them back here; the redirect then
     * lands them on the plans page. The target is fixed: nothing from the request is used.
     *
     * @return redirect to the plans page
     */
    @GetMapping("/plans/suscribirme")
    public String entry() {
        return "redirect:" + PLANS_PATH;
    }

    /** Starts the Mercado Pago payment of a plan and sends the browser to the provider checkout. */
    @PostMapping("/suscripciones")
    public String subscribe(
            @Valid @ModelAttribute("form") SubscriptionForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            HttpSession session,
            Model model
    ) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (bindingResult.hasErrors()) {
            return PurchaseFlowErrors.apply(
                    new SubscriptionApiClient.InvalidRequestException("invalid subscription form"),
                    model, response, PLANS_PATH, BACK_TO_PLANS);
        }
        SubscriptionCheckout checkout;
        try {
            checkout = subscribeToPlanUseCase.execute(
                    (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE),
                    form.getPlanId(), form.getIdempotencyKey());
        } catch (SubscriptionApiClient.SubscriptionAlreadyActiveException e) {
            String endDate = e.currentEndDate() == null
                    ? null : DATE.format(e.currentEndDate().atZone(classesProperties.getDisplayZone()));
            return PurchaseFlowErrors.apply(e, model, response, PLANS_PATH, BACK_TO_PLANS, endDate);
        } catch (SubscriptionApiClient.SubscriptionFailureException
                 | BillingApiClient.ServiceUnavailableException e) {
            return PurchaseFlowErrors.apply(e, model, response, PLANS_PATH, BACK_TO_PLANS);
        }
        if (!CheckoutUrlPolicy.isAllowed(checkout.checkoutUrl())) {
            // Never log the URL itself: it can carry provider identifiers.
            log.error("Billing API returned a checkout URL that is not allowed as a redirect target");
            return PurchaseFlowErrors.apply(
                    new BillingApiClient.ServiceUnavailableException("unusable checkout URL"),
                    model, response, PLANS_PATH, BACK_TO_PLANS);
        }
        session.setAttribute(PhysicalPurchaseController.PAYMENT_ID_SESSION_ATTRIBUTE, checkout.paymentId());
        session.setAttribute(PhysicalPurchaseController.PURCHASE_KIND_SESSION_ATTRIBUTE, SUBSCRIPTION_KIND);
        return "redirect:" + checkout.checkoutUrl();
    }
}
