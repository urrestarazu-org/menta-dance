package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.ResendActivationCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.usecase.ResendActivationUseCase;
import com.menta.bff.infrastructure.security.ClientOriginResolver;
import com.menta.bff.infrastructure.web.dto.ResendActivationForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Resend of the activation email (#359).
 *
 * <p>A successful submission always lands on the same "check your email" screen
 * (Post/Redirect/Get) as the registration: the Auth API answers 202 whether the email is
 * unknown, pending or active, and this controller adds nothing that could tell them
 * apart. The email is never put in the redirect.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class ResendActivationController {

    private static final String VIEW = "resend-activation";

    private final ResendActivationUseCase resendActivationUseCase;
    private final ClientOriginResolver clientOriginResolver;

    @GetMapping("/reenviar-activacion")
    public String form(Model model) {
        model.addAttribute("form", new ResendActivationForm());
        return VIEW;
    }

    @PostMapping("/reenviar-activacion")
    public String submit(
            @Valid @ModelAttribute("form") ResendActivationForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return VIEW;
        }
        try {
            resendActivationUseCase.execute(
                    new ResendActivationCommand(form.getEmail(), clientOriginResolver.resolve(request)));
        } catch (AuthApiClient.RateLimitedException
                 | AuthApiClient.InvalidRequestException
                 | AuthApiClient.ServiceUnavailableException e) {
            AuthFlowErrors.apply(e, model, response);
            return VIEW;
        }
        return "redirect:/registro/pendiente";
    }
}
