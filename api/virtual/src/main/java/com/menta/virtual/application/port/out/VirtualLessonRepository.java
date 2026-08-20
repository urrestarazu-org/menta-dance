package com.menta.virtual.application.port.out;

import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualLesson;
import java.util.List;
import java.util.Optional;

/** Persistence port for {@link VirtualLesson}. */
public interface VirtualLessonRepository {

    Optional<VirtualLesson> findById(LessonId lessonId);

    List<VirtualLesson> findByModuleId(ModuleId moduleId);

    /** Every lesson of a course across all its modules — used for publish-completeness validation. */
    List<VirtualLesson> findByCourseId(CourseId courseId);

    int countByModuleId(ModuleId moduleId);

    VirtualLesson save(VirtualLesson lesson);

    /**
     * Deletes every lesson of a course (across all its modules). Used when
     * deleting a {@code DRAFT} course, before its modules — lessons must go
     * first since they also FK-reference {@code virtual_modules}.
     */
    void deleteByCourseId(CourseId courseId);
}
