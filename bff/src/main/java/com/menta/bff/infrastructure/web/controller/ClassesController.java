package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.usecase.ClassesView;
import com.menta.bff.application.usecase.GetClassesViewUseCase;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Public physical classes list (#383), anonymous-reachable.
 *
 * <p>A catalog outage degrades to the page's own notice with status 503;
 * upstream details never reach the browser.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class ClassesController {

    private final GetClassesViewUseCase getClassesViewUseCase;

    @GetMapping("/classes")
    public String classes(Model model, HttpServletResponse response) {
        ClassesView classes = getClassesViewUseCase.execute();
        if (classes.unavailable()) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        model.addAttribute("classes", classes);
        return "classes";
    }
}
