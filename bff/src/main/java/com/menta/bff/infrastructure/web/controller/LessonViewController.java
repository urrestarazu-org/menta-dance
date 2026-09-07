package com.menta.bff.infrastructure.web.controller;

import com.menta.bff.application.usecase.GetLessonViewUseCase;
import com.menta.bff.application.usecase.LessonView;
import com.menta.bff.infrastructure.web.filter.TokenRefreshFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Lesson-player controller — anonymous-reachable (spec
 * {@code virtual-lesson-view}, "Deep-linkable nested route with course
 * context").
 * <p>
 * Reads the caller's access token from {@link TokenRefreshFilter#ACCESS_TOKEN_ATTRIBUTE},
 * present only for already-authenticated callers and {@code null} for
 * anonymous ones, and forwards it straight through to
 * {@link GetLessonViewUseCase}. This controller never evaluates entitlement
 * itself: it only renders the sealed {@link LessonView} the use case already
 * decided (design decision B).
 * </p>
 * <p>
 * The {@code switch} below is a Java 21 exhaustive statement over the sealed
 * {@link LessonView} — the compiler, not review, guarantees both {@link
 * LessonView.Playable} and {@link LessonView.Sample} are handled, so no
 * {@code default} branch exists or is needed. Both variants render the same
 * {@code lesson} template (design decision F — flat templates, no
 * fragments); the template branches on which model attribute is present.
 * </p>
 * <p>
 * Part of Clean Architecture infrastructure layer.
 * </p>
 */
@Controller
public class LessonViewController {

    private final GetLessonViewUseCase getLessonViewUseCase;

    public LessonViewController(GetLessonViewUseCase getLessonViewUseCase) {
        this.getLessonViewUseCase = getLessonViewUseCase;
    }

    @GetMapping("/courses/{courseId}/lessons/{lessonId}")
    public String lesson(
            @PathVariable String courseId,
            @PathVariable String lessonId,
            HttpServletRequest request,
            Model model) {
        String accessToken = (String) request.getAttribute(TokenRefreshFilter.ACCESS_TOKEN_ATTRIBUTE);
        LessonView lessonView = getLessonViewUseCase.execute(courseId, lessonId, accessToken);

        switch (lessonView) {
            case LessonView.Playable playable -> model.addAttribute("playable", playable);
            case LessonView.Sample sample -> model.addAttribute("sample", sample);
        }

        return "lesson";
    }
}
