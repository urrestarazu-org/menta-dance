package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.management.ManagementRoles;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.CourseManagementException.Code;
import com.menta.bff.application.port.out.PhysicalCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.usecase.CreateManagedCourseUseCase;
import com.menta.bff.application.usecase.GetManagedCourseUseCase;
import com.menta.bff.application.usecase.InstructorOptions;
import com.menta.bff.application.usecase.ListInstructorsUseCase;
import com.menta.bff.application.usecase.ListManagedCoursesUseCase;
import com.menta.bff.application.usecase.ManagedCourseView;
import com.menta.bff.application.usecase.OwnInstructor;
import com.menta.bff.application.usecase.ResolveOwnInstructorUseCase;
import com.menta.bff.application.usecase.UpdateManagedCourseUseCase;
import com.menta.bff.infrastructure.web.dto.CourseForm;
import com.menta.bff.infrastructure.web.dto.CourseFormOptions;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Physical courses management screens (#368): the list, {@code GET /gestion/cursos-presenciales}, the creation
 * form, {@code /nuevo}, and the edit form, {@code /{courseId}/editar}. Access is restricted to {@code ADMIN} and
 * {@code INSTRUCTOR} in {@code BffSecurityConfig}; the Physical API makes the real decision with the session's
 * access token and scopes an instructor to their own courses.
 *
 * <p>Successful submissions are Post/Redirect/Get to the list with a flash confirmation; nothing travels in the
 * redirect URL. The administrator picks the instructor of a new course from the instructors the Auth API lists
 * and the name stored is the one listed, never one submitted; an instructor's course is always their own (see
 * {@link ResolveOwnInstructorUseCase}). A refusal of the API is worded here from its {@code code}: a field error
 * when it names a field, a notice page for a course that is not found or not owned, a general error otherwise.
 * The API's body never reaches the browser. Pages are never cached.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ManagementCourseController {

    static final String CREATED_MESSAGE = "Creamos el curso.";
    static final String UPDATED_MESSAGE = "Guardamos los cambios del curso.";
    static final String INVALID = "Revisá los datos e intentá de nuevo.";
    static final String UNAVAILABLE = "No pudimos guardar el curso. Probá de nuevo en unos minutos.";
    static final String PROFESSOR_REQUIRED = "Elegí un profesor/a de la lista.";
    static final String PROFESSOR_MISMATCH = "Solo podés crear cursos a tu nombre.";
    static final String HAS_ASSIGNMENTS = "No se puede desactivar: tiene alumnos asignados a sesiones futuras.";
    static final String NO_INSTRUCTORS = "No pudimos cargar los profesores. Probá de nuevo en unos minutos.";
    static final String INSTRUCTOR_UNKNOWN = "Por ahora no podemos crear cursos desde tu cuenta en la web. "
            + "Pedile a un administrador que cree el curso a tu nombre.";
    static final String NOT_FOUND = "No encontramos ese curso.";
    static final String NOT_OWNED = "No podés gestionar este curso.";
    static final String LOAD_FAILED = "No pudimos cargar el curso en este momento. Intentá nuevamente en unos minutos.";

    private static final String BASE = "/gestion/cursos-presenciales";
    private static final String LIST_VIEW = "gestion/cursos/list";
    private static final String FORM_VIEW = "gestion/cursos/form";
    private static final String PROBLEM_VIEW = "gestion/cursos/problem";
    private static final String BACK_TO_LIST = "redirect:" + BASE;

    private final ListManagedCoursesUseCase listManagedCoursesUseCase;
    private final GetManagedCourseUseCase getManagedCourseUseCase;
    private final CreateManagedCourseUseCase createManagedCourseUseCase;
    private final UpdateManagedCourseUseCase updateManagedCourseUseCase;
    private final ListInstructorsUseCase listInstructorsUseCase;
    private final ResolveOwnInstructorUseCase resolveOwnInstructorUseCase;

    @ModelAttribute("days")
    Map<String, String> days() {
        return CourseFormOptions.DAYS;
    }

    @ModelAttribute("levels")
    Map<String, String> levels() {
        return CourseFormOptions.LEVELS;
    }

    @ModelAttribute("statuses")
    Map<String, String> statuses() {
        return CourseFormOptions.STATUSES;
    }

    // ---- list ----

    @GetMapping(BASE)
    public String list(HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        List<ManagedCourseView> courses = List.of();
        boolean unavailable = false;
        try {
            courses = listManagedCoursesUseCase.execute(accessToken(request));
        } catch (CourseManagementException | ServiceUnavailableException e) {
            log.warn("Courses unavailable, rendering the screen without them: {}", e.getMessage());
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            unavailable = true;
        }
        model.addAttribute("courses", courses);
        model.addAttribute("unavailable", unavailable);
        return LIST_VIEW;
    }

    // ---- create ----

    @GetMapping(BASE + "/nuevo")
    public String newForm(
            Authentication authentication, HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        model.addAttribute("form", new CourseForm());
        Target target = resolveTarget(authentication, accessToken(request), model);
        if (target.failure() != null) {
            model.addAttribute("formError", target.failure());
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        return FORM_VIEW;
    }

    @PostMapping(BASE + "/nuevo")
    public String create(
            @Valid @ModelAttribute("form") CourseForm form,
            BindingResult bindingResult,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String accessToken = accessToken(request);
        Target target = resolveTarget(authentication, accessToken, model);
        if (target.failure() != null) {
            model.addAttribute("formError", target.failure());
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            return FORM_VIEW;
        }
        String professorName = target.nameOf(form.getProfessorId());
        if (professorName == null) {
            bindingResult.rejectValue("professorId", "professor.required", PROFESSOR_REQUIRED);
        }
        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }
        try {
            createManagedCourseUseCase.execute(accessToken, form.toCreateCommand(target.idFor(form), professorName));
        } catch (CourseManagementException e) {
            return reportRefusal(e.getCode(), bindingResult, model, response);
        } catch (ServiceUnavailableException e) {
            return reportUnavailable(e, model, response);
        }
        redirectAttributes.addFlashAttribute("courseSaved", CREATED_MESSAGE);
        return BACK_TO_LIST;
    }

    // ---- edit ----

    @GetMapping(BASE + "/{courseId}/editar")
    public String editForm(
            @PathVariable String courseId, HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        Optional<ManagedCourseView> course = load(courseId, accessToken(request), model, response);
        course.ifPresent(found -> model.addAttribute("form", CourseForm.from(found)));
        return course.isPresent() ? FORM_VIEW : PROBLEM_VIEW;
    }

    @PostMapping(BASE + "/{courseId}/editar")
    public String edit(
            @PathVariable String courseId,
            @Valid @ModelAttribute("form") CourseForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String accessToken = accessToken(request);
        Optional<ManagedCourseView> course = load(courseId, accessToken, model, response);
        if (course.isEmpty()) {
            return PROBLEM_VIEW;
        }
        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }
        try {
            updateManagedCourseUseCase.execute(accessToken, course.get().courseId(), form.toUpdateCommand());
        } catch (CourseManagementException e) {
            return reportRefusal(e.getCode(), bindingResult, model, response);
        } catch (ServiceUnavailableException e) {
            return reportUnavailable(e, model, response);
        }
        redirectAttributes.addFlashAttribute("courseSaved", UPDATED_MESSAGE);
        return BACK_TO_LIST;
    }

    // ---- helpers ----

    private static String accessToken(HttpServletRequest request) {
        return (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
    }

    /**
     * Who a new course can be assigned to: the instructors listed for an administrator, the caller for an
     * instructor. {@code failure} is the notice to show when the page cannot offer a usable form.
     */
    private record Target(boolean admin, InstructorOptions instructors, OwnInstructor own, String failure) {

        String nameOf(String chosenId) {
            if (admin) {
                return chosenId == null ? null : instructors.nameOf(chosenId);
            }
            return own == null ? null : own.name();
        }

        String idFor(CourseForm form) {
            return admin ? form.getProfessorId() : own.id();
        }
    }

    /** Puts what the form needs to render the instructor into the model and tells who the course goes to. */
    private Target resolveTarget(Authentication authentication, String accessToken, Model model) {
        boolean admin = ManagementRoles.normalize(authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList()).contains("ADMIN");
        model.addAttribute("editing", false);
        model.addAttribute("isAdmin", admin);
        if (admin) {
            InstructorOptions instructors = listInstructorsUseCase.execute(accessToken);
            Map<String, String> choices = new LinkedHashMap<>();
            instructors.options().forEach(option -> choices.put(option.id(), option.name()));
            model.addAttribute("instructorChoices", choices);
            model.addAttribute("instructorsTruncated", instructors.truncated());
            return new Target(true, instructors, null, instructors.unavailable() ? NO_INSTRUCTORS : null);
        }
        Optional<OwnInstructor> own = resolveOwnInstructorUseCase.execute(accessToken);
        model.addAttribute("ownInstructor", own.orElse(null));
        return new Target(false, null, own.orElse(null), own.isPresent() ? null : INSTRUCTOR_UNKNOWN);
    }

    /** Reads the course of an edit page; when it cannot, fills the notice page and its status. */
    private Optional<ManagedCourseView> load(
            String courseId, String accessToken, Model model, HttpServletResponse response) {
        try {
            ManagedCourseView course = getManagedCourseUseCase.execute(accessToken, courseId);
            model.addAttribute("editing", true);
            model.addAttribute("course", course);
            model.addAttribute("courseId", course.courseId());
            return Optional.of(course);
        } catch (CourseManagementException e) {
            if (e.getCode() == Code.COURSE_NOT_OWNED) {
                problem(model, response, HttpStatus.FORBIDDEN, "Sin permiso", NOT_OWNED);
            } else if (e.getCode() == Code.COURSE_NOT_FOUND) {
                problem(model, response, HttpStatus.NOT_FOUND, "Curso no encontrado", NOT_FOUND);
            } else {
                problem(model, response, HttpStatus.SERVICE_UNAVAILABLE, "Cursos presenciales", LOAD_FAILED);
            }
        } catch (ServiceUnavailableException e) {
            log.warn("Course unavailable, rendering the notice: {}", e.getMessage());
            problem(model, response, HttpStatus.SERVICE_UNAVAILABLE, "Cursos presenciales", LOAD_FAILED);
        }
        return Optional.empty();
    }

    private static void problem(
            Model model, HttpServletResponse response, HttpStatus status, String title, String message) {
        response.setStatus(status.value());
        model.addAttribute("problemTitle", title);
        model.addAttribute("problemMessage", message);
    }

    /** Words a refusal of the API by its code. Returns the view to render. */
    private static String reportRefusal(
            Code code, BindingResult bindingResult, Model model, HttpServletResponse response) {
        switch (code) {
            case PROFESSOR_REQUIRED -> {
                bindingResult.rejectValue("professorId", "professor.required", PROFESSOR_REQUIRED);
                response.setStatus(HttpStatus.BAD_REQUEST.value());
            }
            case PROFESSOR_MISMATCH -> {
                bindingResult.rejectValue("professorId", "professor.mismatch", PROFESSOR_MISMATCH);
                response.setStatus(HttpStatus.BAD_REQUEST.value());
            }
            case COURSE_HAS_ACTIVE_ASSIGNMENTS -> {
                bindingResult.rejectValue("statusValid", "status.hasAssignments", HAS_ASSIGNMENTS);
                response.setStatus(HttpStatus.CONFLICT.value());
            }
            case INVALID_REQUEST -> {
                model.addAttribute("formError", INVALID);
                response.setStatus(HttpStatus.BAD_REQUEST.value());
            }
            case COURSE_NOT_OWNED -> {
                problem(model, response, HttpStatus.FORBIDDEN, "Sin permiso", NOT_OWNED);
                return PROBLEM_VIEW;
            }
            case COURSE_NOT_FOUND -> {
                problem(model, response, HttpStatus.NOT_FOUND, "Curso no encontrado", NOT_FOUND);
                return PROBLEM_VIEW;
            }
            default -> {
                model.addAttribute("formError", UNAVAILABLE);
                response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            }
        }
        return FORM_VIEW;
    }

    private static String reportUnavailable(ServiceUnavailableException e, Model model, HttpServletResponse response) {
        log.warn("Course save unavailable: {}", e.getMessage());
        model.addAttribute("formError", UNAVAILABLE);
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        return FORM_VIEW;
    }
}
