package com.menta.bff.infrastructure.web.controller;

import static com.menta.bff.infrastructure.web.controller.ManagementVirtualModuleController.COURSE;
import static com.menta.bff.infrastructure.web.controller.ManagementVirtualModuleController.accessToken;
import static com.menta.bff.infrastructure.web.controller.ManagementVirtualModuleController.done;
import static com.menta.bff.infrastructure.web.controller.ManagementVirtualModuleController.inOrder;
import static com.menta.bff.infrastructure.web.controller.ManagementVirtualModuleController.loadFailed;
import static com.menta.bff.infrastructure.web.controller.ManagementVirtualModuleController.problem;
import static com.menta.bff.infrastructure.web.controller.ManagementVirtualModuleController.unavailable;

import com.menta.bff.application.dto.VirtualCourseDetail.VirtualLessonDetail;
import com.menta.bff.application.dto.VirtualCourseDetail.VirtualModuleDetail;
import com.menta.bff.application.dto.VirtualLessonLocation;
import com.menta.bff.application.dto.VirtualLessonPosition;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException;
import com.menta.bff.application.usecase.DeleteVirtualLessonUseCase;
import com.menta.bff.application.usecase.GetManagedVirtualCourseUseCase;
import com.menta.bff.application.usecase.GetManagedVirtualLessonUseCase;
import com.menta.bff.application.usecase.SwapVirtualLessonsUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
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
 * Virtual courses management (#831): what the structure of a course offers on each lesson besides editing it, under
 * {@code /gestion/cursos-virtuales/{courseId}/modulos/{moduleId}/lecciones/{lessonId}}: the move up and down buttons
 * ({@code subir}, {@code bajar}) and the deletion ({@code borrar}: a confirmation page and the POST that confirms
 * it). For {@code ADMIN} and {@code INSTRUCTOR}; the Virtual API makes the real decision. Every write is
 * Post/Redirect/Get to the structure with a flash, a refusal is worded from the API's {@code code} (never its body,
 * by the module controller's helpers) and pages are never cached.
 *
 * <p>A move reads the course detail, takes the lesson and its neighbour in the module and swaps their {@code order}
 * (two updates, see {@link SwapVirtualLessonsUseCase}). At the edge, or when the lesson is gone, the API is not
 * asked. The confirmation of a deletion shows the title of the lesson, which the API cannot read on its own, so it
 * is found in the course detail too.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class ManagementVirtualLessonActionsController {

    static final String MOVED = "Lección movida.";
    static final String NOT_MOVED = "La lección ya está en esa posición.";
    static final String GONE = "Esa lección ya no existe. Revisá la estructura del curso.";
    static final String MOVE_FAILED = "No pudimos mover la lección. Intentá de nuevo.";
    static final String DELETED = "Lección borrada.";

    private static final String LESSON = COURSE + "/modulos/{moduleId}/lecciones/{lessonId}";
    private static final String VIRTUAL_COURSES = "Cursos virtuales";
    private static final String CONFIRM_VIEW = "gestion/cursos-virtuales/leccion-borrar";

    private final GetManagedVirtualCourseUseCase getUseCase;
    private final GetManagedVirtualLessonUseCase findUseCase;
    private final SwapVirtualLessonsUseCase swapUseCase;
    private final DeleteVirtualLessonUseCase deleteUseCase;

    // ---- move ----

    @PostMapping(LESSON + "/subir")
    public String moveUp(
            @PathVariable String courseId, @PathVariable String moduleId, @PathVariable String lessonId,
            HttpServletRequest request, HttpServletResponse response, Model model,
            RedirectAttributes redirectAttributes) {
        return move(courseId, moduleId, lessonId, -1, request, response, model, redirectAttributes);
    }

    @PostMapping(LESSON + "/bajar")
    public String moveDown(
            @PathVariable String courseId, @PathVariable String moduleId, @PathVariable String lessonId,
            HttpServletRequest request, HttpServletResponse response, Model model,
            RedirectAttributes redirectAttributes) {
        return move(courseId, moduleId, lessonId, 1, request, response, model, redirectAttributes);
    }

    /** Swaps the lesson with its neighbour {@code step} places away; at the edge or when gone the API is not asked. */
    private String move(
            String courseId, String moduleId, String lessonId, int step, HttpServletRequest request,
            HttpServletResponse response, Model model, RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String token = accessToken(request);
        try {
            List<VirtualLessonDetail> lessons = lessonsOf(courseId, moduleId, token);
            int from = indexOf(lessons, lessonId);
            int to = from + step;
            if (from < 0 || to < 0 || to >= lessons.size()) {
                return done(from < 0 ? GONE : NOT_MOVED, "info", redirectAttributes);
            }
            swapUseCase.execute(token, position(lessons.get(from)), position(lessons.get(to)));
        } catch (VirtualCourseManagementException e) {
            return ManagementVirtualLessonController.refused(e.getCode(), model, response,
                    () -> problem(model, response, HttpStatus.SERVICE_UNAVAILABLE, VIRTUAL_COURSES, MOVE_FAILED));
        } catch (ServiceUnavailableException e) {
            return unavailable(e,
                    () -> problem(model, response, HttpStatus.SERVICE_UNAVAILABLE, VIRTUAL_COURSES, MOVE_FAILED));
        }
        return done(MOVED, redirectAttributes);
    }

    /** The lessons of the module in order; a module that is not in the course has none. */
    private List<VirtualLessonDetail> lessonsOf(String courseId, String moduleId, String token) {
        return inOrder(getUseCase.execute(token, courseId)).stream()
                .filter(module -> module.moduleId().equals(moduleId)).findFirst()
                .map(VirtualModuleDetail::lessons).orElse(List.of());
    }

    private static int indexOf(List<VirtualLessonDetail> lessons, String lessonId) {
        for (int i = 0; i < lessons.size(); i++) {
            if (lessons.get(i).lessonId().equals(lessonId)) {
                return i;
            }
        }
        return -1;
    }

    private static VirtualLessonPosition position(VirtualLessonDetail lesson) {
        return new VirtualLessonPosition(lesson.lessonId(), lesson.order());
    }

    // ---- delete ----

    @GetMapping(LESSON + "/borrar")
    public String confirmation(
            @PathVariable String courseId, @PathVariable String moduleId, @PathVariable String lessonId,
            HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            VirtualLessonLocation found = findUseCase.execute(accessToken(request), courseId, moduleId, lessonId);
            return showConfirmation(found, null, model);
        } catch (VirtualCourseManagementException e) {
            return ManagementVirtualLessonController.refused(e.getCode(), model, response,
                    () -> loadFailed(model, response));
        } catch (ServiceUnavailableException e) {
            return unavailable(e, () -> loadFailed(model, response));
        }
    }

    @PostMapping(LESSON + "/borrar")
    public String delete(
            @PathVariable String courseId, @PathVariable String moduleId, @PathVariable String lessonId,
            HttpServletRequest request, HttpServletResponse response, Model model,
            RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String token = accessToken(request);
        try {
            deleteUseCase.execute(token, lessonId);
        } catch (VirtualCourseManagementException e) {
            return ManagementVirtualLessonController.refused(e.getCode(), model, response,
                    () -> failed(courseId, moduleId, lessonId, token, model, response));
        } catch (ServiceUnavailableException e) {
            return unavailable(e, () -> failed(courseId, moduleId, lessonId, token, model, response));
        }
        return done(DELETED, redirectAttributes);
    }

    /** Shows the confirmation again with the failure, or a notice page if the lesson cannot be read now. */
    private String failed(
            String courseId, String moduleId, String lessonId, String token, Model model,
            HttpServletResponse response) {
        try {
            VirtualLessonLocation found = findUseCase.execute(token, courseId, moduleId, lessonId);
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            return showConfirmation(found,
                    ManagementVirtualCourseStatusController.ACTION_FAILED, model);
        } catch (VirtualCourseManagementException | ServiceUnavailableException e) {
            return problem(model, response, HttpStatus.SERVICE_UNAVAILABLE, VIRTUAL_COURSES,
                    ManagementVirtualCourseStatusController.ACTION_FAILED);
        }
    }

    private static String showConfirmation(VirtualLessonLocation found, String failure, Model model) {
        model.addAttribute("courseId", found.courseId());
        model.addAttribute("moduleId", found.moduleId());
        model.addAttribute("moduleTitle", found.moduleTitle());
        model.addAttribute("lessonId", found.lesson().lessonId());
        model.addAttribute("lessonTitle", found.lesson().title());
        model.addAttribute("actionError", failure);
        return CONFIRM_VIEW;
    }
}
