package com.menta.bff.infrastructure.web.controller;

import static com.menta.bff.infrastructure.web.controller.ManagementVirtualModuleController.accessToken;
import static com.menta.bff.infrastructure.web.controller.ManagementVirtualModuleController.loadFailed;
import static com.menta.bff.infrastructure.web.controller.ManagementVirtualModuleController.refused;
import static com.menta.bff.infrastructure.web.controller.ManagementVirtualModuleController.statusLabel;
import static com.menta.bff.infrastructure.web.controller.ManagementVirtualModuleController.unavailable;

import com.menta.bff.application.dto.VirtualCourseDetail;
import com.menta.bff.application.dto.VirtualCourseStatus;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException;
import com.menta.bff.application.usecase.DeleteVirtualCourseUseCase;
import com.menta.bff.application.usecase.GetManagedVirtualCourseUseCase;
import com.menta.bff.application.usecase.PublishVirtualCourseUseCase;
import com.menta.bff.application.usecase.UnpublishVirtualCourseUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Virtual courses management (#367): publish, unpublish and delete a course, each as a confirmation page
 * ({@code GET /gestion/cursos-virtuales/{courseId}/publicar|despublicar|borrar}, which reads the course to show its
 * title and state) and the POST that confirms it. The BFF does not repeat the API's rules: the page is shown for any
 * state, with a note when it is not the expected one, and the API decides when the action is confirmed. Success is
 * Post/Redirect/Get to the list with a flash; a refusal is worded from its {@code code}, and the API's body never
 * reaches the browser except the reason of {@code COURSE_NOT_PUBLISHABLE}, which the API words in Spanish for this.
 * Pages are never cached.
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class ManagementVirtualCourseStatusController {

    static final String ACTION_FAILED = "No pudimos completar la acción. Intentá de nuevo.";
    static final String HAS_PROGRESS = "No se puede borrar el curso porque ya tiene progreso de alumnos.";
    static final String IN_USE = "No se puede borrar el curso porque está incluido en un plan o en una suscripción.";
    static final String NOT_PUBLISHABLE = "El curso todavía no cumple las condiciones para publicarse.";

    private static final String BASE = "/gestion/cursos-virtuales/{courseId}/";
    private static final String CONFIRM_VIEW = "gestion/cursos-virtuales/confirmar";
    private static final Refusal FAILED = new Refusal(HttpStatus.SERVICE_UNAVAILABLE, ACTION_FAILED, false);

    /** What the confirmation page says about a refusal; {@code reviewStructure} adds the link to fix the course. */
    private record Refusal(HttpStatus status, String message, boolean reviewStructure) {
    }

    /** What each action says on its page; {@code notDraft} words {@code COURSE_NOT_DRAFT}, when it applies. */
    private enum Action {
        PUBLISH("publicar", "Publicar", "Va a quedar visible en el catálogo para los alumnos.", "Sí, publicar",
                "Curso publicado.", VirtualCourseStatus.DRAFT, null, "Solo se puede publicar un curso en borrador."),
        UNPUBLISH("despublicar", "Despublicar", "Va a dejar de verse en el catálogo y vuelve a borrador.",
                "Sí, despublicar", "Curso despublicado.", VirtualCourseStatus.PUBLISHED, null, null),
        DELETE("borrar", "Borrar", "Se borran también sus módulos y lecciones. Esta acción no se puede deshacer.",
                "Sí, borrar el curso", "Curso borrado.", VirtualCourseStatus.DRAFT,
                "Solo se pueden borrar cursos en borrador.",
                "Solo se puede borrar un curso en borrador. Despublicalo primero.");

        private final String path;
        private final String verb;
        private final String effect;
        private final String confirmLabel;
        private final String done;
        private final VirtualCourseStatus expected;
        private final String hint;
        private final String notDraft;

        Action(String path, String verb, String effect, String confirmLabel, String done,
                VirtualCourseStatus expected, String hint, String notDraft) {
            this.path = path;
            this.verb = verb;
            this.effect = effect;
            this.confirmLabel = confirmLabel;
            this.done = done;
            this.expected = expected;
            this.hint = hint;
            this.notDraft = notDraft;
        }
    }

    private final GetManagedVirtualCourseUseCase getUseCase;
    private final PublishVirtualCourseUseCase publishUseCase;
    private final UnpublishVirtualCourseUseCase unpublishUseCase;
    private final DeleteVirtualCourseUseCase deleteUseCase;

    @GetMapping(BASE + "publicar")
    public String publishPage(
            @PathVariable String courseId, HttpServletRequest request, HttpServletResponse response, Model model) {
        return show(Action.PUBLISH, courseId, request, response, model);
    }

    @GetMapping(BASE + "despublicar")
    public String unpublishPage(
            @PathVariable String courseId, HttpServletRequest request, HttpServletResponse response, Model model) {
        return show(Action.UNPUBLISH, courseId, request, response, model);
    }

    @GetMapping(BASE + "borrar")
    public String deletePage(
            @PathVariable String courseId, HttpServletRequest request, HttpServletResponse response, Model model) {
        return show(Action.DELETE, courseId, request, response, model);
    }

    @PostMapping(BASE + "publicar")
    public String publish(@PathVariable String courseId, HttpServletRequest request, HttpServletResponse response,
            Model model, RedirectAttributes redirectAttributes) {
        return confirm(Action.PUBLISH, courseId, request, response, model, redirectAttributes);
    }

    @PostMapping(BASE + "despublicar")
    public String unpublish(@PathVariable String courseId, HttpServletRequest request, HttpServletResponse response,
            Model model, RedirectAttributes redirectAttributes) {
        return confirm(Action.UNPUBLISH, courseId, request, response, model, redirectAttributes);
    }

    @PostMapping(BASE + "borrar")
    public String delete(@PathVariable String courseId, HttpServletRequest request, HttpServletResponse response,
            Model model, RedirectAttributes redirectAttributes) {
        return confirm(Action.DELETE, courseId, request, response, model, redirectAttributes);
    }

    private String show(
            Action action, String courseId, HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            return confirmation(action, getUseCase.execute(accessToken(request), courseId), null, false, model);
        } catch (VirtualCourseManagementException e) {
            return refused(e.getCode(), model, response, () -> loadFailed(model, response));
        } catch (ServiceUnavailableException e) {
            return unavailable(e, () -> loadFailed(model, response));
        }
    }

    private String confirm(Action action, String courseId, HttpServletRequest request, HttpServletResponse response,
            Model model, RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String accessToken = accessToken(request);
        try {
            switch (action) {
                case PUBLISH -> publishUseCase.execute(accessToken, courseId);
                case UNPUBLISH -> unpublishUseCase.execute(accessToken, courseId);
                default -> deleteUseCase.execute(accessToken, courseId);
            }
        } catch (VirtualCourseManagementException e) {
            return refused(e.getCode(), model, response,
                    () -> failed(action, refusal(action, e), courseId, accessToken, model, response));
        } catch (ServiceUnavailableException e) {
            return unavailable(e, () -> failed(action, FAILED, courseId, accessToken, model, response));
        }
        redirectAttributes.addFlashAttribute("virtualCourseSaved", action.done);
        return "redirect:/gestion/cursos-virtuales";
    }

    /** Shows the confirmation again with what the API refused, or a notice page if the course cannot be read now. */
    private String failed(Action action, Refusal refusal, String courseId, String accessToken, Model model,
            HttpServletResponse response) {
        response.setStatus(refusal.status().value());
        try {
            VirtualCourseDetail course = getUseCase.execute(accessToken, courseId);
            return confirmation(action, course, refusal.message(), refusal.reviewStructure(), model);
        } catch (VirtualCourseManagementException | ServiceUnavailableException e) {
            model.addAttribute("problemTitle", "Cursos virtuales");
            model.addAttribute("problemMessage", refusal.message());
            return "gestion/cursos-virtuales/problem";
        }
    }

    private String confirmation(
            Action action, VirtualCourseDetail course, String failure, boolean reviewStructure, Model model) {
        model.addAttribute("courseId", course.courseId());
        model.addAttribute("courseTitle", course.title());
        model.addAttribute("statusLabel", statusLabel(course.status()));
        model.addAttribute("statusNote", course.status() == action.expected ? null : mismatch(course.status()));
        model.addAttribute("confirmTitle", action.verb + " curso");
        model.addAttribute("verb", action.verb);
        model.addAttribute("effect", action.effect);
        model.addAttribute("hint", action.hint);
        model.addAttribute("confirmLabel", action.confirmLabel);
        model.addAttribute("actionPath", action.path);
        model.addAttribute("destructive", action == Action.DELETE);
        model.addAttribute("actionError", failure);
        model.addAttribute("reviewStructure", reviewStructure);
        return CONFIRM_VIEW;
    }

    private static Refusal refusal(Action action, VirtualCourseManagementException e) {
        return switch (e.getCode()) {
            case COURSE_NOT_PUBLISHABLE -> new Refusal(HttpStatus.BAD_REQUEST,
                    e.getPublishDetail() == null ? NOT_PUBLISHABLE : e.getPublishDetail(), true);
            case COURSE_NOT_DRAFT -> action.notDraft == null
                    ? FAILED : new Refusal(HttpStatus.CONFLICT, action.notDraft, false);
            // Only deleting is refused for progress; on any other action the code means nothing here.
            case COURSE_HAS_PROGRESS -> action == Action.DELETE
                    ? new Refusal(HttpStatus.CONFLICT, HAS_PROGRESS, false) : FAILED;
            // Same: only deleting is refused for Billing references.
            case COURSE_IN_USE -> action == Action.DELETE
                    ? new Refusal(HttpStatus.CONFLICT, IN_USE, false) : FAILED;
            default -> FAILED;
        };
    }

    private static String mismatch(VirtualCourseStatus status) {
        return switch (status) {
            case DRAFT -> "Este curso ya está en borrador.";
            case PUBLISHED -> "Este curso ya está publicado.";
            case ARCHIVED -> "Este curso está archivado.";
            case UNKNOWN -> "No conocemos el estado de este curso.";
        };
    }
}
