package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException.Code;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.usecase.CreateCourseSessionUseCase;
import com.menta.bff.application.usecase.DescribeSessionConflictsUseCase;
import com.menta.bff.application.usecase.GetManagedCourseUseCase;
import com.menta.bff.application.usecase.ListCourseSessionsUseCase;
import com.menta.bff.application.usecase.ManagedCourseView;
import com.menta.bff.application.usecase.ManagedSessionView;
import com.menta.bff.application.usecase.ResolveSessionWindowUseCase;
import com.menta.bff.application.usecase.SessionWindow;
import com.menta.bff.infrastructure.web.dto.SessionForm;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The sessions of a physical course (#368): {@code GET /gestion/cursos-presenciales/{courseId}/sesiones} lists
 * them, with the form that creates one on the same page, so the user sees what already exists next to what they
 * add. Access is restricted to {@code ADMIN} and {@code INSTRUCTOR} in {@code BffSecurityConfig}; the Physical
 * API makes the real decision with the session's access token and scopes an instructor to their own courses.
 *
 * <p>The list shows a window of days (today to 90 days ahead unless the {@code desde} and {@code hasta} query
 * parameters say otherwise; a value that cannot be used gives the default window and a hint, never an error).
 * Whether a course already has a session at a moment is the API's decision: the BFF never checks it, it words
 * the {@code SESSION_ALREADY_EXISTS} refusal next to the field with the dates the API reports, in the academy's
 * zone. A successful submission is Post/Redirect/Get to the list with a flash confirmation; nothing travels in
 * the redirect URL but the course's id, taken from the API's answer. A course that is not found or not owned
 * has a notice page. The API's body never reaches the browser. Pages are never cached.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ManagementCourseSessionsController {

    static final String CREATED_MESSAGE = "Creamos la sesión.";
    static final String INVALID = "Revisá los datos e intentá de nuevo.";
    static final String CREATE_UNAVAILABLE = "No pudimos crear la sesión. Probá de nuevo en unos minutos.";
    static final String WINDOW_FALLBACK = "No pudimos usar esas fechas. Mostramos los próximos 90 días.";
    static final String LOAD_FAILED =
            "No pudimos cargar las sesiones en este momento. Intentá nuevamente en unos minutos.";
    static final String ALREADY_EXISTS = "Ya existe una sesión de este curso el %s.";
    static final String ALREADY_EXISTS_UNKNOWN = "Ya existe una sesión de este curso en ese horario.";

    private static final String BASE = "/gestion/cursos-presenciales";
    private static final String SESSIONS = BASE + "/{courseId}/sesiones";
    private static final String PAGE_VIEW = "gestion/cursos/sesiones";
    private static final String PROBLEM_VIEW = "gestion/cursos/problem";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final GetManagedCourseUseCase getManagedCourseUseCase;
    private final ListCourseSessionsUseCase listCourseSessionsUseCase;
    private final ResolveSessionWindowUseCase resolveSessionWindowUseCase;
    private final CreateCourseSessionUseCase createCourseSessionUseCase;
    private final DescribeSessionConflictsUseCase describeSessionConflictsUseCase;

    // ---- list ----

    @GetMapping(SESSIONS)
    public String list(
            @PathVariable String courseId,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        SessionWindow window = resolveSessionWindowUseCase.execute(desde, hasta);
        model.addAttribute("windowFallback", !window.requested());
        return page(courseId, window, accessToken(request), model, response);
    }

    // ---- create one ----

    @PostMapping(SESSIONS)
    public String create(
            @PathVariable String courseId,
            @Valid @ModelAttribute("form") SessionForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String accessToken = accessToken(request);
        if (bindingResult.hasErrors()) {
            return page(courseId, resolveSessionWindowUseCase.execute(null, null), accessToken, model, response);
        }
        ManagedSessionView created;
        try {
            created = createCourseSessionUseCase.execute(accessToken, courseId, form.toCommand());
        } catch (CourseManagementException e) {
            return reportCreateRefusal(e, courseId, accessToken, bindingResult, model, response);
        } catch (ServiceUnavailableException e) {
            log.warn("Session creation unavailable: {}", e.getMessage());
            return reportFormError(CREATE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, courseId, accessToken, model,
                    response);
        }
        redirectAttributes.addFlashAttribute("sessionsSaved", CREATED_MESSAGE);
        return backToSessions(created.courseId());
    }

    // ---- helpers ----

    private static String accessToken(HttpServletRequest request) {
        return (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
    }

    static String backToSessions(String courseId) {
        return "redirect:" + BASE + "/" + courseId + "/sesiones";
    }

    /**
     * Fills the model of the sessions page: the course, its sessions in the window and the forms (an empty one
     * where the request brought none). The sessions are read first, so an instructor who does not own the course
     * is told so instead of "not found". When it cannot, fills the notice page and its status.
     */
    private String page(String courseId, SessionWindow window, String accessToken, Model model,
            HttpServletResponse response) {
        try {
            List<ManagedSessionView> sessions = listCourseSessionsUseCase.execute(
                    accessToken, courseId, window.from(), window.to());
            ManagedCourseView course = getManagedCourseUseCase.execute(accessToken, courseId);
            model.addAttribute("course", course);
            model.addAttribute("sessions", sessions);
            model.addAttribute("windowFrom", window.from().toString());
            model.addAttribute("windowTo", window.to().toString());
            model.addAttribute("windowLabel", "del " + DAY.format(window.from()) + " al " + DAY.format(window.to()));
            if (!model.containsAttribute("form")) {
                model.addAttribute("form", SessionForm.forCourse(course));
            }
            return PAGE_VIEW;
        } catch (CourseManagementException e) {
            return problemOf(e.getCode(), model, response);
        } catch (ServiceUnavailableException e) {
            log.warn("Course sessions unavailable, rendering the notice: {}", e.getMessage());
            return problemOf(null, model, response);
        }
    }

    /** The notice page for a course that cannot be shown. */
    private static String problemOf(Code code, Model model, HttpServletResponse response) {
        if (code == Code.COURSE_NOT_OWNED) {
            problem(model, response, HttpStatus.FORBIDDEN, "Sin permiso", ManagementCourseController.NOT_OWNED);
        } else if (code == Code.COURSE_NOT_FOUND) {
            problem(model, response, HttpStatus.NOT_FOUND, "Curso no encontrado",
                    ManagementCourseController.NOT_FOUND);
        } else {
            problem(model, response, HttpStatus.SERVICE_UNAVAILABLE, "Sesiones del curso", LOAD_FAILED);
        }
        return PROBLEM_VIEW;
    }

    private static void problem(
            Model model, HttpServletResponse response, HttpStatus status, String title, String message) {
        response.setStatus(status.value());
        model.addAttribute("problemTitle", title);
        model.addAttribute("problemMessage", message);
    }

    /** Words a refusal of the API to a session creation by its code. Returns the view to render. */
    private String reportCreateRefusal(CourseManagementException e, String courseId, String accessToken,
            BindingResult bindingResult, Model model, HttpServletResponse response) {
        switch (e.getCode()) {
            case SESSION_ALREADY_EXISTS -> {
                List<String> dates = describeSessionConflictsUseCase.execute(e.getConflictingDates());
                String message = dates.isEmpty() ? ALREADY_EXISTS_UNKNOWN : ALREADY_EXISTS.formatted(dates.get(0));
                bindingResult.rejectValue("localDate", "session.alreadyExists", message);
                response.setStatus(HttpStatus.CONFLICT.value());
                return page(courseId, resolveSessionWindowUseCase.execute(null, null), accessToken, model, response);
            }
            case INVALID_REQUEST -> {
                return reportFormError(INVALID, HttpStatus.BAD_REQUEST, courseId, accessToken, model, response);
            }
            case COURSE_NOT_OWNED, COURSE_NOT_FOUND -> {
                return problemOf(e.getCode(), model, response);
            }
            default -> {
                return reportFormError(CREATE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, courseId, accessToken,
                        model, response);
            }
        }
    }

    /** Re-renders the page with a general error above the creation form. */
    private String reportFormError(String message, HttpStatus status, String courseId, String accessToken,
            Model model, HttpServletResponse response) {
        model.addAttribute("formError", message);
        String view = page(courseId, resolveSessionWindowUseCase.execute(null, null), accessToken, model, response);
        if (PAGE_VIEW.equals(view)) {
            response.setStatus(status.value());
        }
        return view;
    }
}
