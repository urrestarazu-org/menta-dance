package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException.Code;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.usecase.GetCourseSessionRosterUseCase;
import com.menta.bff.application.usecase.SessionRosterView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * The students of one session of a physical course (#368):
 * {@code GET /gestion/cursos-presenciales/{courseId}/sesiones/{sessionId}}, with a search by name in {@code ?q=}.
 * It is read only: it shows who has a spot and whether they already checked in, and has no form that changes
 * anything (checking in belongs to the reception screens). Access is restricted to {@code ADMIN} and
 * {@code INSTRUCTOR} in {@code BffSecurityConfig}.
 *
 * <p>The roster endpoint of the API does not limit an instructor to their own courses, so the course in the URL
 * decides: {@link GetCourseSessionRosterUseCase} looks the session up in that course first, which the API refuses
 * for a course that is not the instructor's, and refuses a roster of another course. An id that is not a UUID,
 * a session that is not in the course and a course that does not exist are a 404 notice; a course that is not the
 * instructor's, a 403 notice; an outage of the API, a 503 notice. The API's body never reaches the browser. The
 * pages hold the students' names, so they are never cached; the search text is only echoed, always escaped by the
 * template.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ManagementSessionRosterController {

    static final String LOAD_FAILED =
            "No pudimos cargar los alumnos de la sesión en este momento. Intentá nuevamente en unos minutos.";

    private static final String ROSTER = ManagementCourseSessionsController.BASE + "/{courseId}/sesiones/{sessionId}";
    private static final String ROSTER_VIEW = "gestion/cursos/alumnos";
    private static final String PROBLEM_VIEW = "gestion/cursos/problem";

    private final GetCourseSessionRosterUseCase getCourseSessionRosterUseCase;

    @GetMapping(ROSTER)
    public String roster(
            @PathVariable String courseId,
            @PathVariable String sessionId,
            @RequestParam(name = "q", required = false) String text,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            SessionRosterView roster = getCourseSessionRosterUseCase.execute(
                    ManagementCourseSessionsController.accessToken(request), courseId, sessionId, text);
            model.addAttribute("roster", roster);
            model.addAttribute("courseId", roster.courseId());
            model.addAttribute("sessionId", sessionId);
            return ROSTER_VIEW;
        } catch (CourseManagementException e) {
            return problemOf(e.getCode(), courseId, model, response);
        } catch (ServiceUnavailableException e) {
            log.warn("Session roster unavailable, rendering the notice: {}", e.getMessage());
            return problemOf(null, courseId, model, response);
        }
    }

    /** Fills the notice page for a course or session whose students cannot be shown. */
    private static String problemOf(Code code, String courseId, Model model, HttpServletResponse response) {
        if (code == Code.COURSE_NOT_OWNED) {
            ManagementCourseSessionsController.problem(model, response, HttpStatus.FORBIDDEN, "Sin permiso",
                    ManagementCourseController.NOT_OWNED);
        } else if (code == Code.COURSE_NOT_FOUND) {
            ManagementCourseSessionsController.problem(model, response, HttpStatus.NOT_FOUND, "Curso no encontrado",
                    ManagementCourseController.NOT_FOUND);
        } else if (code == Code.SESSION_NOT_FOUND) {
            ManagementCourseSessionsController.problem(model, response, HttpStatus.NOT_FOUND, "Sesión no encontrada",
                    ManagementSessionChangesController.SESSION_NOT_FOUND);
            // The course is known to exist here, and the template encodes the id as a path segment.
            model.addAttribute("problemCourseId", courseId);
        } else {
            ManagementCourseSessionsController.problem(model, response, HttpStatus.SERVICE_UNAVAILABLE,
                    "Alumnos de la sesión", LOAD_FAILED);
        }
        return PROBLEM_VIEW;
    }
}
