package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.VideoLesson;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.VideoManagementException;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.VideoManagementException.Code;
import com.menta.bff.application.usecase.DeleteVideoUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Videos management (#842): deleting a video, administrator only, as a confirmation page
 * ({@code GET /gestion/videos/{videoId}/borrar}) and the POST that confirms it. The security rules refuse every other
 * role before this controller runs; the Virtual API refuses again. The BFF does not repeat the API's rule that a video
 * in use cannot be deleted: when the API answers {@code 409 VIDEO_IN_USE} the page lists the lessons that use the
 * video, each linked to its edit screen, and offers no confirmation. Success is Post/Redirect/Get to the list with a
 * one-time notice. The API's body never reaches the browser. Pages are never cached.
 *
 * <p>The API has no read of a single video, so the page names it with the title that the list put in the link
 * ({@code ?titulo=}, carried by a hidden field when the page is shown again after a refusal): it is only a hint,
 * escaped by the template.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ManagementVideoDeleteController {

    static final String DELETED = "Video borrado.";
    static final String ACTION_FAILED = "No pudimos completar la acción. Intentá de nuevo.";
    static final String IN_USE =
            "No se puede borrar el video porque está en uso en estas lecciones. "
                    + "Cambiá el video de esas lecciones antes de borrarlo.";
    static final String IN_USE_UNKNOWN = "No se puede borrar el video porque está en uso en una lección. "
            + "Cambiá el video de esa lección antes de borrarlo.";

    private static final String PATH = VideoScreens.LIST_PATH + "/{videoId}/borrar";
    private static final String CONFIRM_VIEW = "gestion/videos/borrar";
    private static final Pattern SIMPLE_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private final DeleteVideoUseCase deleteVideoUseCase;

    @GetMapping(PATH)
    public String confirmation(
            @PathVariable String videoId, @RequestParam(name = "titulo", required = false) String title,
            HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (!SIMPLE_ID.matcher(videoId).matches()) {
            return VideoScreens.notFound(model, response);
        }
        return page(videoId, title, null, false, List.of(), model);
    }

    @PostMapping(PATH)
    public String delete(
            @PathVariable String videoId, @RequestParam(name = "titulo", required = false) String title,
            HttpServletRequest request, HttpServletResponse response, Model model,
            RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            deleteVideoUseCase.execute(VideoScreens.accessToken(request), videoId);
        } catch (VideoManagementException e) {
            return refused(e, videoId, title, model, response);
        } catch (ServiceUnavailableException e) {
            log.warn("Video library unavailable while deleting: {}", e.getMessage());
            return failed(ACTION_FAILED, HttpStatus.SERVICE_UNAVAILABLE, false, videoId, title, List.of(), model,
                    response);
        }
        redirectAttributes.addFlashAttribute(VideoScreens.SAVED_ATTRIBUTE, DELETED);
        return "redirect:" + VideoScreens.LIST_PATH;
    }

    private String refused(
            VideoManagementException e, String videoId, String title, Model model, HttpServletResponse response) {
        if (e.getCode() == Code.VIDEO_IN_USE) {
            return failed(e.getLessons().isEmpty() ? IN_USE_UNKNOWN : IN_USE, HttpStatus.CONFLICT, true, videoId,
                    title, e.getLessons(), model, response);
        }
        return VideoScreens.refusalPage(e.getCode(), model, response).orElseGet(() -> failed(ACTION_FAILED,
                HttpStatus.SERVICE_UNAVAILABLE, false, videoId, title, List.of(), model, response));
    }

    private String failed(String message, HttpStatus status, boolean inUse, String videoId, String title,
            List<VideoLesson> lessons, Model model, HttpServletResponse response) {
        response.setStatus(status.value());
        return page(videoId, title, message, inUse, lessons, model);
    }

    private static String page(
            String videoId, String title, String failure, boolean inUse, List<VideoLesson> lessons, Model model) {
        model.addAttribute("videoId", videoId);
        model.addAttribute("videoTitle", VideoScreens.titleHint(title));
        model.addAttribute("actionError", failure);
        model.addAttribute("lessons", lessons);
        model.addAttribute("inUse", inUse);
        return CONFIRM_VIEW;
    }
}
