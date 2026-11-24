package com.menta.virtual.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.testsupport.MySqlTestImage;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.VirtualCourseJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualLessonJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualModuleJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * {@link VirtualLessonRepositoryAdapter#findByVideoId} against real MySQL (#841): the usage lookup that decides
 * whether a video may be deleted (any lesson of any course in any status uses it) and who may rename it. Real MySQL
 * on purpose: its default collation compares {@code video_id} ignoring case, which the use cases rely on knowing.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = VirtualLessonRepositoryVideoUsageIntegrationTest.JpaConfiguration.class)
@Testcontainers
class VirtualLessonRepositoryVideoUsageIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MySqlTestImage.NAME)
        .withDatabaseName("menta_virtual_video_usage_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // No Flyway in this module: Hibernate builds the schema from the scanned entities.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Configuration
    @EntityScan(basePackageClasses = VirtualLessonJpaEntity.class)
    @EnableJpaRepositories(basePackageClasses = VirtualLessonJpaRepository.class)
    @Import(VirtualLessonRepositoryAdapter.class)
    static class JpaConfiguration {
    }

    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    @Autowired private VirtualLessonRepositoryAdapter adapter;
    @Autowired private VirtualCourseJpaRepository courseRepository;
    @Autowired private VirtualModuleJpaRepository moduleRepository;
    @Autowired private VirtualLessonJpaRepository lessonRepository;

    /** A course in {@code status} with one module and one lesson per video id (null = a lesson without video). */
    private List<UUID> seedCourse(CourseStatus status, String... videoIds) {
        UUID courseId = UUID.randomUUID();
        courseRepository.save(new VirtualCourseJpaEntity(
            courseId, "Curso " + courseId, "corta", "larga", UUID.randomUUID(), "https://cdn/img.jpg", "tango",
            "BEGINNER", false, status, NOW, NOW
        ));
        UUID moduleId = UUID.randomUUID();
        moduleRepository.save(new VirtualModuleJpaEntity(moduleId, courseId, "Módulo", false, 0));
        java.util.ArrayList<UUID> lessonIds = new java.util.ArrayList<>();
        for (int order = 0; order < videoIds.length; order++) {
            UUID lessonId = UUID.randomUUID();
            lessonRepository.save(new VirtualLessonJpaEntity(
                lessonId, moduleId, courseId, "Lección " + order, "d", videoIds[order], 5, false, order
            ));
            lessonIds.add(lessonId);
        }
        return lessonIds;
    }

    @Test
    void finds_every_lesson_that_uses_the_video_in_any_course_whatever_its_status() {
        List<UUID> draft = seedCourse(CourseStatus.DRAFT, "video-a", "video-b");
        List<UUID> published = seedCourse(CourseStatus.PUBLISHED, "video-a");
        seedCourse(CourseStatus.PUBLISHED, "video-c", null);

        List<VirtualLesson> lessons = adapter.findByVideoId("video-a");

        assertThat(lessons).extracting(lesson -> lesson.getId().getValue())
            .containsExactlyInAnyOrder(draft.get(0), published.get(0));
        assertThat(lessons).allSatisfy(lesson -> assertThat(lesson.getVideoId()).isEqualTo("video-a"));
    }

    @Test
    void a_video_no_lesson_uses_has_no_lessons() {
        seedCourse(CourseStatus.PUBLISHED, "video-a", null);

        assertThat(adapter.findByVideoId("video-z")).isEmpty();
    }

    @Test
    void a_lookup_by_a_prefix_or_a_pattern_does_not_match() {
        seedCourse(CourseStatus.PUBLISHED, "video-abc");

        assertThat(adapter.findByVideoId("video-ab")).isEmpty();
        assertThat(adapter.findByVideoId("video-%")).isEmpty();
        assertThat(adapter.findByVideoId("video_abc")).isEmpty();
    }
}
