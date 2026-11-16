package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.DashboardView;
import com.menta.bff.application.usecase.CancelSubscriptionResult;
import com.menta.bff.application.usecase.CancelSubscriptionResult.Outcome;
import com.menta.bff.application.usecase.CancelSubscriptionUseCase;
import com.menta.bff.application.usecase.GetDashboardViewUseCase;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The student cancelling their own subscription (#363): a confirmation page and the POST that cancels.
 *
 * <p>Same path for both verbs, as the management cancel screens do. Only a student may reach either:
 * {@code BffSecurityConfig} answers 403 to any other role, sends an anonymous visitor to the login and
 * requires the CSRF token on the POST before this controller runs. Both responses are marked {@code
 * Cache-Control: no-store} because they carry per-user state. Both read the access token the {@link
 * TokenRefreshFilter} put on the request; neither it nor an upstream body is ever rendered.
 * </p>
 *
 * <p>The POST never renders: it is Post/Redirect/Get to the dashboard, where the outcome is shown once as a
 * flash notice. The confirmation shows the plan and the end date only when the dashboard use case could read
 * an {@code ACTIVE} subscription; if it could not, the page still asks, without inventing a date, and the
 * Billing API decides on the POST. With nothing to cancel (no subscription, pending, expired or already
 * cancelled) the page sends the student back to the dashboard instead of asking.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class CancelSubscriptionController {

    static final String PATH = "/suscripciones/cancelar";
    static final String CANCELLED =
            "Cancelamos tu suscripción: no se va a renovar. Mantenés el acceso hasta el %s.";
    static final String NOTHING_TO_CANCEL = "No tenés una suscripción activa para cancelar.";
    static final String CONFLICT = "Tu suscripción cambió mientras la cancelábamos. Intentá de nuevo.";
    static final String UNAVAILABLE = "No pudimos confirmar la cancelación de tu suscripción. "
            + "Revisá su estado en el panel o probá de nuevo en unos minutos.";

    private static final String DASHBOARD = "redirect:/dashboard";

    private final GetDashboardViewUseCase getDashboardViewUseCase;
    private final CancelSubscriptionUseCase cancelSubscriptionUseCase;

    @GetMapping(PATH)
    public String confirm(HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String accessToken = (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
        DashboardView subscription = accessToken == null || accessToken.isBlank()
                ? new DashboardView.Unavailable()
                : getDashboardViewUseCase.execute(accessToken);
        if (subscription instanceof DashboardView.Active active) {
            model.addAttribute("planName", active.planName());
            model.addAttribute("endDateLabel", active.endDateLabel());
        } else if (!(subscription instanceof DashboardView.Unavailable)) {
            // Nothing to cancel. When the state is unknown the page asks anyway, with no plan and no date.
            return DASHBOARD;
        }
        return "cancel-subscription";
    }

    @PostMapping(PATH)
    public String cancel(
            HttpServletRequest request, HttpServletResponse response, RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String accessToken = (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
        CancelSubscriptionResult result = cancelSubscriptionUseCase.execute(accessToken);
        redirectAttributes.addFlashAttribute("subscriptionNoticeKind", kind(result.outcome()));
        redirectAttributes.addFlashAttribute("subscriptionNotice", message(result));
        return DASHBOARD;
    }

    private static String kind(Outcome outcome) {
        return switch (outcome) {
            case CANCELLED -> "success";
            case NOTHING_TO_CANCEL -> "info";
            case CONFLICT, UNAVAILABLE -> "error";
        };
    }

    private static String message(CancelSubscriptionResult result) {
        return switch (result.outcome()) {
            case CANCELLED -> CANCELLED.formatted(result.accessEndsLabel());
            case NOTHING_TO_CANCEL -> NOTHING_TO_CANCEL;
            case CONFLICT -> CONFLICT;
            case UNAVAILABLE -> UNAVAILABLE;
        };
    }
}
