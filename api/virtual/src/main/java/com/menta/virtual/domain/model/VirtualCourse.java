package com.menta.virtual.domain.model;

import java.util.Objects;

/**
 * A course in the Virtual catalog (US-VIRTUAL-001).
 *
 * <p>Read-only for now: this issue only lists published courses, it never
 * creates or edits one. {@code moduleCount}/{@code lessonCount}/{@code
 * totalDurationMinutes} are pre-aggregated by the persistence adapter
 * (COUNT/SUM queries against virtual_modules/virtual_lessons) — modelling
 * those as full domain aggregates here would load content this issue never
 * needs (module/lesson detail is US-VIRTUAL-002's job).</p>
 */
public final class VirtualCourse {

    private final CourseId id;
    private final String title;
    private final String shortDescription;
    private final String imageUrl;
    private final CourseCategory category;
    private final CourseLevel level;
    private final boolean premium;
    private final CourseStatus status;
    private final int moduleCount;
    private final int lessonCount;
    private final int totalDurationMinutes;

    public VirtualCourse(
        CourseId id, String title, String shortDescription, String imageUrl,
        CourseCategory category, CourseLevel level, boolean premium, CourseStatus status,
        int moduleCount, int lessonCount, int totalDurationMinutes
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.title = Objects.requireNonNull(title, "title cannot be null");
        this.shortDescription = Objects.requireNonNull(shortDescription, "shortDescription cannot be null");
        this.imageUrl = Objects.requireNonNull(imageUrl, "imageUrl cannot be null");
        this.category = Objects.requireNonNull(category, "category cannot be null");
        this.level = Objects.requireNonNull(level, "level cannot be null");
        this.premium = premium;
        this.status = Objects.requireNonNull(status, "status cannot be null");
        if (moduleCount < 0) {
            throw new IllegalArgumentException("moduleCount cannot be negative");
        }
        if (lessonCount < 0) {
            throw new IllegalArgumentException("lessonCount cannot be negative");
        }
        if (totalDurationMinutes < 0) {
            throw new IllegalArgumentException("totalDurationMinutes cannot be negative");
        }
        this.moduleCount = moduleCount;
        this.lessonCount = lessonCount;
        this.totalDurationMinutes = totalDurationMinutes;
    }

    public boolean isPublished() {
        return status == CourseStatus.PUBLISHED;
    }

    public CourseId getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public CourseCategory getCategory() {
        return category;
    }

    public CourseLevel getLevel() {
        return level;
    }

    public boolean isPremium() {
        return premium;
    }

    public CourseStatus getStatus() {
        return status;
    }

    public int getModuleCount() {
        return moduleCount;
    }

    public int getLessonCount() {
        return lessonCount;
    }

    public int getTotalDurationMinutes() {
        return totalDurationMinutes;
    }
}
