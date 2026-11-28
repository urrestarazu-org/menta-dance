package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.VideoManagementException;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.VideoManagementException.Code;
import com.menta.bff.application.usecase.RenameVideoUseCase;
import com.menta.bff.infrastructure.web.dto.VideoRenameForm;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.regex.Pattern;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Videos management (#842): renaming a video, {@code GET}/{@code POST /gestion/videos/{videoId}/renombrar}. For
 * {@code ADMIN} and {@code INSTRUCTOR}; the Virtual API decides which videos an instructor may rename. Success is
 * Post/Redirect/Get to the list with a one-time notice; a refusal is worded from its {@code code} and the API's body
 * never reaches the browser. Pages are never cached.
 *
 * <p>The API has no read of a single video, so the form is prefilled with the title that the list put in the link
 * ({@code ?titulo=}): it is only a hint, escaped by the template, and opening the page asks nothing of the API.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ManagementVideoRenameController {

    static final String RENAMED = "Video renombrado.";
    static final String INVALID = "Revisá los datos ingresados.";
    static final String SAVE_FAILED = "No pudimos guardar el video. Intentá de nuevo.";

    private static final String PATH = VideoScreens.LIST_PATH + "/{videoId}/renombrar";
    private static final String FORM_VIEW = "gestion/videos/renombrar";
    private static final Pattern SIMPLE_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private final RenameVideoUseCase renameVideoUseCase;

    @InitBinder
    void trimTexts(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(false));
    }

    @GetMapping(PATH)
    public String form(
            @PathVariable String videoId, @RequestParam(name = "titulo", required = false) String title,
            HttpServletResponse response, Model model) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (!SIMPLE_ID.matcher(videoId).matches()) {
            return VideoScreens.notFound(model, response);
        }
        model.addAttribute("form", VideoRenameForm.of(VideoScreens.titleHint(title)));
        model.addAttribute("videoId", videoId);
        return FORM_VIEW;
    }

    @PostMapping(PATH)
    public String rename(
            @PathVariable String videoId,
            @Valid @ModelAttribute("form") VideoRenameForm form,
            BindingResult bindingResult,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model,
            RedirectAttributes redirectAttributes) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        model.addAttribute("videoId", videoId);
        if (bindingResult.hasErrors()) {
            return FORM_VIEW;
        }
        try {
            renameVideoUseCase.execute(VideoScreens.accessToken(request), videoId, form.getTitle());
        } catch (VideoManagementException e) {
            if (e.getCode() == Code.INVALID_REQUEST) {
                return formError(INVALID, HttpStatus.BAD_REQUEST, model, response);
            }
            return VideoScreens.refusalPage(e.getCode(), model, response)
                    .orElseGet(() -> formError(SAVE_FAILED, HttpStatus.SERVICE_UNAVAILABLE, model, response));
        } catch (ServiceUnavailableException e) {
            log.warn("Video library unavailable while renaming: {}", e.getMessage());
            return formError(SAVE_FAILED, HttpStatus.SERVICE_UNAVAILABLE, model, response);
        }
        redirectAttributes.addFlashAttribute(VideoScreens.SAVED_ATTRIBUTE, RENAMED);
        return "redirect:" + VideoScreens.LIST_PATH;
    }

    private static String formError(String message, HttpStatus status, Model model, HttpServletResponse response) {
        model.addAttribute("formError", message);
        response.setStatus(status.value());
        return FORM_VIEW;
    }
}
