package com.menta.virtual.infrastructure.demo;

import com.menta.shared.demo.DemoIds;
import com.menta.shared.demo.DemoSeedOrder;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualLessonJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualModuleJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Seeds the two published demo Virtual courses ("Bachata Sensual" and "Bachata Tradicional"), each with a
 * preview module holding a free lesson and a restricted module holding a protected lesson.
 *
 * <p>Course ids come from {@link DemoIds}; module and lesson ids are private to this fixture. Every course,
 * module and lesson is upserted by its fixed id ({@code existsById}), so a second start creates nothing and a
 * partially seeded database only receives the missing rows. Entities are saved through the JPA repositories,
 * like {@code E2eBunnyNetVirtualFixture}, because the Virtual use cases generate random ids. The video
 * references are plain labels that the local Bunny.net adapter signs without any credential. The runner is
 * intentionally not transactional.
 */
@Component
@Profile("demo")
@RequiredArgsConstructor
public final class DemoVirtualCatalogFixture implements ApplicationRunner, Ordered {

    private static final String CATEGORY = "DANCE";
    private static final String LEVEL = "BEGINNER";
    private static final String THUMBNAIL = "https://example.test/demo-course.jpg";

    private static final List<DemoCourse> DEMO_COURSES = List.of(
        new DemoCourse(
            DemoIds.VIRTUAL_SENSUAL_COURSE_ID,
            "Bachata Sensual",
            "Online bachata sensual course.",
            new DemoModule(
                UUID.fromString("de000000-0000-4000-8000-0000000001a1"), "Introduction", true,
                new DemoLesson(
                    UUID.fromString("de000000-0000-4000-8000-0000000002a1"), "Basic steps (free)",
                    "demo-sensual-free", true)),
            new DemoModule(
                UUID.fromString("de000000-0000-4000-8000-0000000001a2"), "Advanced technique", false,
                new DemoLesson(
                    UUID.fromString("de000000-0000-4000-8000-0000000002a2"), "Body movement",
                    "demo-sensual-restricted", false))),
        new DemoCourse(
            DemoIds.VIRTUAL_TRADITIONAL_COURSE_ID,
            "Bachata Tradicional",
            "Online bachata tradicional course.",
            new DemoModule(
                UUID.fromString("de000000-0000-4000-8000-0000000001b1"), "Introduction", true,
                new DemoLesson(
                    UUID.fromString("de000000-0000-4000-8000-0000000002b1"), "Basic steps (free)",
                    "demo-traditional-free", true)),
            new DemoModule(
                UUID.fromString("de000000-0000-4000-8000-0000000001b2"), "Advanced technique", false,
                new DemoLesson(
                    UUID.fromString("de000000-0000-4000-8000-0000000002b2"), "Footwork",
                    "demo-traditional-restricted", false))));

    private final VirtualCourseJpaRepository courseRepository;
    private final VirtualModuleJpaRepository moduleRepository;
    private final VirtualLessonJpaRepository lessonRepository;

    @Override
    public void run(ApplicationArguments arguments) {
        DEMO_COURSES.forEach(this::seed);
    }

    @Override
    public int getOrder() {
        return DemoSeedOrder.CONTENT;
    }

    private void seed(DemoCourse course) {
        if (!courseRepository.existsById(course.id())) {
            Instant now = Instant.now();
            courseRepository.save(new VirtualCourseJpaEntity(
                course.id(), course.title(), course.description(), course.description(),
                DemoIds.INSTRUCTOR_USER_ID, THUMBNAIL, CATEGORY, LEVEL, false,
                CourseStatus.PUBLISHED, now, now));
        }
        seed(course.id(), course.previewModule(), 0);
        seed(course.id(), course.restrictedModule(), 1);
    }

    private void seed(UUID courseId, DemoModule module, int displayOrder) {
        if (!moduleRepository.existsById(module.id())) {
            moduleRepository.save(
                new VirtualModuleJpaEntity(module.id(), courseId, module.title(), module.preview(), displayOrder));
        }
        DemoLesson lesson = module.lesson();
        if (!lessonRepository.existsById(lesson.id())) {
            lessonRepository.save(new VirtualLessonJpaEntity(
                lesson.id(), module.id(), courseId, lesson.title(), "Demo lesson.", lesson.videoId(), 5,
                lesson.free(), 0));
        }
    }

    private record DemoCourse(
        UUID id, String title, String description, DemoModule previewModule, DemoModule restrictedModule) {
    }

    private record DemoModule(UUID id, String title, boolean preview, DemoLesson lesson) {
    }

    private record DemoLesson(UUID id, String title, String videoId, boolean free) {
    }
}
