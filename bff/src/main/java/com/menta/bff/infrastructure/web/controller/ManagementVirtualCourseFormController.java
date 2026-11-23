package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.VirtualCourseDetail;
import com.menta.bff.application.management.ManagementRoles;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException.Code;
import com.menta.bff.application.usecase.CreateVirtualCourseUseCase;
import com.menta.bff.application.usecase.GetManagedVirtualCourseUseCase;
import com.menta.bff.application.usecase.InstructorOptions;
import com.menta.bff.application.usecase.ListInstructorsUseCase;
import com.menta.bff.application.usecase.UpdateVirtualCourseUseCase;
import com.menta.bff.infrastructure.web.dto.CourseFormOptions;
import com.menta.bff.infrastructure.web.dto.VirtualCourseForm;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Virtual courses management (#367): the creation form, {@code /gestion/cursos-virtuales/nuevo}, and the edit
 * form, {@code /{courseId}/editar}, for {@code ADMIN} and {@code INSTRUCTOR} (the Virtual API makes the real
 * decision). Success is Post/Redirect/Get to the list with a flash; a new course is always a draft. The
 * administrator may pick its instructor or leave it to themselves; for an instructor the BFF never sends a
 * {@code professorId}. A refusal of the API is worded from its {@code code}; its body never reaches the browser.
 * Pages are never cached.
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ManagementVirtualCourseFormController {

    static final String CREATED_MESSAGE = "Curso creado como borrador.";
    static final String UPDATED_MESSAGE = "Curso actualizado.";
    static final String INVALID = "Revisá los datos ingresados.";
    static final String SAVE_FAILED = "No pudimos guardar el curso. Intentá de nuevo.";
    static final String PROFESSOR_MISMATCH = "Solo podés crear cursos a tu nombre.";
    static final String PROFESSOR_UNKNOWN = "Elegí un profesor/a de la lista.";
    static final String NO_INSTRUCTORS = "No pudimos cargar los profesores. Probá de nuevo en unos minutos.";
    static final String NOT_FOUND = "No encontramos el curso.";
    static final String NOT_OWNED = "No podés gestionar este curso.";
    static final String LOAD_FAILED = "No pudimos cargar el curso en este momento. Intentá nuevamente en unos minutos.";

    private static final String BASE = "/gestion/cursos-virtuales";
    private static final String FORM_VIEW = "gestion/cursos-virtuales/form";
    private static final String PROBLEM_VIEW = "gestion/cursos-virtuales/problem";
    private static final String BACK_TO_LIST = "redirect:" + BASE;
    private static final InstructorOptions NO_CHOICE = new InstructorOptions(List.of(), false, false);

    private final GetManagedVirtualCourseUseCase getUseCase;
    private final CreateVirtualCourseUseCase createUseCase;
    private final UpdateVirtualCourseUseCase updateUseCase;
    private final ListInstructorsUseCase listInstructorsUseCase;

    @InitBinder
    void trimTexts(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
    }

    @ModelAttribute("levels")
    Map<String, String> levels() {
        return CourseFormOptions.LEVELS;
    }

    // ---- create ----

    @GetMapping(BASE + "/nuevo")
    public String newForm(
            Authentication authentication, HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        model.addAttribute("form", new VirtualCourseForm());
        if (prepareCreate(authentication, accessToken(request), model).unavailable()) {
            return noInstructors(model, response);
        }
        return FORM_VIEW;
    }

    @PostMapping(BASE)
    public String create(
            @Valid @ModelAttribute("form") VirtualCourseForm form,
            BindingResult bindingResult,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String accessToken = accessToken(request);
        InstructorOptions instructors = prepareCreate(authentication, accessToken, model);
        if (instructors.unavailable()) {
            return noInstructors(model, response);
        }
        String professorId = instructors == NO_CHOICE ? null : blankToNull(form.getProfessorId());
        if (professorId != null && !instructors.contains(professorId)) {
            bindingResult.rejectValue("professorId", "professor.unknown", PROFESSOR_UNKNOWN);
        }
        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }
        try {
            createUseCase.execute(accessToken, form.toCommand(professorId));
        } catch (VirtualCourseManagementException e) {
            return reportRefusal(e.getCode(), model, response);
        } catch (ServiceUnavailableException e) {
            return reportUnavailable(e, model, response);
        }
        redirectAttributes.addFlashAttribute("virtualCourseSaved", CREATED_MESSAGE);
        return BACK_TO_LIST;
    }

    // ---- edit ----

    @GetMapping(BASE + "/{courseId}/editar")
    public String editForm(
            @PathVariable String courseId, HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            VirtualCourseDetail course = getUseCase.execute(accessToken(request), courseId);
            model.addAttribute("form", VirtualCourseForm.from(course));
            prepareEdit(courseId, model);
            return FORM_VIEW;
        } catch (VirtualCourseManagementException e) {
            return switch (e.getCode()) {
                case COURSE_NOT_OWNED -> notOwned(model, response);
                case COURSE_NOT_FOUND -> notFound(model, response);
                default -> loadFailed(model, response);
            };
        } catch (ServiceUnavailableException e) {
            log.warn("Virtual course unavailable, rendering the notice: {}", e.getMessage());
            return loadFailed(model, response);
        }
    }

    @PostMapping(BASE + "/{courseId}/editar")
    public String edit(
            @PathVariable String courseId,
            @Valid @ModelAttribute("form") VirtualCourseForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        prepareEdit(courseId, model);
        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }
        try {
            updateUseCase.execute(accessToken(request), courseId, form.toChanges());
        } catch (VirtualCourseManagementException e) {
            return reportRefusal(e.getCode(), model, response);
        } catch (ServiceUnavailableException e) {
            return reportUnavailable(e, model, response);
        }
        redirectAttributes.addFlashAttribute("virtualCourseSaved", UPDATED_MESSAGE);
        return BACK_TO_LIST;
    }

    // ---- helpers ----

    private static String accessToken(HttpServletRequest request) {
        return (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static void prepareEdit(String courseId, Model model) {
        model.addAttribute("editing", true);
        model.addAttribute("courseId", courseId);
    }

    /** Puts the create form's model: the instructors listed for an administrator, {@link #NO_CHOICE} otherwise. */
    private InstructorOptions prepareCreate(Authentication authentication, String accessToken, Model model) {
        boolean admin = ManagementRoles.normalize(authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).toList()).contains("ADMIN");
        model.addAttribute("editing", false);
        model.addAttribute("isAdmin", admin);
        if (!admin) {
            return NO_CHOICE;
        }
        InstructorOptions instructors = listInstructorsUseCase.execute(accessToken);
        Map<String, String> choices = new LinkedHashMap<>();
        instructors.options().forEach(option -> choices.put(option.id(), option.name()));
        model.addAttribute("instructorChoices", choices);
        model.addAttribute("instructorsTruncated", instructors.truncated());
        return instructors;
    }

    private static String noInstructors(Model model, HttpServletResponse response) {
        model.addAttribute("formError", NO_INSTRUCTORS);
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        return FORM_VIEW;
    }

    private static String loadFailed(Model model, HttpServletResponse response) {
        return problem(model, response, HttpStatus.SERVICE_UNAVAILABLE, "Cursos virtuales", LOAD_FAILED);
    }

    private static String problem(
            Model model, HttpServletResponse response, HttpStatus status, String title, String message) {
        response.setStatus(status.value());
        model.addAttribute("problemTitle", title);
        model.addAttribute("problemMessage", message);
        return PROBLEM_VIEW;
    }

    private static String reportRefusal(Code code, Model model, HttpServletResponse response) {
        return switch (code) {
            case INVALID_REQUEST -> formError(INVALID, HttpStatus.BAD_REQUEST, model, response);
            case PROFESSOR_MISMATCH -> formError(PROFESSOR_MISMATCH, HttpStatus.BAD_REQUEST, model, response);
            case COURSE_NOT_OWNED -> notOwned(model, response);
            case COURSE_NOT_FOUND -> notFound(model, response);
            // The structure codes do not apply to the course form: same generic message as the lifecycle ones.
            case COURSE_NOT_DRAFT, COURSE_HAS_PROGRESS, COURSE_IN_USE, COURSE_NOT_PUBLISHABLE,
                    MODULE_NOT_FOUND, LESSON_NOT_FOUND, MODULE_REORDER_MISMATCH -> saveFailed(model, response);
        };
    }

    private static String reportUnavailable(ServiceUnavailableException e, Model model, HttpServletResponse response) {
        log.warn("Virtual course save unavailable: {}", e.getMessage());
        return saveFailed(model, response);
    }

    private static String saveFailed(Model model, HttpServletResponse response) {
        return formError(SAVE_FAILED, HttpStatus.SERVICE_UNAVAILABLE, model, response);
    }

    private static String notOwned(Model model, HttpServletResponse response) {
        return problem(model, response, HttpStatus.FORBIDDEN, "Sin permiso", NOT_OWNED);
    }

    private static String notFound(Model model, HttpServletResponse response) {
        return problem(model, response, HttpStatus.NOT_FOUND, "Curso no encontrado", NOT_FOUND);
    }

    private static String formError(String message, HttpStatus status, Model model, HttpServletResponse response) {
        model.addAttribute("formError", message);
        response.setStatus(status.value());
        return FORM_VIEW;
    }
}
