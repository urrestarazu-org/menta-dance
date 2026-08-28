package com.menta.virtual.infrastructure.e2e;

import com.menta.virtual.application.dto.CreateVirtualCourseCommand;
import com.menta.virtual.application.dto.CreateVirtualLessonCommand;
import com.menta.virtual.application.dto.CreateVirtualModuleCommand;
import com.menta.virtual.application.dto.VirtualCourseManagementResult;
import com.menta.virtual.application.dto.VirtualModuleManagementResult;
import com.menta.virtual.application.port.in.CreateVirtualCourseUseCase;
import com.menta.virtual.application.port.in.CreateVirtualLessonUseCase;
import com.menta.virtual.application.port.in.CreateVirtualModuleUseCase;
import com.menta.virtual.application.port.in.ListManagedVirtualCoursesUseCase;
import com.menta.virtual.application.port.in.PublishVirtualCourseUseCase;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/** Profile-only Virtual baseline fixture for the catalog/content E2E journey. */
@Component
@Profile("e2e-catalog-content")
public final class E2eCatalogContentVirtualFixture implements ApplicationRunner, Ordered {

    public static final String PUBLISHED_TITLE = "E2E Published Catalog Course";
    public static final String DRAFT_TITLE = "E2E Draft Catalog Course";
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000127");

    private final ListManagedVirtualCoursesUseCase listCourses;
    private final CreateVirtualCourseUseCase createCourse;
    private final CreateVirtualModuleUseCase createModule;
    private final CreateVirtualLessonUseCase createLesson;
    private final PublishVirtualCourseUseCase publishCourse;

    public E2eCatalogContentVirtualFixture(
        ListManagedVirtualCoursesUseCase listCourses, CreateVirtualCourseUseCase createCourse,
        CreateVirtualModuleUseCase createModule, CreateVirtualLessonUseCase createLesson,
        PublishVirtualCourseUseCase publishCourse
    ) {
        this.listCourses = listCourses;
        this.createCourse = createCourse;
        this.createModule = createModule;
        this.createLesson = createLesson;
        this.publishCourse = publishCourse;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        List<VirtualCourseManagementResult> existing = listCourses.list(ACTOR_ID, true);
        ensureCourse(existing, PUBLISHED_TITLE, true);
        ensureCourse(existing, DRAFT_TITLE, false);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    private void ensureCourse(List<VirtualCourseManagementResult> existing, String title, boolean published) {
        if (existing.stream().anyMatch(course -> course.title().equals(title))) {
            return;
        }
        VirtualCourseManagementResult course = createCourse.create(
            new CreateVirtualCourseCommand(
                title, "Deterministic E2E fixture", "Local catalog/content E2E fixture.",
                ACTOR_ID, "https://example.test/e2e-course.jpg", "DANCE", "BEGINNER"
            ),
            ACTOR_ID, true
        );
        VirtualModuleManagementResult module = createModule.create(
            course.courseId(), new CreateVirtualModuleCommand("E2E module", Optional.of(0), true), ACTOR_ID, true
        );
        createLesson.create(
            module.moduleId(),
            new CreateVirtualLessonCommand(
                "E2E lesson", "Deterministic local fixture lesson.", "e2e-local-video", 5, true, Optional.of(0)
            ),
            ACTOR_ID, true
        );
        if (published) {
            publishCourse.publish(course.courseId(), ACTOR_ID, true);
        }
    }
}
