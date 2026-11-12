package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.UserRole;
import com.menta.bff.application.usecase.ChangeUserRoleResult;
import com.menta.bff.application.usecase.ChangeUserRoleUseCase;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Administrator's role change, {@code POST /gestion/usuarios/{id}/rol} (#378, part 5b-2b), submitted by the form
 * of each row of the users list. Access is restricted to {@code ADMIN} in {@code BffSecurityConfig}; the Auth API
 * makes the real decision with the session's access token.
 *
 * <p>Every answer is Post/Redirect/Get to the users list, where the outcome is shown once from a flash
 * attribute: the confirmation in a status region, each failure in an alert. The list comes back with the filters
 * and the page the administrator was on. The redirect is never a URL from the client: it is always the fixed
 * list path with at most the three list parameters, re-validated and encoded here. The API's answer (its body,
 * its code) never reaches the browser, and nothing about the account travels in a URL. The role change closes
 * every session of the account, so when it is the administrator's own the session is ended here and the
 * administrator goes to the login with a notice, instead of staying in a session the API already revoked.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class ManagementUserRoleController {

    static final String CHANGED_MESSAGE =
            "Cambiamos el rol de %s a %s. Se cerraron sus sesiones: tendrá que iniciar sesión de nuevo.";
    static final String LAST_ADMIN = "No se puede quitar el rol de administrador al único administrador activo.";
    static final String NOT_FOUND = "No encontramos esa cuenta.";
    static final String INVALID = "Revisá los datos e intentá de nuevo.";
    static final String UNAVAILABLE = "No pudimos cambiar el rol. Probá de nuevo en unos minutos.";

    private static final String LIST_PATH = "/gestion/usuarios";
    private static final String OWN_ROLE_CHANGED_REDIRECT = "redirect:/login?roleChanged";
    /** The search box allows no more; a longer text did not come from it. */
    private static final int MAX_TEXT_LENGTH = 100;

    private final ChangeUserRoleUseCase changeUserRoleUseCase;

    @PostMapping("/gestion/usuarios/{id}/rol")
    public String changeRole(
            @PathVariable String id,
            @RequestParam(required = false) String role,
            @RequestParam(name = "filterRole", required = false) String filterRole,
            @RequestParam(name = "q", required = false) String text,
            @RequestParam(required = false) String page,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String accessToken = (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
        ChangeUserRoleResult result = changeUserRoleUseCase.execute(accessToken, id, role);
        if (result.outcome() == ChangeUserRoleResult.Outcome.CHANGED_OWN_ROLE) {
            new SecurityContextLogoutHandler().logout(request, response, authentication);
            return OWN_ROLE_CHANGED_REDIRECT;
        }
        if (result.outcome() == ChangeUserRoleResult.Outcome.CHANGED) {
            redirectAttributes.addFlashAttribute(
                    "roleChanged", CHANGED_MESSAGE.formatted(result.displayName(), result.role().getLabel()));
        } else {
            redirectAttributes.addFlashAttribute("roleChangeError", errorMessage(result.outcome()));
        }
        return "redirect:" + listUrl(filterRole, text, page);
    }

    private static String errorMessage(ChangeUserRoleResult.Outcome outcome) {
        return switch (outcome) {
            case LAST_ADMIN -> LAST_ADMIN;
            case USER_NOT_FOUND -> NOT_FOUND;
            case INVALID -> INVALID;
            default -> UNAVAILABLE;
        };
    }

    /**
     * The list's address with the state the administrator was in. The values come from hidden fields, so they
     * are validated like the list does and are expanded as URI variables, which are encoded strictly: whatever
     * the client sent can neither add a parameter or a fragment nor change the path.
     */
    private static String listUrl(String filterRole, String text, String page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(LIST_PATH);
        Map<String, Object> variables = new HashMap<>();
        UserRole role = parseRole(filterRole);
        if (role != null) {
            builder.queryParam("role", "{role}");
            variables.put("role", role.name());
        }
        String cleanText = text == null ? "" : text.strip();
        if (!cleanText.isEmpty() && cleanText.length() <= MAX_TEXT_LENGTH) {
            builder.queryParam("q", "{q}");
            variables.put("q", cleanText);
        }
        Integer pageIndex = parsePage(page);
        if (pageIndex != null) {
            builder.queryParam("page", "{page}");
            variables.put("page", pageIndex);
        }
        return builder.encode().build(variables).toString();
    }

    private static UserRole parseRole(String value) {
        try {
            return value == null ? null : UserRole.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer parsePage(String value) {
        try {
            int index = value == null ? -1 : Integer.parseInt(value);
            return index < 0 ? null : index;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
