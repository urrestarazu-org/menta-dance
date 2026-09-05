package com.menta.virtual.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.menta.virtual.application.port.out.CourseProgressRowProjection;
import com.menta.virtual.infrastructure.persistence.entity.LessonProgressJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualLessonJpaEntity;
import com.menta.virtual.infrastructure.persistence.entity.VirtualModuleJpaEntity;
import com.menta.virtual.infrastructure.persistence.repository.LessonProgressJpaRepository;
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
 * behaves correctly under real MySQL 8.0 {@code ORDER BY ... DESC} NULL-ordering semantics, which
 * H2 (used by every other {@code @DataJpaTest} in this codebase, e.g. {@code
 * SubscriptionJpaRepositoryTest}, {@code ActivationTokenJpaRepositoryTest}) does not reliably
 * reproduce and Mockito cannot verify at all.
 *
 * <p>Scenario: a student saves position in one lesson, then marks a DIFFERENT, never-played
 * lesson complete. {@code position_updated_at} is NULL for the completed-but-never-played row and
 * non-NULL for the played row. Under MySQL 8.0, {@code ORDER BY ... DESC} sorts NULL last, so the
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
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
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
}
