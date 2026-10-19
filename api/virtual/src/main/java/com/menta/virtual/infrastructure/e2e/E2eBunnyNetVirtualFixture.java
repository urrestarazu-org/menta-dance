package com.menta.virtual.infrastructure.e2e;

import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualLessonJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualModuleJpaRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Seeds the two fixed-id Virtual courses required by the local Bunny.net E2E journey
 * (ADR-0040, design.md A3/A4').
 *
 * <p>{@link #UNPLANNED_COURSE_ID} carries a preview module and a protected module and is
 * never linked to any billing plan — alone it proves the D7 denial for an unplanned course.
 * {@link #PLANNED_COURSE_ID} carries only a protected module; {@code
 * E2eBunnyNetBillingFixture} links it to a billing plan for the premium-grant scenario.</p>
 *
 * <p>Seeded directly through Virtual's repository ports with id-carrying JPA entity
 * constructors, not the admin use cases, so ids stay deterministic and no admin login is
 * required (design.md A3).</p>
 */
@Component
@Profile("e2e-bunny-net")
public final class E2eBunnyNetVirtualFixture implements ApplicationRunner, Ordered {

    public static final UUID UNPLANNED_COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000129");
    public static final UUID PLANNED_COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000130");
    private static final UUID PROFESSOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000127");

    private final VirtualCourseJpaRepository courseRepository;
    private final VirtualModuleJpaRepository moduleRepository;
    private final VirtualLessonJpaRepository lessonRepository;

    public E2eBunnyNetVirtualFixture(
        VirtualCourseJpaRepository courseRepository, VirtualModuleJpaRepository moduleRepository,
        VirtualLessonJpaRepository lessonRepository
    ) {
        this.courseRepository = courseRepository;
        this.moduleRepository = moduleRepository;
        this.lessonRepository = lessonRepository;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        seedUnplannedCourse();
        seedPlannedCourse();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private void seedUnplannedCourse() {
        if (courseRepository.existsById(UNPLANNED_COURSE_ID)) {
            return;
        }
        saveCourse(UNPLANNED_COURSE_ID, "E2E Bunny.net Unplanned Course");
        UUID previewModuleId = UUID.randomUUID();
        moduleRepository.save(
            new VirtualModuleJpaEntity(previewModuleId, UNPLANNED_COURSE_ID, "E2E preview module", true, 0)
        );
        saveLesson(previewModuleId, UNPLANNED_COURSE_ID, "E2E unplanned preview lesson", "e2e-unplanned-preview");
        UUID protectedModuleId = UUID.randomUUID();
        moduleRepository.save(
            new VirtualModuleJpaEntity(protectedModuleId, UNPLANNED_COURSE_ID, "E2E protected module", false, 1)
        );
        saveLesson(
            protectedModuleId, UNPLANNED_COURSE_ID, "E2E unplanned protected lesson", "e2e-unplanned-protected"
        );
    }

    private void seedPlannedCourse() {
        if (courseRepository.existsById(PLANNED_COURSE_ID)) {
            return;
        }
        saveCourse(PLANNED_COURSE_ID, "E2E Bunny.net Planned Course");
        UUID protectedModuleId = UUID.randomUUID();
        moduleRepository.save(
            new VirtualModuleJpaEntity(protectedModuleId, PLANNED_COURSE_ID, "E2E protected module", false, 0)
        );
        saveLesson(protectedModuleId, PLANNED_COURSE_ID, "E2E planned protected lesson", "e2e-planned-protected");
    }

    private void saveCourse(UUID courseId, String title) {
        Instant now = Instant.now();
        courseRepository.save(new VirtualCourseJpaEntity(
            courseId, title, "Deterministic local Bunny.net E2E fixture.",
            "Local Bunny.net E2E fixture course with a fixed id (design.md A3/A4').",
            PROFESSOR_ID, "https://example.test/e2e-bunny-net-course.jpg", "DANCE", "BEGINNER", false,
            CourseStatus.PUBLISHED, now, now
        ));
    }

    private void saveLesson(UUID moduleId, UUID courseId, String title, String videoId) {
        lessonRepository.save(new VirtualLessonJpaEntity(
            UUID.randomUUID(), moduleId, courseId, title,
            "Deterministic local Bunny.net E2E fixture lesson.", videoId, 5, false, 0
        ));
    }
}
