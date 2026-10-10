package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.RequestPasswordResetCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.usecase.RequestPasswordResetUseCase;
import com.menta.bff.infrastructure.security.ClientOriginResolver;
import com.menta.bff.infrastructure.web.dto.RequestPasswordResetForm;
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
 * Password recovery request from the web (#360).
 *
 * <p>{@code /recuperar-contrasena} asks for the email and always ends on the same
 * confirmation screen (Post/Redirect/Get), whatever the account, with no email in the URL.
 * The email is a credential-adjacent datum: it is never logged or echoed in a message.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class PasswordResetController {

    private static final String REQUEST_VIEW = "request-password-reset";

    private final RequestPasswordResetUseCase requestPasswordResetUseCase;
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
}
