package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.usecase.ClassDetailView;
import com.menta.bff.application.usecase.ClassesView;
import com.menta.bff.application.usecase.GetClassDetailViewUseCase;
import com.menta.bff.application.usecase.GetClassesViewUseCase;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Public physical classes pages (#383): the list and one class with its upcoming
 * sessions. Anonymous-reachable.
 *
 * <p>The class page forwards the caller's access token, present only for a signed-in student
 * ({@code null} for a visitor), so the use case can mark the sessions the student already holds
 * (#542).
 * </p>
 *
 * <p>An unknown, unpublished or virtual id surfaces as the catalog port's
 * {@code NotFoundException}, left uncaught so its {@code @ResponseStatus}
 * answers 404 through {@code /error}. A catalog outage degrades to the page's
 * own notice with status 503; upstream details never reach the browser.
 * </p>
 *
 * <p>Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
@RequiredArgsConstructor
public class ClassesController {

    private final GetClassesViewUseCase getClassesViewUseCase;
    private final GetClassDetailViewUseCase getClassDetailViewUseCase;

    @GetMapping("/classes")
    public String classes(Model model, HttpServletResponse response) {
        ClassesView classes = getClassesViewUseCase.execute();
        if (classes.unavailable()) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        model.addAttribute("classes", classes);
        return "classes";
    }

    @GetMapping("/classes/{courseId}")
    public String classDetail(
            @PathVariable String courseId, HttpServletRequest request, Model model, HttpServletResponse response) {
        String accessToken = (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
        ClassDetailView course = getClassDetailViewUseCase.execute(courseId, accessToken);
        if (course.unavailable()) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        model.addAttribute("course", course);
        return "class-detail";
    }
}
