package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException;
import com.menta.bff.application.usecase.ListManagedVirtualCoursesUseCase;
import com.menta.bff.application.usecase.ManagedVirtualCoursesView;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Virtual courses management (#367): the read-only list, {@code GET /gestion/cursos-virtuales}. Access is
 * restricted to {@code ADMIN} and {@code INSTRUCTOR} in {@code BffSecurityConfig}; the Virtual API decides which
 * courses the caller sees (all for an administrator, the own ones for an instructor) and the BFF shows them as
 * they come, without filtering.
 *
 * <p>When the API fails or refuses, the page is rendered without rows and a notice, with a 503; the API's body
 * never reaches the browser. The page is never cached.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ManagementVirtualCoursesController {

    static final String UNAVAILABLE =
            "No pudimos cargar los cursos virtuales en este momento. Intentá nuevamente en unos minutos.";

    private static final String LIST_PATH = "/gestion/cursos-virtuales";
    private static final String LIST_VIEW = "gestion/cursos-virtuales/list";

    private final ListManagedVirtualCoursesUseCase listManagedVirtualCoursesUseCase;

    @GetMapping(LIST_PATH)
    public String list(HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        ManagedVirtualCoursesView view;
        try {
            view = listManagedVirtualCoursesUseCase.execute(
                    (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE));
        } catch (VirtualCourseManagementException | ServiceUnavailableException e) {
            log.warn("Virtual courses unavailable, rendering the screen without them: {}", e.getMessage());
            view = ManagedVirtualCoursesView.unavailableView();
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        model.addAttribute("courses", view);
        model.addAttribute("unavailableMessage", UNAVAILABLE);
        return LIST_VIEW;
    }
}
