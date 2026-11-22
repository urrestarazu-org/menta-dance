package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.VirtualCourseDetail;
import com.menta.bff.application.dto.VirtualCourseDetail.VirtualModuleDetail;
import com.menta.bff.application.dto.VirtualCourseStatus;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException.Code;
import com.menta.bff.application.usecase.CreateVirtualModuleUseCase;
import com.menta.bff.application.usecase.GetManagedVirtualCourseUseCase;
import com.menta.bff.application.usecase.ReorderVirtualModulesUseCase;
import com.menta.bff.application.usecase.UpdateVirtualModuleUseCase;
import com.menta.bff.infrastructure.web.dto.ModuleForm;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
 * Virtual courses management (#367): the structure of a course, {@code /gestion/cursos-virtuales/{courseId}/estructura}
 * (its modules in order with their lessons, read only), and the modules: creation, edition and the move up and down
 * buttons (no JavaScript). For {@code ADMIN} and {@code INSTRUCTOR}; the Virtual API makes the real decision. Every
 * write is Post/Redirect/Get to the structure with a flash. A refusal of the API is worded from its {@code code}; its
 * body never reaches the browser. Pages are never cached.
 *
 * <p>The API has no read of a single module, so the edit form is prefilled from the course detail. A move reads the
 * detail, swaps the module with its neighbour and sends the whole list of ids. Another tab may change the structure
 * between that read and the write: the API requires the exact set of modules, detects it as
 * {@code MODULE_REORDER_MISMATCH} and the user is told to review the order; a swap of two modules that were
 * meanwhile reordered is not detected, as the API cannot tell it from the user's intent.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ManagementVirtualModuleController {

    static final String CREATED = "Módulo creado.";
    static final String UPDATED = "Módulo actualizado.";
    static final String MOVED = "Módulo movido.";
    static final String NOT_MOVED = "El módulo ya está en esa posición.";
    static final String GONE = "Ese módulo ya no existe. Revisá la estructura del curso.";
    static final String CHANGED = "La estructura del curso cambió. Revisá el orden e intentá de nuevo.";
    static final String INVALID = "Revisá los datos ingresados.";
    static final String SAVE_FAILED = "No pudimos guardar el módulo. Intentá de nuevo.";
    static final String MOVE_FAILED = "No pudimos mover el módulo. Intentá de nuevo.";
    static final String LOAD_FAILED = "No pudimos cargar el curso en este momento. Intentá nuevamente en unos minutos.";
    static final String COURSE_NOT_FOUND = "No encontramos el curso.";
    static final String MODULE_NOT_FOUND = "No encontramos el módulo.";
    static final String NOT_OWNED = "No podés gestionar este curso.";

    static final String COURSE = "/gestion/cursos-virtuales/{courseId}";
    private static final String STRUCTURE_VIEW = "gestion/cursos-virtuales/estructura";
    private static final String FORM_VIEW = "gestion/cursos-virtuales/modulo-form";
    private static final String PROBLEM_VIEW = "gestion/cursos-virtuales/problem";
    private static final String BACK_TO_STRUCTURE = "redirect:" + COURSE + "/estructura";

    private final GetManagedVirtualCourseUseCase getUseCase;
    private final CreateVirtualModuleUseCase createUseCase;
    private final UpdateVirtualModuleUseCase updateUseCase;
    private final ReorderVirtualModulesUseCase reorderUseCase;

    @InitBinder
    void trimTexts(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
    }

    // ---- structure ----

    @GetMapping(COURSE + "/estructura")
    public String structure(
            @PathVariable String courseId, HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            VirtualCourseDetail course = getUseCase.execute(accessToken(request), courseId);
            model.addAttribute("course", course);
            model.addAttribute("statusLabel", statusLabel(course.status()));
            model.addAttribute("modules", inOrder(course));
            return STRUCTURE_VIEW;
        } catch (VirtualCourseManagementException e) {
            return refused(e.getCode(), model, response, () -> loadFailed(model, response));
        } catch (ServiceUnavailableException e) {
            return unavailable(e, () -> loadFailed(model, response));
        }
    }

    // ---- create ----

    @GetMapping(COURSE + "/modulos/nuevo")
    public String newForm(@PathVariable String courseId, HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        model.addAttribute("form", new ModuleForm());
        prepare(courseId, null, model);
        return FORM_VIEW;
    }

    @PostMapping(COURSE + "/modulos")
    public String create(
            @PathVariable String courseId,
            @Valid @ModelAttribute("form") ModuleForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        prepare(courseId, null, model);
        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }
        try {
            createUseCase.execute(accessToken(request), courseId, form.toCommand());
        } catch (VirtualCourseManagementException e) {
            return saveRefused(e.getCode(), model, response);
        } catch (ServiceUnavailableException e) {
            return unavailable(e, () -> formError(SAVE_FAILED, HttpStatus.SERVICE_UNAVAILABLE, model, response));
        }
        return done(CREATED, redirectAttributes);
    }

    // ---- edit ----

    @GetMapping(COURSE + "/modulos/{moduleId}/editar")
    public String editForm(
            @PathVariable String courseId,
            @PathVariable String moduleId,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            VirtualModuleDetail module = inOrder(getUseCase.execute(accessToken(request), courseId)).stream()
                    .filter(candidate -> candidate.moduleId().equals(moduleId)).findFirst()
                    .orElseThrow(() -> new VirtualCourseManagementException(Code.MODULE_NOT_FOUND));
            model.addAttribute("form", ModuleForm.from(module));
            prepare(courseId, moduleId, model);
            return FORM_VIEW;
        } catch (VirtualCourseManagementException e) {
            return refused(e.getCode(), model, response, () -> loadFailed(model, response));
        } catch (ServiceUnavailableException e) {
            return unavailable(e, () -> loadFailed(model, response));
        }
    }

    @PostMapping(COURSE + "/modulos/{moduleId}/editar")
    public String edit(
            @PathVariable String courseId,
            @PathVariable String moduleId,
            @Valid @ModelAttribute("form") ModuleForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        prepare(courseId, moduleId, model);
        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }
        try {
            updateUseCase.execute(accessToken(request), moduleId, form.toChanges());
        } catch (VirtualCourseManagementException e) {
            return saveRefused(e.getCode(), model, response);
        } catch (ServiceUnavailableException e) {
            return unavailable(e, () -> formError(SAVE_FAILED, HttpStatus.SERVICE_UNAVAILABLE, model, response));
        }
        return done(UPDATED, redirectAttributes);
    }

    // ---- move ----

    @PostMapping(COURSE + "/modulos/{moduleId}/subir")
    public String moveUp(
            @PathVariable String courseId, @PathVariable String moduleId, HttpServletRequest request,
            HttpServletResponse response, Model model, RedirectAttributes redirectAttributes) {
        return move(courseId, moduleId, -1, request, response, model, redirectAttributes);
    }

    @PostMapping(COURSE + "/modulos/{moduleId}/bajar")
    public String moveDown(
            @PathVariable String courseId, @PathVariable String moduleId, HttpServletRequest request,
            HttpServletResponse response, Model model, RedirectAttributes redirectAttributes) {
        return move(courseId, moduleId, 1, request, response, model, redirectAttributes);
    }

    /** Swaps the module with its neighbour {@code step} places away; at the edge or when gone the API is not asked. */
    private String move(
            String courseId, String moduleId, int step, HttpServletRequest request, HttpServletResponse response,
            Model model, RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        String token = accessToken(request);
        try {
            List<String> ids = new ArrayList<>(inOrder(getUseCase.execute(token, courseId)).stream()
                    .map(VirtualModuleDetail::moduleId).toList());
            int from = ids.indexOf(moduleId);
            int to = from + step;
            if (from < 0 || to < 0 || to >= ids.size()) {
                return done(from < 0 ? GONE : NOT_MOVED, "info", redirectAttributes);
            }
            Collections.swap(ids, from, to);
            reorderUseCase.execute(token, courseId, ids);
        } catch (VirtualCourseManagementException e) {
            if (e.getCode() == Code.MODULE_REORDER_MISMATCH) {
                return done(CHANGED, "error", redirectAttributes);
            }
            return refused(e.getCode(), model, response, () -> moveFailed(model, response));
        } catch (ServiceUnavailableException e) {
            return unavailable(e, () -> moveFailed(model, response));
        }
        return done(MOVED, redirectAttributes);
    }

    // ---- helpers ----

    static String accessToken(HttpServletRequest request) {
        return (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
    }

    static List<VirtualModuleDetail> inOrder(VirtualCourseDetail course) {
        return course.modules().stream().sorted(Comparator.comparingInt(VirtualModuleDetail::order)).toList();
    }

    private static void prepare(String courseId, String moduleId, Model model) {
        model.addAttribute("editing", moduleId != null);
        model.addAttribute("courseId", courseId);
        model.addAttribute("moduleId", moduleId);
    }

    static String statusLabel(VirtualCourseStatus status) {
        return switch (status) {
            case DRAFT -> "Borrador";
            case PUBLISHED -> "Publicado";
            case ARCHIVED -> "Archivado";
            case UNKNOWN -> "Sin dato";
        };
    }

    static String done(String message, RedirectAttributes redirectAttributes) {
        return done(message, "success", redirectAttributes);
    }

    static String done(String message, String kind, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("structureAlert", message);
        redirectAttributes.addFlashAttribute("structureAlertKind", kind);
        return BACK_TO_STRUCTURE;
    }

    static String unavailable(ServiceUnavailableException e, Supplier<String> page) {
        log.warn("Virtual course unavailable, rendering the notice: {}", e.getMessage());
        return page.get();
    }

    /** What a save that the API refused looks like: the form says what to fix, or a notice page. */
    private static String saveRefused(Code code, Model model, HttpServletResponse response) {
        if (code == Code.INVALID_REQUEST) {
            return formError(INVALID, HttpStatus.BAD_REQUEST, model, response);
        }
        return refused(code, model, response,
                () -> formError(SAVE_FAILED, HttpStatus.SERVICE_UNAVAILABLE, model, response));
    }

    /** The refusals every screen words alike; any other code is not the user's to fix and goes to {@code otherwise}. */
    static String refused(Code code, Model model, HttpServletResponse response, Supplier<String> otherwise) {
        return switch (code) {
            case COURSE_NOT_OWNED -> problem(model, response, HttpStatus.FORBIDDEN, "Sin permiso", NOT_OWNED);
            case COURSE_NOT_FOUND -> notFound(model, response, "Curso no encontrado", COURSE_NOT_FOUND);
            case MODULE_NOT_FOUND -> notFound(model, response, "Módulo no encontrado", MODULE_NOT_FOUND);
            default -> otherwise.get();
        };
    }

    static String notFound(Model model, HttpServletResponse response, String title, String message) {
        return problem(model, response, HttpStatus.NOT_FOUND, title, message);
    }

    static String loadFailed(Model model, HttpServletResponse response) {
        return problem(model, response, HttpStatus.SERVICE_UNAVAILABLE, "Cursos virtuales", LOAD_FAILED);
    }

    private static String moveFailed(Model model, HttpServletResponse response) {
        return problem(model, response, HttpStatus.SERVICE_UNAVAILABLE, "Cursos virtuales", MOVE_FAILED);
    }

    static String problem(
            Model model, HttpServletResponse response, HttpStatus status, String title, String message) {
        response.setStatus(status.value());
        model.addAttribute("problemTitle", title);
        model.addAttribute("problemMessage", message);
        return PROBLEM_VIEW;
    }

    static String formError(String message, HttpStatus status, Model model, HttpServletResponse response) {
        model.addAttribute("formError", message);
        response.setStatus(status.value());
        return FORM_VIEW;
    }
}
