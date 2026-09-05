package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.CourseProgressView;
import com.menta.virtual.application.port.out.CourseProgressRowProjection;
import java.util.List;

/**
 * Pure static assembler (US-VIRTUAL-005, Slice 3) — no mocks needed to reach the 0.95
 * domain+application coverage gate. Resume ordering MUST stay in SQL (design.md): this class
 * consumes an already-ordered row list and never compares timestamps, so it needs no null
 * handling of its own.
 */
public final class CourseProgressAssembler {

    private CourseProgressAssembler() {
    }

    public static CourseProgressView assemble(
        String courseId, List<CourseProgressRowProjection> orderedRows, long totalLessons
    ) {
        int completed = 0;
        for (CourseProgressRowProjection row : orderedRows) {
            if (row.isCompleted()) {
                completed++;
            }
        }
        CourseProgressView.ResumeLesson resume = orderedRows.isEmpty() ? null : resumeFrom(orderedRows.get(0));
        return new CourseProgressView(courseId, completed, (int) totalLessons, percentageOf(completed, totalLessons), resume);
    }

    private static CourseProgressView.ResumeLesson resumeFrom(CourseProgressRowProjection row) {
        return new CourseProgressView.ResumeLesson(
            row.getLessonId().toString(), row.getModuleId().toString(), row.getPositionSeconds(), row.isCompleted()
        );
    }

    private static int percentageOf(int completed, long total) {
        if (total == 0) {
            return 0;
        }
        if (completed == total) {
            return 100;
        }
        long roundedHalfUp = Math.round(completed * 100.0 / total);
        return (int) Math.min(roundedHalfUp, 99);
    }
}
