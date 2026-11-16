package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.usecase.GetPaymentHistoryUseCase;
import com.menta.bff.application.usecase.GetSubscriptionHistoryUseCase;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * History page of a student (#363): subscriptions and payments.
 *
 * <p>Only a student may open it: {@code BffSecurityConfig} answers 403 to any other role and sends
 * an anonymous visitor to the login before this controller runs. Both sections are read with the
 * access token the {@link TokenRefreshFilter} put on the request and are independent of each other:
 * each use case fails closed on its own (a missing token included, with no Billing call), so a
 * failure of either one never hides the other. The response is marked {@code Cache-Control:
 * no-store} because it carries per-user state.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class HistoryController {

    private final GetSubscriptionHistoryUseCase getSubscriptionHistoryUseCase;
    private final GetPaymentHistoryUseCase getPaymentHistoryUseCase;

    @GetMapping("/dashboard/history")
    public String history(HttpServletRequest request, HttpServletResponse response, Model model) {
        String accessToken = (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        model.addAttribute("subscriptionHistory", getSubscriptionHistoryUseCase.execute(accessToken));
        model.addAttribute("paymentHistory", getPaymentHistoryUseCase.execute(accessToken));
        return "history";
    }
}
