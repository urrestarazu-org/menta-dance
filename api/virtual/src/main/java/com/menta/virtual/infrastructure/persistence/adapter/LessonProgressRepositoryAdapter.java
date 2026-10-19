package com.menta.virtual.infrastructure.persistence.adapter;

import com.menta.virtual.application.port.out.CourseProgressRowProjection;
import com.menta.virtual.application.port.out.LessonProgressRepository;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.LessonProgress;
import com.menta.virtual.infrastructure.persistence.entity.LessonProgressJpaEntity;
import com.menta.virtual.infrastructure.persistence.mapper.LessonProgressJpaMapper;
import com.menta.virtual.infrastructure.persistence.repository.LessonProgressJpaRepository;
import com.menta.virtual.infrastructure.persistence.repository.VirtualLessonJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for {@link LessonProgressRepository}. */
@Component
public class LessonProgressRepositoryAdapter implements LessonProgressRepository {

    private final LessonProgressJpaRepository jpaRepository;
    private final VirtualLessonJpaRepository lessonJpaRepository;

    public LessonProgressRepositoryAdapter(
        LessonProgressJpaRepository jpaRepository, VirtualLessonJpaRepository lessonJpaRepository
    ) {
        this.jpaRepository = jpaRepository;
        this.lessonJpaRepository = lessonJpaRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<LessonProgress> findByUserIdAndLessonId(UUID userId, LessonId lessonId) {
        return jpaRepository.findByUserIdAndLessonId(userId, lessonId.getValue())
            .map(LessonProgressJpaMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public LessonProgress save(LessonProgress progress) {
        Instant now = Instant.now();
        Instant createdAt = jpaRepository.findById(progress.getId().getValue())
            .map(LessonProgressJpaEntity::getCreatedAt)
            .orElse(now);
        LessonProgressJpaEntity saved =
            jpaRepository.save(LessonProgressJpaMapper.toEntity(progress, createdAt, now));
        return LessonProgressJpaMapper.toDomain(saved);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<CourseProgressRowProjection> findRowsForUserAndCourse(UUID userId, UUID courseId) {
        return jpaRepository.findRowsForUserAndCourse(userId, courseId);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public long countLessonsByCourseId(UUID courseId) {
        return lessonJpaRepository.countByCourseId(courseId);
    }
}
