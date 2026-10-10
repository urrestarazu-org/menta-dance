package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.PhysicalPurchaseType;
import com.menta.bff.application.port.out.BillingApiClient;
import com.menta.bff.application.port.out.CatalogApiClient;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import com.menta.bff.application.usecase.QuotePhysicalPurchaseUseCase;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

/**
 * Web purchase of a physical class (#365). This slice only quotes; paying and the return page
 * come later. The route needs a signed-in student: the security config does not list it as public.
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class PhysicalPurchaseController {

    private final QuotePhysicalPurchaseUseCase quotePhysicalPurchaseUseCase;

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
        String accessToken = (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
        try {
            model.addAttribute("quote",
                    quotePhysicalPurchaseUseCase.execute(accessToken, courseId, purchaseType, selectedSessionId));
            return "purchase-quote";
        } catch (PhysicalPurchaseApiClient.PurchaseFailureException
                 | BillingApiClient.NotFoundException
                 | BillingApiClient.ServiceUnavailableException
                 | CatalogApiClient.ServiceUnavailableException e) {
            return PurchaseFlowErrors.apply(e, model, response,
                    "/classes/" + UriUtils.encodePathSegment(courseId, StandardCharsets.UTF_8), "Volver a la clase");
        }
    }
}
