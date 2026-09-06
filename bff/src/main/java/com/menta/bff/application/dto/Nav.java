package com.menta.bff.application.dto;

/**
 * Previous/next lesson pointers, mirroring {@code PublicLessonNavigationDto}.
 * Either side may be {@code null} at the edges of a course (first lesson has
 * no {@code previousLesson}, last has no {@code nextLesson}).
 *
 * @param previousLesson pointer to the previous lesson, or {@code null}
 * @param nextLesson     pointer to the next lesson, or {@code null}
 */
public record Nav(Ref previousLesson, Ref nextLesson) {

    /**
     * Compact sibling-lesson reference.
     *
     * @param lessonId sibling lesson identifier
     * @param title    sibling lesson title
     */
    public record Ref(String lessonId, String title) {
    }
}
