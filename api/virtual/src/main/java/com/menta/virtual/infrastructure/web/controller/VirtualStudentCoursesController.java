package com.menta.virtual.infrastructure.web.controller;

import com.menta.virtual.application.port.in.ListInProgressCoursesUseCase;
import com.menta.virtual.infrastructure.web.dto.InProgressCourseResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP adapter for the student's own course listings (#765). The user always comes from the token
 * subject, never from a parameter, so a student can only list their own courses. Shares the
 * {@link VirtualStudentEndpoint} advice chain with {@link VirtualStudentProgressController}.
 */
@RestController
@VirtualStudentEndpoint
@RequiredArgsConstructor
public class VirtualStudentCoursesController {

    private final ListInProgressCoursesUseCase listInProgressCoursesUseCase;

    /** A non-integer {@code limit} is a {@code 400} from the advice chain; its range is checked by the use case. */
    @GetMapping("/api/v1/virtual/courses/me/in-progress")
    public ResponseEntity<List<InProgressCourseResponse>> inProgress(
        @RequestParam(defaultValue = "" + ListInProgressCoursesUseCase.DEFAULT_LIMIT) int limit,
        Authentication authentication
    ) {
        UUID actingUserId = UUID.fromString(authentication.getName());
        List<InProgressCourseResponse> courses = listInProgressCoursesUseCase.list(actingUserId, limit).stream()
            .map(InProgressCourseResponse::from)
            .toList();
        return ResponseEntity.ok(courses);
    }
}
