package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.RegisterCommand;
import com.menta.bff.application.port.out.AuthApiClient;
import com.menta.bff.application.usecase.RegisterAccountUseCase;
import com.menta.bff.infrastructure.security.ClientOriginResolver;
import com.menta.bff.infrastructure.web.dto.RegisterForm;
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
 * Public account registration (#359).
 *
 * <p>A successful submission always lands on the same "check your email" screen
 * (Post/Redirect/Get): the Auth API answers 202 whether the email was new or
 * already registered, and this controller adds nothing that could tell them apart.
 * The email is never put in the redirect.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class RegistrationController {

    private final RegisterAccountUseCase registerAccountUseCase;
    private final ClientOriginResolver clientOriginResolver;

    @GetMapping("/registro")
    public String form(Model model) {
        model.addAttribute("form", new RegisterForm());
        return "register";
    }

    @PostMapping("/registro")
    public String submit(
            @Valid @ModelAttribute("form") RegisterForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            return "register";
        }
        try {
            registerAccountUseCase.execute(RegisterCommand.student(
                    form.getEmail(), form.getPassword(), form.getFirstName(), form.getLastName(),
                    form.normalizedPhone(), form.parsedBirthDate(), clientOriginResolver.resolve(request)));
        } catch (AuthApiClient.RateLimitedException
                 | AuthApiClient.InvalidRequestException
                 | AuthApiClient.ServiceUnavailableException e) {
            AuthFlowErrors.apply(e, model, response);
            return "register";
        }
        return "redirect:/registro/pendiente";
    }

    @GetMapping("/registro/pendiente")
    public String pending() {
        return "check-email";
    }
}
