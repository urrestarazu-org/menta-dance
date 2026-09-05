package com.menta.virtual.infrastructure.web.controller;

import com.menta.virtual.application.dto.CourseProgressView;
import com.menta.virtual.application.dto.LessonProgressView;
import com.menta.virtual.application.port.in.CompleteLessonUseCase;
import com.menta.virtual.application.port.in.GetCourseProgressUseCase;
import com.menta.virtual.application.port.in.GetLessonProgressUseCase;
import com.menta.virtual.application.port.in.SaveLessonProgressUseCase;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.infrastructure.web.dto.CourseProgressResponse;
import com.menta.virtual.infrastructure.web.dto.LessonProgressResponse;
import com.menta.virtual.infrastructure.web.dto.SaveLessonProgressRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for the student-facing lesson and course progress endpoints (US-VIRTUAL-005).
 * {@code actingUserId} always comes from the token subject ({@link VirtualLessonAdminController}'s
 * own pattern), never from the path or body — a student cannot read or write another student's
 * row. No class-level {@code @RequestMapping}: the course-progress route (Slice 3) lives under
 * {@code /api/v1/virtual/courses/**}, a different base path than the lesson-progress routes under
 * {@code /api/v1/virtual/lessons/**}, so each method declares its own full path.
 */
@RestController
@VirtualStudentEndpoint
public class VirtualStudentProgressController {

    private final SaveLessonProgressUseCase saveLessonProgressUseCase;
    private final GetLessonProgressUseCase getLessonProgressUseCase;
    private final CompleteLessonUseCase completeLessonUseCase;
    private final GetCourseProgressUseCase getCourseProgressUseCase;

    public VirtualStudentProgressController(
        SaveLessonProgressUseCase saveLessonProgressUseCase,
        GetLessonProgressUseCase getLessonProgressUseCase,
        CompleteLessonUseCase completeLessonUseCase,
        GetCourseProgressUseCase getCourseProgressUseCase
    ) {
        this.saveLessonProgressUseCase = saveLessonProgressUseCase;
        this.getLessonProgressUseCase = getLessonProgressUseCase;
        this.completeLessonUseCase = completeLessonUseCase;
        this.getCourseProgressUseCase = getCourseProgressUseCase;
    }

    @PutMapping("/api/v1/virtual/lessons/{lessonId}/progress")
    public ResponseEntity<LessonProgressResponse> save(
        @PathVariable String lessonId, @RequestBody SaveLessonProgressRequest request, Authentication authentication
    ) {
        LessonProgressView view =
            saveLessonProgressUseCase.save(lessonId, actingUserId(authentication), request.positionSeconds());
        return ResponseEntity.ok(LessonProgressResponse.from(view));
    }

    @GetMapping("/api/v1/virtual/lessons/{lessonId}/progress")
    public ResponseEntity<LessonProgressResponse> get(@PathVariable String lessonId, Authentication authentication) {
        LessonProgressView view = getLessonProgressUseCase.get(lessonId, actingUserId(authentication))
            .orElseThrow(LessonNotFoundException::new);
        return ResponseEntity.ok(LessonProgressResponse.from(view));
    }

    @PostMapping("/api/v1/virtual/lessons/{lessonId}/complete")
    public ResponseEntity<LessonProgressResponse> complete(
        @PathVariable String lessonId, Authentication authentication
    ) {
        LessonProgressView view = completeLessonUseCase.complete(lessonId, actingUserId(authentication));
        return ResponseEntity.ok(LessonProgressResponse.from(view));
    }

    @GetMapping("/api/v1/virtual/courses/{courseId}/progress")
    public ResponseEntity<CourseProgressResponse> courseProgress(
        @PathVariable String courseId, Authentication authentication
    ) {
        CourseProgressView view = getCourseProgressUseCase.get(courseId, actingUserId(authentication));
        return ResponseEntity.ok(CourseProgressResponse.from(view));
    }

    private static UUID actingUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
