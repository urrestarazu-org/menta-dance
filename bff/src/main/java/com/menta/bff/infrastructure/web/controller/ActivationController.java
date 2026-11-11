package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.ActivationRequirement;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.usecase.AccountActivationUseCase;
import com.menta.bff.infrastructure.web.dto.ActivationForm;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Account activation from the link in the email (#359, #378).
 *
 * <p>The link lands on {@code GET /activar/{token}}, which only validates the token and
 * shows the button. The token then travels back in the body of {@code POST /activar} and a
 * success redirects (Post/Redirect/Get) to a screen whose URL carries nothing.
 * </p>
 *
 * <p>Accounts created by an administrator have no password yet, so for them the page also asks
 * the holder to choose one and to repeat it. The page tells the two kinds apart by the answer of the token
 * validation; the submission tells them apart by the presence of the {@code password} field, so the
 * Auth API stays the authority: if the account turns out to need a password the page switches to the
 * password form instead of failing, and the token, which the API does not consume in that case, stays
 * usable.
 * </p>
 *
 * <p>The token and the password are credentials. They are never logged or echoed in a message, the token is
 * rendered only as the value of the hidden form field, the password never (the fields come back empty after
 * any error), and the pages that carry them forbid caching and the Referer header, so they cannot leak
 * through the browser cache or to a linked site.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class ActivationController {

    private static final String VIEW = "activate";
    private static final String FORM = "form";
    private static final String PASSWORD_REQUIRED_ATTRIBUTE = "passwordRequired";
    static final String PASSWORD_REQUIRED = "Elegí una contraseña para activar tu cuenta.";

    private final AccountActivationUseCase accountActivationUseCase;

    @GetMapping("/activar/{token}")
    public String confirm(@PathVariable String token, Model model, HttpServletResponse response) {
        keepSecretsOutOfCachesAndReferrers(response);
        ActivationRequirement requirement;
        try {
            requirement = accountActivationUseCase.checkToken(token);
        } catch (AuthApiClient.InvalidActivationTokenException e) {
            return invalidLink(model, response);
        } catch (AuthApiClient.RateLimitedException | AuthApiClient.ServiceUnavailableException e) {
            // The token could not be checked, not rejected: keep the button so the user can retry.
            AuthFlowErrors.apply(e, model, response);
            requirement = ActivationRequirement.NONE;
        }
        ActivationForm form = new ActivationForm();
        form.setToken(token);
        model.addAttribute(FORM, form);
        model.addAttribute(PASSWORD_REQUIRED_ATTRIBUTE, requirement == ActivationRequirement.PASSWORD);
        return VIEW;
    }

    @PostMapping(path = "/activar", params = "!password")
    public String activate(
            @ModelAttribute(FORM) ActivationForm form,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        keepSecretsOutOfCachesAndReferrers(response);
        return submit(form, bindingResult, model, response, false);
    }

    @PostMapping(path = "/activar", params = "password")
    public String activateChoosingPassword(
            @Valid @ModelAttribute(FORM) ActivationForm form,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        keepSecretsOutOfCachesAndReferrers(response);
        if (bindingResult.hasErrors()) {
            form.clearPasswords();
            return passwordForm(model);
        }
        return submit(form, bindingResult, model, response, true);
    }

    @GetMapping("/cuenta-activada")
    public String activated() {
        return "account-activated";
    }

    private String submit(ActivationForm form, BindingResult bindingResult, Model model,
                          HttpServletResponse response, boolean choosingPassword) {
        try {
            if (choosingPassword) {
                accountActivationUseCase.activate(form.getToken(), form.getPassword());
            } else {
                accountActivationUseCase.activate(form.getToken());
            }
        } catch (AuthApiClient.InvalidActivationTokenException e) {
            return invalidLink(model, response);
        } catch (AuthApiClient.PasswordRequiredException e) {
            return rejectPassword(form, bindingResult, "api.required", PASSWORD_REQUIRED, model);
        } catch (AuthApiClient.PasswordRejectedException e) {
            return rejectPassword(form, bindingResult, "api.rejected", AuthFlowErrors.WEAK_PASSWORD, model);
        } catch (AuthApiClient.RateLimitedException | AuthApiClient.ServiceUnavailableException e) {
            AuthFlowErrors.apply(e, model, response);
            form.clearPasswords();
            model.addAttribute(PASSWORD_REQUIRED_ATTRIBUTE, choosingPassword);
            return VIEW;
        }
        return "redirect:/cuenta-activada";
    }

    private static String rejectPassword(ActivationForm form, BindingResult bindingResult, String code,
                                         String message, Model model) {
        form.clearPasswords();
        bindingResult.rejectValue("password", code, message);
        return passwordForm(model);
    }

    private static String passwordForm(Model model) {
        model.addAttribute(PASSWORD_REQUIRED_ATTRIBUTE, true);
        return VIEW;
    }

    private static String invalidLink(Model model, HttpServletResponse response) {
        model.addAttribute("invalidToken", true);
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return VIEW;
    }

    private static void keepSecretsOutOfCachesAndReferrers(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
}
