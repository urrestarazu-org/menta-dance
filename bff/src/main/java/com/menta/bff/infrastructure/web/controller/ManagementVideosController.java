package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.management.ManagementRoles;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.VideoManagementException;
import com.menta.bff.application.usecase.ListManagedVideosUseCase;
import com.menta.bff.application.usecase.VideosListView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Videos management (#842): the paginated list with a title search, {@code GET /gestion/videos}. Access is
 * restricted to {@code ADMIN} and {@code INSTRUCTOR} in {@code BffSecurityConfig}; the Virtual API decides which
 * videos the caller sees (the whole library for both roles since #861; for an instructor the {@code lessons} of each
 * video are only the ones of the own courses) and the BFF shows them as they come, including a video that a lesson
 * uses and the library no longer has. Only the administrator is offered the delete action, and the instructor is
 * offered the rename action only on a video that an own lesson uses; the API and the security rules refuse the rest
 * anyway.
 *
 * <p>When the API or the library fails, the page is rendered without rows and a notice, with a 503; the API's body
 * never reaches the browser. The page is never cached. The search and the page travel in the query string so a result
 * can be linked and reloaded; they are only echoed, always escaped by the template.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class ManagementVideosController {

    static final String UNAVAILABLE =
            "No pudimos cargar los videos en este momento. Intentá nuevamente en unos minutos.";
    static final String LIBRARY_DOWN =
            "La biblioteca de videos no está disponible en este momento. Intentá nuevamente en unos minutos.";

    private static final String LIST_VIEW = "gestion/videos/list";

    private final ListManagedVideosUseCase listManagedVideosUseCase;

    @GetMapping(VideoScreens.LIST_PATH)
    public String list(
            @RequestParam(name = "q", required = false) String search,
            @RequestParam(required = false) Integer page,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        VideosListView view;
        try {
            view = listManagedVideosUseCase.execute(VideoScreens.accessToken(request), search, page);
        } catch (VideoManagementException e) {
            return VideoScreens.refusalPage(e.getCode(), model, response)
                    .orElseGet(() -> VideoScreens.problem(model, response, HttpStatus.SERVICE_UNAVAILABLE,
                            "Videos", UNAVAILABLE));
        }
        if (view.unavailable()) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        model.addAttribute("videos", view);
        model.addAttribute("unavailableMessage", view.libraryDown() ? LIBRARY_DOWN : UNAVAILABLE);
        model.addAttribute("administrator", ManagementRoles.normalize(authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet())).contains("ADMIN"));
        return LIST_VIEW;
    }
}
