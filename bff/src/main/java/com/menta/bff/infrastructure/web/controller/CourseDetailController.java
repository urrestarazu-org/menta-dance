package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.usecase.CourseDetailView;
import com.menta.bff.application.usecase.GetCourseDetailViewUseCase;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Course detail controller — anonymous-reachable (spec:
 * {@code virtual-course-detail-view}, "Anonymous course detail access").
 * <p>
 * Reads the caller's access token from {@link TokenRefreshFilter#ACCESS_TOKEN_ATTRIBUTE},
 * present only for already-authenticated callers and {@code null} for
 * anonymous ones, and forwards it straight through to
 * {@link GetCourseDetailViewUseCase}, mirroring {@link LessonViewController}.
 * This controller never evaluates entitlement or progress itself: it only
 * renders the sealed {@link CourseDetailView} the use case already decided
 * (design decision D1/D2). Upstream catalog failures ({@link
 * com.menta.bff.application.port.out.VirtualApiClient.NotFoundException} /
 * {@link com.menta.bff.application.port.out.VirtualApiClient.ServiceUnavailableException})
 * are left uncaught: their {@code @ResponseStatus} annotation resolves them
 * through Spring Boot's default {@code /error} → {@code error.html} path, so
 * no upstream problem-detail body ever reaches the browser.
 * </p>
 * <p>
 * The {@code switch} below is a Java 21 exhaustive statement over the sealed
 * {@link CourseDetailView} — the compiler, not review, guarantees both
 * {@link CourseDetailView.Plain} and {@link CourseDetailView.Resumable} are
 * handled, so no {@code default} branch exists or is needed.
 * </p>
 * <p>
 * Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
public class CourseDetailController {

    private final GetCourseDetailViewUseCase getCourseDetailViewUseCase;

    public CourseDetailController(GetCourseDetailViewUseCase getCourseDetailViewUseCase) {
        this.getCourseDetailViewUseCase = getCourseDetailViewUseCase;
    }

    @GetMapping("/courses/{courseId}")
    public String courseDetail(@PathVariable String courseId, HttpServletRequest request, Model model) {
        String accessToken = (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
        CourseDetailView courseDetailView = getCourseDetailViewUseCase.execute(courseId, accessToken);

        switch (courseDetailView) {
            case CourseDetailView.Plain plain -> model.addAttribute("course", plain.course());
            case CourseDetailView.Resumable resumable -> {
                model.addAttribute("course", resumable.course());
                model.addAttribute("resume", resumable.resume());
            }
        }

        return "course-detail";
    }
}
