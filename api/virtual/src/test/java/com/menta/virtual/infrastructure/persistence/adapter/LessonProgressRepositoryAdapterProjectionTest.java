package com.menta.virtual.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.testsupport.MySqlTestImage;
import com.menta.virtual.application.port.out.CourseProgressRowProjection;
import com.menta.virtual.application.port.out.InProgressCourseRowProjection;
import com.menta.virtual.domain.model.CourseStatus;
import com.menta.virtual.infrastructure.persistence.entity.LessonProgressJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualCourseJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.LessonProgressJpaRepository;
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
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The ONE deliberate Testcontainers case in this whole change (US-VIRTUAL-005, Slice 3;
 * design.md "Testing strategy"): proves {@link LessonProgressJpaRepository#findRowsForUserAndCourse}
 * behaves correctly under real MySQL {@code ORDER BY ... DESC} NULL-ordering semantics, which
 * H2 (used by every other {@code @DataJpaTest} in this codebase, e.g. {@code
 * SubscriptionJpaRepositoryTest}, {@code ActivationTokenJpaRepositoryTest}) does not reliably
 * reproduce and Mockito cannot verify at all.
 *
 * <p>Scenario: a student saves position in one lesson, then marks a DIFFERENT, never-played
 * lesson complete. {@code position_updated_at} is NULL for the completed-but-never-played row and
 * non-NULL for the played row. Under MySQL 8.x, {@code ORDER BY ... DESC} sorts NULL last, so the
 * played lesson must win resume selection — and it must win despite the completed lesson having a
 * LOWER curriculum {@code display_order}, proving the query orders by timestamp first, falling
 * back to curriculum order only on a tie (design.md decision 10), not the reverse.</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes = LessonProgressRepositoryAdapterProjectionTest.JpaConfiguration.class)
@Testcontainers
class LessonProgressRepositoryAdapterProjectionTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MySqlTestImage.NAME)
        .withDatabaseName("menta_virtual_progress_test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        // Replace.NONE keeps the real MySQL container instead of an embedded database, but it
        // also opts out of @DataJpaTest's implicit create-drop schema generation — this module
        // has no Flyway dependency (unlike :api:app), so Hibernate must build the schema itself
        // from the scanned entities for this one test.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Configuration
    @EntityScan(basePackageClasses = LessonProgressJpaEntity.class)
    @EnableJpaRepositories(basePackageClasses = LessonProgressJpaRepository.class)
    static class JpaConfiguration {
    }

    @Autowired private LessonProgressJpaRepository progressRepository;
    @Autowired private VirtualLessonJpaRepository lessonRepository;
    @Autowired private VirtualModuleJpaRepository moduleRepository;
    @Autowired private VirtualCourseJpaRepository courseRepository;

    @Test
    void resume_is_still_the_played_lesson_after_completing_a_different_never_played_one() {
        UUID userId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID moduleId = UUID.randomUUID();
        UUID playedLessonId = UUID.randomUUID();
        UUID completedOnlyLessonId = UUID.randomUUID();
        Instant playedAt = Instant.parse("2026-01-05T10:00:00.000Z");

        moduleRepository.saveAndFlush(new VirtualModuleJpaEntity(moduleId, courseId, "Modulo 1", false, 0));
        // The completed-but-never-played lesson has a LOWER display_order than the played one, so
        // a query that (incorrectly) fell back to curriculum order before timestamp order would
        // pick it first — this seeding makes that bug observable.
        lessonRepository.saveAllAndFlush(List.of(
            new VirtualLessonJpaEntity(
                completedOnlyLessonId, moduleId, courseId, "Leccion 2", "d", "v", 10, false, 1
            ),
            new VirtualLessonJpaEntity(playedLessonId, moduleId, courseId, "Leccion 5", "d", "v", 10, false, 5)
        ));
        progressRepository.saveAllAndFlush(List.of(
            new LessonProgressJpaEntity(
                UUID.randomUUID(), userId, playedLessonId, courseId, 120, false, null,
                playedAt, playedAt, playedAt
            ),
            new LessonProgressJpaEntity(
                UUID.randomUUID(), userId, completedOnlyLessonId, courseId, 0, true, playedAt.plusSeconds(60),
                playedAt, null, playedAt.plusSeconds(60)
            )
        ));

        List<CourseProgressRowProjection> rows = progressRepository.findRowsForUserAndCourse(userId, courseId);

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).getLessonId()).isEqualTo(playedLessonId);
        assertThat(rows.get(0).getPositionUpdatedAt()).isNotNull();
        assertThat(rows.get(0).isCompleted()).isFalse();
        assertThat(rows.get(1).getLessonId()).isEqualTo(completedOnlyLessonId);
        assertThat(rows.get(1).getPositionUpdatedAt()).isNull();
        assertThat(rows.get(1).isCompleted()).isTrue();
    }

    /**
     * #765: the cross-course read returns only the caller's rows in PUBLISHED courses, with both
     * titles, ordered by activity first (NULL {@code position_updated_at} last under MySQL) so
     * each course's first row is its most recent one — and the UUID/Instant columns still convert
     * (JPQL, not native: see the Javadoc of {@code findRowsForUserAndCourse}).
     */
    @Test
    void rows_across_published_courses_come_ordered_by_activity_with_titles_and_only_for_the_caller() {
        UUID userId = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-01-05T10:00:00.000Z");
        UUID olderCourse = seedCourse("Tango Básico", CourseStatus.PUBLISHED);
        UUID recentCourse = seedCourse("Salsa", CourseStatus.PUBLISHED);
        UUID draftCourse = seedCourse("Borrador", CourseStatus.DRAFT);
        UUID olderPlayed = seedLesson(olderCourse, "Postura", 2);
        UUID olderCompletedOnly = seedLesson(olderCourse, "Abrazo", 1);
        UUID recentPlayed = seedLesson(recentCourse, "Básico", 0);
        UUID draftPlayed = seedLesson(draftCourse, "Oculta", 0);
        progressRepository.saveAllAndFlush(List.of(
            progress(userId, olderPlayed, olderCourse, 40, false, t0.plusSeconds(10)),
            progress(userId, olderCompletedOnly, olderCourse, 0, true, null),
            progress(userId, recentPlayed, recentCourse, 75, false, t0.plusSeconds(20)),
            progress(userId, draftPlayed, draftCourse, 5, false, t0.plusSeconds(30)),
            progress(UUID.randomUUID(), olderPlayed, olderCourse, 99, false, t0.plusSeconds(40))
        ));

        List<InProgressCourseRowProjection> rows =
            progressRepository.findRowsForUserInCoursesWithStatus(userId, CourseStatus.PUBLISHED);

        assertThat(rows).extracting(InProgressCourseRowProjection::getLessonId)
            .containsExactly(recentPlayed, olderPlayed, olderCompletedOnly);
        InProgressCourseRowProjection first = rows.get(0);
        assertThat(first.getCourseId()).isEqualTo(recentCourse);
        assertThat(first.getCourseTitle()).isEqualTo("Salsa");
        assertThat(first.getLessonTitle()).isEqualTo("Básico");
        assertThat(first.getPositionSeconds()).isEqualTo(75);
        assertThat(first.getPositionUpdatedAt()).isEqualTo(t0.plusSeconds(20));
        assertThat(rows.get(1).getCourseTitle()).isEqualTo("Tango Básico");
        assertThat(rows.get(2).isCompleted()).isTrue();
        assertThat(rows.get(2).getPositionUpdatedAt()).isNull();
    }

    private UUID seedCourse(String title, CourseStatus status) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        courseRepository.saveAndFlush(new VirtualCourseJpaEntity(
            id, title, "desc", "descripción larga", UUID.randomUUID(), "https://cdn/img.jpg", "tango",
            "BEGINNER", true, status, now, now
        ));
        return id;
    }

    private UUID seedLesson(UUID courseId, String title, int displayOrder) {
        UUID moduleId = UUID.randomUUID();
        moduleRepository.saveAndFlush(new VirtualModuleJpaEntity(moduleId, courseId, "Modulo", false, 0));
        UUID lessonId = UUID.randomUUID();
        lessonRepository.saveAndFlush(
            new VirtualLessonJpaEntity(lessonId, moduleId, courseId, title, "d", "v", 10, false, displayOrder)
        );
        return lessonId;
    }

    private static LessonProgressJpaEntity progress(
        UUID userId, UUID lessonId, UUID courseId, int position, boolean completed, Instant positionUpdatedAt
    ) {
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
        return new LessonProgressJpaEntity(
            UUID.randomUUID(), userId, lessonId, courseId, position, completed, completed ? createdAt : null,
            createdAt, positionUpdatedAt, createdAt
        );
    }
}
