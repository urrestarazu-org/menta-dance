package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.UserRole;
import com.menta.bff.application.usecase.ListUsersUseCase;
import com.menta.bff.application.usecase.UsersListView;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Administrator's users list, {@code GET /gestion/usuarios} (#378, read-only). Access is restricted to
 * {@code ADMIN} in {@code BffSecurityConfig}; the Auth API makes the real decision with the session's access
 * token.
 *
 * <p>The page holds emails and names, so it is never cached. An outage of the Auth API degrades to the
 * page's own notice with status 503; upstream details never reach the browser. The filters travel in the
 * query string so a filtered page can be linked and reloaded; they are only echoed, always escaped by the
 * template.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class ManagementUsersController {

    private final ListUsersUseCase listUsersUseCase;

    @GetMapping("/gestion/usuarios")
    public String users(
            @RequestParam(required = false) String role,
            @RequestParam(name = "q", required = false) String text,
            @RequestParam(required = false) Integer page,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String accessToken = (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
        UsersListView view = listUsersUseCase.execute(accessToken, role, text, page);
        if (view.unavailable()) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        model.addAttribute("users", view);
        model.addAttribute("roles", UserRole.values());
        return "gestion/usuarios/list";
    }
}
