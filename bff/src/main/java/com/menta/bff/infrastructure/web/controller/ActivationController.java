package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.usecase.AccountActivationUseCase;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Account activation from the link in the email (#359).
 *
 * <p>The link lands on {@code GET /activar/{token}}, which only validates the token and
 * shows the button. The token then travels back in the body of {@code POST /activar} and a
 * success redirects (Post/Redirect/Get) to a screen whose URL carries nothing.
 * </p>
 *
 * <p>The token is a credential. It is never logged or echoed in a message, it is rendered only
 * as the value of the hidden form field, and the pages that carry it forbid caching and the
 * Referer header, so it cannot leak through the browser cache or to a linked site.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class ActivationController {

    private static final String VIEW = "activate";

    private final AccountActivationUseCase accountActivationUseCase;

    @GetMapping("/activar/{token}")
    public String confirm(@PathVariable String token, Model model, HttpServletResponse response) {
        keepTokenOutOfCachesAndReferrers(response);
        try {
            if (!accountActivationUseCase.isTokenValid(token)) {
                return invalidLink(model, response);
            }
        } catch (AuthApiClient.RateLimitedException | AuthApiClient.ServiceUnavailableException e) {
            // The token could not be checked, not rejected: keep the button so the user can retry.
            AuthFlowErrors.apply(e, model, response);
        }
        model.addAttribute("token", token);
        return VIEW;
    }

    @PostMapping("/activar")
    public String activate(@RequestParam(defaultValue = "") String token, Model model,
                           HttpServletResponse response) {
        keepTokenOutOfCachesAndReferrers(response);
        try {
            accountActivationUseCase.activate(token);
        } catch (AuthApiClient.InvalidActivationTokenException e) {
            return invalidLink(model, response);
        } catch (AuthApiClient.RateLimitedException | AuthApiClient.ServiceUnavailableException e) {
            AuthFlowErrors.apply(e, model, response);
            model.addAttribute("token", token);
            return VIEW;
        }
        return "redirect:/cuenta-activada";
    }

    @GetMapping("/cuenta-activada")
    public String activated() {
        return "account-activated";
    }

    private static String invalidLink(Model model, HttpServletResponse response) {
        model.addAttribute("invalidToken", true);
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return VIEW;
    }

    private static void keepTokenOutOfCachesAndReferrers(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
}
