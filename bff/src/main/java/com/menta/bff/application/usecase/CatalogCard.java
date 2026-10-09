package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.CatalogCourse;
import com.menta.bff.domain.model.CourseModality;
import java.util.Locale;

/**
 * Display model for one catalog entry, with Spanish labels already resolved.
 *
 * @param courseId      course identifier
 * @param modality      delivery modality
 * @param title         course title
 * @param levelLabel    Spanish level label, {@code null} when unknown
 * @param professorName professor name, {@code null} when not provided
 */
public record CatalogCard(
        String courseId,
        CourseModality modality,
        String title,
        String levelLabel,
        String professorName) {

    static CatalogCard from(CatalogCourse course) {
        return new CatalogCard(
                course.courseId(), course.modality(), course.title(),
                levelLabel(course.level()), course.professorName());
    }

    /** Spanish label for the course's modality. */
    public String modalityLabel() {
        return modality == CourseModality.VIRTUAL ? "Virtual" : "Presencial";
    }

    /**
     * Path of the course's detail page, or {@code null} when it has none yet.
     * Single decision point for linking: physical courses stay unlinked until
     * their detail view exists (issue #383) — enable it by returning
     * {@code "/classes/" + courseId} for {@link CourseModality#PHYSICAL}.
     */
    public String detailPath() {
        return modality == CourseModality.VIRTUAL ? "/courses/" + courseId : null;
    }

    static String levelLabel(String level) {
        if (level == null) {
            return null;
        }
        return switch (level.trim().toUpperCase(Locale.ROOT)) {
            case "BEGINNER" -> "Principiante";
            case "INTERMEDIATE" -> "Intermedio";
            case "ADVANCED" -> "Avanzado";
            default -> null;
        };
    }
}
