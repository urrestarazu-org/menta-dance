package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.dto.CourseDetail;
import com.menta.bff.application.usecase.GetCourseDetailUseCase;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Course detail controller — anonymous-reachable (spec:
 * {@code virtual-course-detail-view}, "Anonymous course detail access").
 * <p>
 * Course detail is access-agnostic (design D2): this controller never checks
 * entitlement, so anonymous and authenticated visitors get the identical
 * rendering. Upstream failures ({@link com.menta.bff.application.port.out.VirtualApiClient.NotFoundException}
 * / {@link com.menta.bff.application.port.out.VirtualApiClient.ServiceUnavailableException})
 * are left uncaught: their {@code @ResponseStatus} annotation resolves them
 * through Spring Boot's default {@code /error} → {@code error.html} path, so
 * no upstream problem-detail body ever reaches the browser.
 * </p>
 * <p>
 * Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
public class CourseDetailController {

    private final GetCourseDetailUseCase getCourseDetailUseCase;

    public CourseDetailController(GetCourseDetailUseCase getCourseDetailUseCase) {
        this.getCourseDetailUseCase = getCourseDetailUseCase;
    }

    @GetMapping("/courses/{courseId}")
    public String courseDetail(@PathVariable String courseId, Model model) {
        CourseDetail courseDetail = getCourseDetailUseCase.execute(courseId);
        model.addAttribute("course", courseDetail);
        return "course-detail";
    }
}
