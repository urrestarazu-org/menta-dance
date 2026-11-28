package com.menta.bff.infrastructure.web.controller;

import static com.menta.bff.infrastructure.web.controller.ManagementVirtualModuleController.COURSE;
import static com.menta.bff.infrastructure.web.controller.ManagementVirtualModuleController.INVALID;
import static com.menta.bff.infrastructure.web.controller.ManagementVirtualModuleController.accessToken;
import static com.menta.bff.infrastructure.web.controller.ManagementVirtualModuleController.doneIn;
import static com.menta.bff.infrastructure.web.controller.ManagementVirtualModuleController.inOrder;
import static com.menta.bff.infrastructure.web.controller.ManagementVirtualModuleController.loadFailed;
import static com.menta.bff.infrastructure.web.controller.ManagementVirtualModuleController.notFound;

import com.menta.bff.application.dto.VirtualCourseDetail.VirtualLessonDetail;
import com.menta.bff.application.dto.VirtualCourseDetail.VirtualModuleDetail;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException;
import com.menta.bff.application.port.out.VirtualCourseManagementApiClient.VirtualCourseManagementException.Code;
import com.menta.bff.application.usecase.CreateVirtualLessonUseCase;
import com.menta.bff.application.usecase.GetManagedVirtualCourseUseCase;
import com.menta.bff.application.usecase.ListVideoChoicesUseCase;
import com.menta.bff.application.usecase.UpdateVirtualLessonUseCase;
import com.menta.bff.infrastructure.web.dto.LessonForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
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
 * Virtual courses management (#367): creation and edition of the lessons of a module, under
 * {@code /gestion/cursos-virtuales/{courseId}/modulos/{moduleId}/lecciones}. For {@code ADMIN} and {@code INSTRUCTOR};
 * the Virtual API makes the real decision. Writes are Post/Redirect/Get to the structure with a flash, refusals are
 * worded from the API's {@code code} (never its body, by the module controller's helpers) and pages are never cached.
 *
 * <p>The API has no read of a single lesson, so the edit form is prefilled from the course detail, looking the module
 * and then the lesson up by id. The write goes to the lesson id; the API decides ownership. A video cannot be cleared
 * once assigned, so on edit an empty video id is not sent.
 * </p>
 *
 * <p>The video is picked from the library list ({@code ListVideoChoicesUseCase}, #843) or pasted by hand; the list is
 * read only to show the form (and to show it again after a refusal), and if it cannot be obtained the form still
 * renders and saves. Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class ManagementVirtualLessonController {

    static final String CREATED = "Lección creada.";
    static final String UPDATED = "Lección actualizada.";
    static final String SAVE_FAILED = "No pudimos guardar la lección. Intentá de nuevo.";
    static final String LESSON_NOT_FOUND = "No encontramos la lección.";

    private static final String LESSONS = COURSE + "/modulos/{moduleId}/lecciones";
    private static final String FORM_VIEW = "gestion/cursos-virtuales/leccion-form";

    private final GetManagedVirtualCourseUseCase getUseCase;
    private final CreateVirtualLessonUseCase createUseCase;
    private final UpdateVirtualLessonUseCase updateUseCase;
    private final ListVideoChoicesUseCase videoChoicesUseCase;

    @InitBinder
    void trimTexts(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
    }

    @GetMapping(LESSONS + "/nueva")
    public String newForm(
            @PathVariable String courseId,
            @PathVariable String moduleId,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        LessonForm form = new LessonForm();
        model.addAttribute("form", form);
        prepare(courseId, moduleId, null, model);
        return formWithVideos(FORM_VIEW, request, form, model);
    }

    @PostMapping(LESSONS)
    public String create(
            @PathVariable String courseId,
            @PathVariable String moduleId,
            @Valid @ModelAttribute("form") LessonForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        prepare(courseId, moduleId, null, model);
        if (bindingResult.hasErrors()) {
            return formWithVideos(FORM_VIEW, request, form, model);
        }
        try {
            createUseCase.execute(accessToken(request), moduleId, form.toCommand());
        } catch (VirtualCourseManagementException e) {
            return formWithVideos(saveRefused(e.getCode(), model, response), request, form, model);
        } catch (ServiceUnavailableException e) {
            return formWithVideos(unavailable(e, model, response), request, form, model);
        }
        return doneIn(moduleId, CREATED, "success", redirectAttributes);
    }

    @GetMapping(LESSONS + "/{lessonId}/editar")
    public String editForm(
            @PathVariable String courseId,
            @PathVariable String moduleId,
            @PathVariable String lessonId,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try {
            VirtualModuleDetail module = inOrder(getUseCase.execute(accessToken(request), courseId)).stream()
                    .filter(candidate -> candidate.moduleId().equals(moduleId)).findFirst()
                    .orElseThrow(() -> new VirtualCourseManagementException(Code.MODULE_NOT_FOUND));
            VirtualLessonDetail lesson = module.lessons().stream()
                    .filter(candidate -> candidate.lessonId().equals(lessonId)).findFirst()
                    .orElseThrow(() -> new VirtualCourseManagementException(Code.LESSON_NOT_FOUND));
            LessonForm form = LessonForm.from(lesson);
            model.addAttribute("form", form);
            prepare(courseId, moduleId, lessonId, model);
            return formWithVideos(FORM_VIEW, request, form, model);
        } catch (VirtualCourseManagementException e) {
            return refused(e.getCode(), model, response, () -> loadFailed(model, response));
        } catch (ServiceUnavailableException e) {
            return ManagementVirtualModuleController.unavailable(e, () -> loadFailed(model, response));
        }
    }

    @PostMapping(LESSONS + "/{lessonId}/editar")
    public String edit(
            @PathVariable String courseId,
            @PathVariable String moduleId,
            @PathVariable String lessonId,
            @Valid @ModelAttribute("form") LessonForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        prepare(courseId, moduleId, lessonId, model);
        if (bindingResult.hasErrors()) {
            return formWithVideos(FORM_VIEW, request, form, model);
        }
        try {
            updateUseCase.execute(accessToken(request), lessonId, form.toChanges());
        } catch (VirtualCourseManagementException e) {
            return formWithVideos(saveRefused(e.getCode(), model, response), request, form, model);
        } catch (ServiceUnavailableException e) {
            return formWithVideos(unavailable(e, model, response), request, form, model);
        }
        return doneIn(moduleId, UPDATED, "success", redirectAttributes);
    }

    /**
     * Adds the videos the selector lists when the view is the form (#843). The list is read only when the form is
     * shown, never to save, and never fails the page: a list that cannot be obtained is an unavailable one, and the
     * form is still saved with a pasted identifier.
     */
    private String formWithVideos(String view, HttpServletRequest request, LessonForm form, Model model) {
        if (FORM_VIEW.equals(view)) {
            model.addAttribute("videos", videoChoicesUseCase.execute(accessToken(request), form.resolvedVideoId()));
        }
        return view;
    }

    private static void prepare(String courseId, String moduleId, String lessonId, Model model) {
        model.addAttribute("editing", lessonId != null);
        model.addAttribute("courseId", courseId);
        model.addAttribute("moduleId", moduleId);
        model.addAttribute("lessonId", lessonId);
    }

    private static String unavailable(ServiceUnavailableException e, Model model, HttpServletResponse response) {
        return ManagementVirtualModuleController.unavailable(e, () -> saveFailed(model, response));
    }

    private static String saveFailed(Model model, HttpServletResponse response) {
        return formError(SAVE_FAILED, HttpStatus.SERVICE_UNAVAILABLE, model, response);
    }

    private static String formError(String message, HttpStatus status, Model model, HttpServletResponse response) {
        model.addAttribute("formError", message);
        response.setStatus(status.value());
        return FORM_VIEW;
    }

    /** What a save that the API refused looks like: the form says what to fix, or a notice page. */
    private static String saveRefused(Code code, Model model, HttpServletResponse response) {
        if (code == Code.INVALID_REQUEST) {
            return formError(INVALID, HttpStatus.BAD_REQUEST, model, response);
        }
        return refused(code, model, response, () -> saveFailed(model, response));
    }

    /** The module controller's refusals plus the lesson that is gone; any other code goes to {@code otherwise}. */
    static String refused(Code code, Model model, HttpServletResponse response, Supplier<String> otherwise) {
        if (code == Code.LESSON_NOT_FOUND) {
            return notFound(model, response, "Lección no encontrada", LESSON_NOT_FOUND);
        }
        return ManagementVirtualModuleController.refused(code, model, response, otherwise);
    }
}
