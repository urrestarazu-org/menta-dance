package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.UpdateSessionCommand;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException.Code;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.usecase.GetCourseSessionUseCase;
import com.menta.bff.application.usecase.ManagedSessionView;
import com.menta.bff.application.usecase.UpdateCourseSessionUseCase;
import com.menta.bff.infrastructure.web.dto.RescheduleForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The changes to one session of a physical course (#368): reschedule,
 * {@code /gestion/cursos-presenciales/{courseId}/sesiones/{sessionId}/reprogramar}, and cancel, {@code .../cancelar}.
 * Both are confirmation pages (a page, not a modal and not script): the {@code GET} shows the session as it is and
 * the form, the {@code POST} makes the change. Access is restricted to {@code ADMIN} and {@code INSTRUCTOR} in
 * {@code BffSecurityConfig}; the Physical API makes the real decision with the session's access token.
 *
 * <p>The API has no read-one endpoint for a session, so every request first looks the session up among the ones of
 * the course in the URL ({@link GetCourseSessionUseCase}): a session that is not in that course, or an id that is
 * not a UUID, is a notice page, and the change is sent with the id the API listed. The page warns when the session
 * already has spots taken (confirmed or held) and says nothing about what happens to them, because the API does
 * not say. A session that is already cancelled shows a notice instead of the form. A refusal of the API is worded
 * here from its {@code code}: a field error when it names a field, a general error otherwise, a notice page for a
 * course or session that is not found or not owned. Success is Post/Redirect/Get to the sessions list with a flash
 * confirmation. The API's body never reaches the browser. Pages are never cached.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ManagementSessionChangesController {

    static final String RESCHEDULED_MESSAGE = "Reprogramamos la sesión.";
    static final String CANCELLED_MESSAGE = "Cancelamos la sesión.";
    static final String RESCHEDULE_OCCURRED = "No se puede reprogramar una sesión que ya ocurrió.";
    static final String CANCEL_OCCURRED = "No se puede cancelar una sesión que ya ocurrió.";
    static final String RESCHEDULE_REFUSED = "No pudimos reprogramar la sesión. Revisá los datos e intentá de nuevo.";
    static final String RESCHEDULE_UNAVAILABLE = "No pudimos reprogramar la sesión. Probá de nuevo en unos minutos.";
    static final String CANCEL_REFUSED = "No pudimos cancelar la sesión. Intentá de nuevo.";
    static final String CANCEL_UNAVAILABLE = "No pudimos cancelar la sesión. Probá de nuevo en unos minutos.";
    static final String SESSION_NOT_FOUND = "No encontramos esa sesión.";
    static final String LOAD_FAILED =
            "No pudimos cargar la sesión en este momento. Intentá nuevamente en unos minutos.";

    private static final String BASE = ManagementCourseSessionsController.BASE;
    private static final String RESCHEDULE = BASE + "/{courseId}/sesiones/{sessionId}/reprogramar";
    private static final String CANCEL = BASE + "/{courseId}/sesiones/{sessionId}/cancelar";
    private static final String RESCHEDULE_VIEW = "gestion/cursos/reprogramar";
    private static final String CANCEL_VIEW = "gestion/cursos/cancelar";
    private static final String PROBLEM_VIEW = "gestion/cursos/problem";

    private final GetCourseSessionUseCase getCourseSessionUseCase;
    private final UpdateCourseSessionUseCase updateCourseSessionUseCase;

    // ---- reschedule ----

    @GetMapping(RESCHEDULE)
    public String rescheduleForm(
            @PathVariable String courseId,
            @PathVariable String sessionId,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        Optional<ManagedSessionView> target = load(courseId, sessionId, request, model, response);
        target.ifPresent(found -> model.addAttribute("form", RescheduleForm.from(found)));
        return target.isPresent() ? RESCHEDULE_VIEW : PROBLEM_VIEW;
    }

    @PostMapping(RESCHEDULE)
    public String reschedule(
            @PathVariable String courseId,
            @PathVariable String sessionId,
            @Valid @ModelAttribute("form") RescheduleForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        Optional<ManagedSessionView> loaded = load(courseId, sessionId, request, model, response);
        if (loaded.isEmpty()) {
            return PROBLEM_VIEW;
        }
        ManagedSessionView target = loaded.get();
        if (target.cancelled()) {
            response.setStatus(HttpStatus.CONFLICT.value());
            return RESCHEDULE_VIEW;
        }
        if (bindingResult.hasErrors()) {
            return RESCHEDULE_VIEW;
        }
        try {
            updateCourseSessionUseCase.execute(ManagementCourseSessionsController.accessToken(request),
                    target.sessionId(), form.toCommand());
        } catch (CourseManagementException e) {
            return reportRescheduleRefusal(e.getCode(), target, bindingResult, model, response);
        } catch (ServiceUnavailableException e) {
            log.warn("Session reschedule unavailable: {}", e.getMessage());
            reportGeneral("formError", RESCHEDULE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, model, response);
            return RESCHEDULE_VIEW;
        }
        redirectAttributes.addFlashAttribute("sessionsSaved", RESCHEDULED_MESSAGE);
        return ManagementCourseSessionsController.backToSessions(target.courseId());
    }

    // ---- cancel ----

    @GetMapping(CANCEL)
    public String cancelForm(
            @PathVariable String courseId,
            @PathVariable String sessionId,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return load(courseId, sessionId, request, model, response).isPresent() ? CANCEL_VIEW : PROBLEM_VIEW;
    }

    @PostMapping(CANCEL)
    public String cancel(
            @PathVariable String courseId,
            @PathVariable String sessionId,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        Optional<ManagedSessionView> loaded = load(courseId, sessionId, request, model, response);
        if (loaded.isEmpty()) {
            return PROBLEM_VIEW;
        }
        ManagedSessionView target = loaded.get();
        if (target.cancelled()) {
            response.setStatus(HttpStatus.CONFLICT.value());
            return CANCEL_VIEW;
        }
        try {
            updateCourseSessionUseCase.execute(ManagementCourseSessionsController.accessToken(request),
                    target.sessionId(), new UpdateSessionCommand(null, null, null, null, "CANCELLED"));
        } catch (CourseManagementException e) {
            return reportCancelRefusal(e.getCode(), target, model, response);
        } catch (ServiceUnavailableException e) {
            log.warn("Session cancellation unavailable: {}", e.getMessage());
            reportGeneral("cancelError", CANCEL_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, model, response);
            return CANCEL_VIEW;
        }
        redirectAttributes.addFlashAttribute("sessionsSaved", CANCELLED_MESSAGE);
        return ManagementCourseSessionsController.backToSessions(target.courseId());
    }

    // ---- helpers ----

    /** Reads the session of a confirmation page into the model; when it cannot, fills the notice page and status. */
    private Optional<ManagedSessionView> load(
            String courseId, String sessionId, HttpServletRequest request, Model model,
            HttpServletResponse response) {
        try {
            ManagedSessionView found = getCourseSessionUseCase.execute(
                    ManagementCourseSessionsController.accessToken(request), courseId, sessionId);
            model.addAttribute("sessionView", found);
            model.addAttribute("courseId", found.courseId());
            return Optional.of(found);
        } catch (CourseManagementException e) {
            problemOf(e.getCode(), courseId, model, response);
        } catch (ServiceUnavailableException e) {
            log.warn("Session unavailable, rendering the notice: {}", e.getMessage());
            problemOf(null, courseId, model, response);
        }
        return Optional.empty();
    }

    /** Fills the notice page for a course or session that cannot be shown. */
    private static void problemOf(Code code, String courseId, Model model, HttpServletResponse response) {
        if (code == Code.COURSE_NOT_OWNED) {
            ManagementCourseSessionsController.problem(model, response, HttpStatus.FORBIDDEN, "Sin permiso",
                    ManagementCourseController.NOT_OWNED);
        } else if (code == Code.COURSE_NOT_FOUND) {
            ManagementCourseSessionsController.problem(model, response, HttpStatus.NOT_FOUND, "Curso no encontrado",
                    ManagementCourseController.NOT_FOUND);
        } else if (code == Code.SESSION_NOT_FOUND) {
            ManagementCourseSessionsController.problem(model, response, HttpStatus.NOT_FOUND, "Sesión no encontrada",
                    SESSION_NOT_FOUND);
            // The course is known to exist here, and the template encodes the id as a path segment.
            model.addAttribute("problemCourseId", courseId);
        } else {
            ManagementCourseSessionsController.problem(model, response, HttpStatus.SERVICE_UNAVAILABLE,
                    "Sesiones del curso", LOAD_FAILED);
        }
    }

    /** Words a refusal of the API to a reschedule by its code. Returns the view to render. */
    private String reportRescheduleRefusal(
            Code code, ManagedSessionView target, BindingResult bindingResult, Model model,
            HttpServletResponse response) {
        switch (code) {
            case SESSION_ALREADY_OCCURRED -> {
                bindingResult.rejectValue("localDate", "session.occurred", RESCHEDULE_OCCURRED);
                response.setStatus(HttpStatus.CONFLICT.value());
            }
            case CAPACITY_BELOW_ASSIGNED -> {
                reportGeneral("formError", RESCHEDULE_REFUSED, HttpStatus.CONFLICT, model, response);
            }
            case INVALID_REQUEST -> {
                reportGeneral("formError", RESCHEDULE_REFUSED, HttpStatus.BAD_REQUEST, model, response);
            }
            case COURSE_NOT_OWNED, COURSE_NOT_FOUND, SESSION_NOT_FOUND -> {
                problemOf(code, target.courseId(), model, response);
                return PROBLEM_VIEW;
            }
            default -> {
                reportGeneral("formError", RESCHEDULE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, model, response);
            }
        }
        return RESCHEDULE_VIEW;
    }

    /** Words a refusal of the API to a cancellation by its code. Returns the view to render. */
    private String reportCancelRefusal(
            Code code, ManagedSessionView target, Model model, HttpServletResponse response) {
        switch (code) {
            case SESSION_ALREADY_OCCURRED -> {
                reportGeneral("cancelError", CANCEL_OCCURRED, HttpStatus.CONFLICT, model, response);
            }
            case CAPACITY_BELOW_ASSIGNED, INVALID_REQUEST -> {
                reportGeneral("cancelError", CANCEL_REFUSED, HttpStatus.BAD_REQUEST, model, response);
            }
            case COURSE_NOT_OWNED, COURSE_NOT_FOUND, SESSION_NOT_FOUND -> {
                problemOf(code, target.courseId(), model, response);
                return PROBLEM_VIEW;
            }
            default -> {
                reportGeneral("cancelError", CANCEL_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, model, response);
            }
        }
        return CANCEL_VIEW;
    }

    /** A general error above the form of the page. */
    private static void reportGeneral(
            String attribute, String message, HttpStatus status, Model model, HttpServletResponse response) {
        model.addAttribute(attribute, message);
        response.setStatus(status.value());
    }
}
