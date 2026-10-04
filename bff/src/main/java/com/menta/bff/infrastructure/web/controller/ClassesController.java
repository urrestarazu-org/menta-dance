package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.ClassPaymentMethod;
import com.menta.bff.application.dto.ClassPurchaseType;
import com.menta.bff.application.usecase.ClassPurchaseView;
import com.menta.bff.application.usecase.ClassQuoteView;
import com.menta.bff.application.usecase.GetClassDetailViewUseCase;
import com.menta.bff.application.usecase.GetClassesViewUseCase;
import com.menta.bff.application.usecase.PurchaseClassUseCase;
import com.menta.bff.application.usecase.QuoteClassUseCase;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * In-person classes: browse, availability, quote and checkout (spec {@code
 * bff-physical-classes-view}).
 *
 * <p>The two {@code GET} routes are anonymous-reachable; the two {@code POST}
 * routes require a session ({@code BffSecurityConfig}). Like {@link
 * CourseDetailController}, this controller only renders what the use cases
 * decided — the {@code switch} statements are exhaustive over sealed views, so
 * the compiler guarantees every outcome has a page. Transport failures stay
 * uncaught {@code @ResponseStatus} exceptions and land on the shared error view.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class ClassesController {

    private final GetClassesViewUseCase getClassesViewUseCase;
    private final GetClassDetailViewUseCase getClassDetailViewUseCase;
    private final QuoteClassUseCase quoteClassUseCase;
    private final PurchaseClassUseCase purchaseClassUseCase;

    @GetMapping("/classes")
    public String classes(Model model) {
        model.addAttribute("classes", getClassesViewUseCase.execute());
        return "classes";
    }

    @GetMapping("/classes/{courseId}")
    public String classDetail(@PathVariable String courseId, HttpServletRequest request, Model model) {
        model.addAttribute("course", getClassDetailViewUseCase.execute(courseId));
        // Buy forms are only offered to a caller who can submit them; anonymous
        // visitors get a login link instead of a form that would bounce.
        model.addAttribute("signedIn", accessToken(request) != null);
        return "class-detail";
    }

    @PostMapping("/classes/{courseId}/quote")
    public String quote(
            @PathVariable String courseId,
            @RequestParam ClassPurchaseType purchaseType,
            @RequestParam(required = false) String sessionId,
            HttpServletRequest request,
            Model model) {
        ClassQuoteView view = quoteClassUseCase.execute(courseId, purchaseType, sessionId, accessToken(request));

        model.addAttribute("courseId", view.courseId());
        switch (view) {
            case ClassQuoteView.Payable payable -> model.addAttribute("payable", payable);
            case ClassQuoteView.SoldOut soldOut -> model.addAttribute("outcome", "soldOut");
            case ClassQuoteView.Rejected rejected -> model.addAttribute("outcome", "rejected");
        }
        return "class-quote";
    }

    @PostMapping("/classes/purchases")
    public String purchase(
            @RequestParam String courseId,
            @RequestParam String quoteId,
            @RequestParam ClassPaymentMethod paymentMethod,
            @RequestParam String idempotencyKey,
            HttpServletRequest request,
            Model model) {
        ClassPurchaseView view = purchaseClassUseCase.execute(
                courseId, quoteId, paymentMethod, idempotencyKey, accessToken(request));

        model.addAttribute("courseId", courseId);
        switch (view) {
            case ClassPurchaseView.RedirectToProvider redirect -> {
                // The URL comes from our own billing response, never from the request.
                return "redirect:" + redirect.checkoutUrl();
            }
            case ClassPurchaseView.BankTransfer bankTransfer -> {
                model.addAttribute("bankTransfer", bankTransfer.instructions());
            }
            case ClassPurchaseView.SoldOut soldOut -> model.addAttribute("outcome", "soldOut");
            case ClassPurchaseView.QuoteExpired expired -> model.addAttribute("outcome", "quoteExpired");
        }
        return "class-purchase";
    }

    private static String accessToken(HttpServletRequest request) {
        return (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
    }
}
