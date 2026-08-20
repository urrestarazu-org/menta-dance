package com.menta.virtual.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * A course in the Virtual catalog (US-VIRTUAL-001, US-VIRTUAL-006).
 *
 * <p>{@code professorId} is an opaque reference to an {@code api:auth} user
 * id — stored by value, never a FK or JOIN across modules
 * (docs/25-ARCHITECTURE-RULES.md). It is the ownership check's source of
 * truth: an INSTRUCTOR may only manage a course where {@code professorId}
 * equals their own authenticated user id — same convention as {@code
 * PhysicalCourse} (#42).</p>
 *
 * <p>{@code moduleCount}/{@code lessonCount}/{@code totalDurationMinutes}
 * are pre-aggregated by the persistence adapter (COUNT/SUM queries against
 * virtual_modules/virtual_lessons) — kept for the public catalog read path
 * (#46/#95), unrelated to the management fields added by #54.</p>
 */
public final class VirtualCourse {

    private final CourseId id;
    private final String title;
    private final String shortDescription;
    private final String description;
    private final UUID professorId;
    private final String imageUrl;
    private final CourseCategory category;
    private final CourseLevel level;
    private final boolean premium;
    private final CourseStatus status;
    private final int moduleCount;
    private final int lessonCount;
    private final int totalDurationMinutes;

    public VirtualCourse(
        CourseId id, String title, String shortDescription, String description, UUID professorId,
        String imageUrl, CourseCategory category, CourseLevel level, boolean premium, CourseStatus status,
        int moduleCount, int lessonCount, int totalDurationMinutes
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.title = Objects.requireNonNull(title, "title cannot be null");
        this.shortDescription = Objects.requireNonNull(shortDescription, "shortDescription cannot be null");
        this.description = Objects.requireNonNull(description, "description cannot be null");
        this.professorId = Objects.requireNonNull(professorId, "professorId cannot be null");
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

    /** Creates a brand-new course: fresh {@link CourseId}, always {@code DRAFT}, zero content yet. */
    public static VirtualCourse create(
        String title, String shortDescription, String description, UUID professorId, String imageUrl,
        CourseCategory category, CourseLevel level
    ) {
        return new VirtualCourse(
            CourseId.generate(), title, shortDescription, description, professorId, imageUrl, category, level,
            false, CourseStatus.DRAFT, 0, 0, 0
        );
    }

    public boolean isPublished() {
        return status == CourseStatus.PUBLISHED;
    }

    public boolean isDraft() {
        return status == CourseStatus.DRAFT;
    }

    public boolean isOwnedBy(UUID candidateProfessorId) {
        return professorId.equals(candidateProfessorId);
    }

    public VirtualCourse withTitle(String newTitle) {
        return copyWith(newTitle, shortDescription, description, imageUrl, category, level, premium, status);
    }

    public VirtualCourse withShortDescription(String newShortDescription) {
        return copyWith(title, newShortDescription, description, imageUrl, category, level, premium, status);
    }

    public VirtualCourse withDescription(String newDescription) {
        return copyWith(title, shortDescription, newDescription, imageUrl, category, level, premium, status);
    }

    public VirtualCourse withImageUrl(String newImageUrl) {
        return copyWith(title, shortDescription, description, newImageUrl, category, level, premium, status);
    }

    public VirtualCourse withCategory(CourseCategory newCategory) {
        return copyWith(title, shortDescription, description, imageUrl, newCategory, level, premium, status);
    }

    public VirtualCourse withLevel(CourseLevel newLevel) {
        return copyWith(title, shortDescription, description, imageUrl, category, newLevel, premium, status);
    }

    public VirtualCourse withPremium(boolean newPremium) {
        return copyWith(title, shortDescription, description, imageUrl, category, level, newPremium, status);
    }

    /** US-VIRTUAL-006 escenario 4 — publish validation (module/lesson completeness) is the use case's job. */
    public VirtualCourse publish() {
        return copyWith(title, shortDescription, description, imageUrl, category, level, premium, CourseStatus.PUBLISHED);
    }

    /**
     * Escenario 5: subscribers keeping temporary access after unpublish is
     * entitlement logic (#56), out of this course-management aggregate's
     * scope — this only flips the status.
     */
    public VirtualCourse unpublish() {
        return copyWith(title, shortDescription, description, imageUrl, category, level, premium, CourseStatus.DRAFT);
    }

    private VirtualCourse copyWith(
        String newTitle, String newShortDescription, String newDescription, String newImageUrl,
        CourseCategory newCategory, CourseLevel newLevel, boolean newPremium, CourseStatus newStatus
    ) {
        return new VirtualCourse(
            id, newTitle, newShortDescription, newDescription, professorId, newImageUrl, newCategory, newLevel,
            newPremium, newStatus, moduleCount, lessonCount, totalDurationMinutes
        );
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

    public String getDescription() {
        return description;
    }

    public UUID getProfessorId() {
        return professorId;
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
