package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.UserRole;
import com.menta.bff.application.usecase.CreateUserResult;
import com.menta.bff.application.usecase.CreateUserUseCase;
import com.menta.bff.infrastructure.web.dto.CreateUserForm;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Administrator's staff account creation, {@code GET /gestion/usuarios/nuevo} and
 * {@code POST /gestion/usuarios} (#378, part 5b-2a). Access is restricted to {@code ADMIN} in
 * {@code BffSecurityConfig}; the Auth API makes the real decision with the session's access token.
 *
 * <p>A successful submission is Post/Redirect/Get to the users list with a flash confirmation, so reloading
 * the list never submits again. Nothing about the account travels in the redirect URL. A failure comes back
 * to the form with what was typed: a local error or an email that already has an account as field errors, a
 * rejection by the API as a general error, an outage as a 503 notice. The API's answer (its body, its code)
 * never reaches the browser. Every response holds personal data of a third person, so none is cached.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class ManagementUserCreateController {

    static final String CREATED_MESSAGE =
            "Creamos la cuenta de %s. Le enviamos un correo para que la active y elija su contraseña.";
    static final String EMAIL_TAKEN = "Ya existe una cuenta con ese email.";
    static final String INVALID = "Revisá los datos e intentá de nuevo.";
    static final String UNAVAILABLE = "No pudimos crear la cuenta. Probá de nuevo en unos minutos.";

    private static final String FORM_VIEW = "gestion/usuarios/new";

    private final CreateUserUseCase createUserUseCase;

    @ModelAttribute("roles")
    List<UserRole> roles() {
        return UserRole.staffRoles();
    }

    @GetMapping("/gestion/usuarios/nuevo")
    public String form(HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        model.addAttribute("form", new CreateUserForm());
        return FORM_VIEW;
    }

    @PostMapping("/gestion/usuarios")
    public String create(
            @Valid @ModelAttribute("form") CreateUserForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }
        String accessToken = (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
        CreateUserResult result = createUserUseCase.execute(
                accessToken, form.getEmail(), form.getFirstName(), form.getLastName(), form.parsedRole());
        if (result.outcome() == CreateUserResult.Outcome.CREATED) {
            redirectAttributes.addFlashAttribute(
                    "accountCreated", CREATED_MESSAGE.formatted(result.displayName()));
            return "redirect:/gestion/usuarios";
        }
        reportFailure(result.outcome(), bindingResult, model, response);
        return FORM_VIEW;
    }

    private static void reportFailure(
            CreateUserResult.Outcome outcome, BindingResult bindingResult, Model model,
            HttpServletResponse response) {
        if (outcome == CreateUserResult.Outcome.EMAIL_ALREADY_REGISTERED) {
            bindingResult.rejectValue("email", "email.alreadyRegistered", EMAIL_TAKEN);
            response.setStatus(HttpStatus.CONFLICT.value());
        } else if (outcome == CreateUserResult.Outcome.INVALID) {
            model.addAttribute("formError", INVALID);
            response.setStatus(HttpStatus.BAD_REQUEST.value());
        } else {
            model.addAttribute("formError", UNAVAILABLE);
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
    }
}
