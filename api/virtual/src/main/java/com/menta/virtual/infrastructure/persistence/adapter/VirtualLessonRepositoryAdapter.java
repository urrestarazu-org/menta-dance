package com.menta.virtual.infrastructure.persistence.adapter;

import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.infrastructure.persistence.mapper.VirtualLessonJpaMapper;
import com.menta.virtual.infrastructure.persistence.repository.VirtualLessonJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** JPA adapter for {@link VirtualLessonRepository}. */
@Component
public class VirtualLessonRepositoryAdapter implements VirtualLessonRepository {

    private final VirtualLessonJpaRepository lessonRepository;

    public VirtualLessonRepositoryAdapter(VirtualLessonJpaRepository lessonRepository) {
        this.lessonRepository = lessonRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<VirtualLesson> findById(LessonId lessonId) {
        return lessonRepository.findById(lessonId.getValue()).map(VirtualLessonJpaMapper::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<VirtualLesson> findByModuleId(ModuleId moduleId) {
        return lessonRepository.findByModuleIdOrderByDisplayOrderAsc(moduleId.getValue()).stream()
            .map(VirtualLessonJpaMapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<VirtualLesson> findByCourseId(CourseId courseId) {
        return lessonRepository.findByCourseId(courseId.getValue()).stream()
            .map(VirtualLessonJpaMapper::toDomain)
            .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public int countByModuleId(ModuleId moduleId) {
        return (int) lessonRepository.countByModuleId(moduleId.getValue());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public VirtualLesson save(VirtualLesson lesson) {
        return VirtualLessonJpaMapper.toDomain(lessonRepository.save(VirtualLessonJpaMapper.toEntity(lesson)));
    }
}
