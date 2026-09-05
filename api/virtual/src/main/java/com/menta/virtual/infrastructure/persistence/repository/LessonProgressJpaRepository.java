package com.menta.virtual.infrastructure.persistence.repository;

import com.menta.virtual.infrastructure.persistence.entity.LessonProgressJpaEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonProgressJpaRepository extends JpaRepository<LessonProgressJpaEntity, UUID> {

    Optional<LessonProgressJpaEntity> findByUserIdAndLessonId(UUID userId, UUID lessonId);
}
