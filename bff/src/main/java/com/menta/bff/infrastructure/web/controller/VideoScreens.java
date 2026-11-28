package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.VideoManagementException.Code;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;

/**
 * What the video management screens (#842) share: the access token, the notice page and how a refusal of the API is
 * worded from its {@code code}. The API's {@code detail} and body never reach the browser.
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
final class VideoScreens {

    static final String LIST_PATH = "/gestion/videos";
    static final String PROBLEM_VIEW = "gestion/videos/problem";
    static final String NOT_OWNED = "No podés gestionar este video.";
    static final String ACCESS_DENIED = "No podés gestionar los videos.";
    static final String NOT_FOUND = "No encontramos el video.";
    static final String LIBRARY_DOWN =
            "La biblioteca de videos no está disponible en este momento. Intentá de nuevo en unos minutos.";
    static final String SAVED_ATTRIBUTE = "videoSaved";

    private static final int MAX_TITLE_HINT = 255;

    private VideoScreens() {
    }

    static String accessToken(HttpServletRequest request) {
        return (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
    }

    /** The title that came in a link, trimmed and cut: only a hint to prefill or name the page, never trusted. */
    static String titleHint(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String stripped = title.strip();
        return stripped.length() > MAX_TITLE_HINT ? stripped.substring(0, MAX_TITLE_HINT) : stripped;
    }

    /**
     * The notice page for the refusals every video screen words alike: someone else's video, a role that cannot
     * manage videos, a video that does not exist and a library that does not answer.
     *
     * @return the view, or empty when the code is not one of those and the screen words it itself
     */
    static Optional<String> refusalPage(Code code, Model model, HttpServletResponse response) {
        return switch (code) {
            case VIDEO_NOT_OWNED -> Optional.of(
                    problem(model, response, HttpStatus.FORBIDDEN, "Sin permiso", NOT_OWNED));
            case ACCESS_DENIED -> Optional.of(
                    problem(model, response, HttpStatus.FORBIDDEN, "Sin permiso", ACCESS_DENIED));
            case VIDEO_NOT_FOUND -> Optional.of(notFound(model, response));
            case VIDEO_LIBRARY_UNAVAILABLE -> Optional.of(
                    problem(model, response, HttpStatus.SERVICE_UNAVAILABLE, "Videos", LIBRARY_DOWN));
            default -> Optional.empty();
        };
    }

    static String notFound(Model model, HttpServletResponse response) {
        return problem(model, response, HttpStatus.NOT_FOUND, "Video no encontrado", NOT_FOUND);
    }

    static String problem(
            Model model, HttpServletResponse response, HttpStatus status, String title, String message) {
        response.setStatus(status.value());
        model.addAttribute("problemTitle", title);
        model.addAttribute("problemMessage", message);
        return PROBLEM_VIEW;
    }
}
