package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.RequestPasswordResetCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.usecase.RequestPasswordResetUseCase;
import com.menta.bff.application.usecase.ResetPasswordUseCase;
import com.menta.bff.infrastructure.security.ClientOriginResolver;
import com.menta.bff.infrastructure.web.dto.RequestPasswordResetForm;
import com.menta.bff.infrastructure.web.dto.ResetPasswordForm;
import jakarta.servlet.http.HttpServletRequest;
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
 * Password recovery from the web (#360).
 *
 * <p>{@code /recuperar-contrasena} asks for the email and always ends on the same
 * confirmation screen (Post/Redirect/Get), whatever the account, with no email in the URL.
 * The email link lands on {@code GET /restablecer-contrasena/{token}}, which shows the form;
 * the token goes back in the body of {@code POST /restablecer-contrasena} and a success
 * redirects to the login.
 * </p>
 *
 * <p>The token, the email and the password are credentials. They are never logged or echoed in
 * a message; the token is rendered only as the value of a hidden field, the password never,
 * and the pages that carry the token forbid caching and the Referer header.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class PasswordResetController {

    private static final String REQUEST_VIEW = "request-password-reset";
    private static final String RESET_VIEW = "reset-password";
    static final String WEAK_PASSWORD = "La contraseña no cumple los requisitos. Elegí una de al menos 8 "
            + "caracteres, con una mayúscula y un número.";
    static final String SAME_PASSWORD = "La nueva contraseña debe ser diferente a la actual.";

    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;
    private final ClientOriginResolver clientOriginResolver;

    @GetMapping("/recuperar-contrasena")
    public String requestForm(Model model) {
        model.addAttribute("form", new RequestPasswordResetForm());
        return REQUEST_VIEW;
    }

    @PostMapping("/recuperar-contrasena")
    public String request(
            @Valid @ModelAttribute("form") RequestPasswordResetForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return REQUEST_VIEW;
        }
        try {
            requestPasswordResetUseCase.execute(
                    new RequestPasswordResetCommand(form.getEmail(), clientOriginResolver.resolve(request)));
        } catch (AuthApiClient.RateLimitedException
                 | AuthApiClient.InvalidRequestException
                 | AuthApiClient.ServiceUnavailableException e) {
            AuthFlowErrors.apply(e, model, response);
            return REQUEST_VIEW;
        }
        return "redirect:/recuperar-contrasena/enviado";
    }

    @GetMapping("/recuperar-contrasena/enviado")
    public String requested() {
        return "password-reset-requested";
    }

    @GetMapping("/restablecer-contrasena/{token}")
    public String resetForm(@PathVariable String token, Model model, HttpServletResponse response) {
        keepTokenOutOfCachesAndReferrers(response);
        ResetPasswordForm form = new ResetPasswordForm();
        form.setToken(token);
        model.addAttribute("form", form);
        return RESET_VIEW;
    }

    @PostMapping("/restablecer-contrasena")
    public String reset(
            @Valid @ModelAttribute("form") ResetPasswordForm form,
            BindingResult bindingResult,
            Model model,
            HttpServletResponse response
    ) {
        keepTokenOutOfCachesAndReferrers(response);
        if (form.getToken() == null || form.getToken().isBlank()) {
            return invalidLink(model, response);
        }
        if (bindingResult.hasErrors()) {
            return RESET_VIEW;
        }
        try {
            resetPasswordUseCase.execute(form.getToken(), form.getPassword());
        } catch (AuthApiClient.InvalidResetTokenException e) {
            return invalidLink(model, response);
        } catch (AuthApiClient.PasswordRejectedException e) {
            bindingResult.rejectValue("password", "api.rejected",
                    e.reason() == AuthApiClient.PasswordRejectedException.Reason.SAME_AS_CURRENT
                            ? SAME_PASSWORD : WEAK_PASSWORD);
            return RESET_VIEW;
        } catch (AuthApiClient.RateLimitedException
                 | AuthApiClient.InvalidRequestException
                 | AuthApiClient.ServiceUnavailableException e) {
            AuthFlowErrors.apply(e, model, response);
            return RESET_VIEW;
        }
        return "redirect:/login?reset";
    }

    private static String invalidLink(Model model, HttpServletResponse response) {
        model.addAttribute("invalidToken", true);
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return RESET_VIEW;
    }

    private static void keepTokenOutOfCachesAndReferrers(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
}
