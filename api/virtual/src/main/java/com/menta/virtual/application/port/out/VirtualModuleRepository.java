package com.menta.virtual.application.port.out;

import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.List;
import java.util.Optional;

/** Persistence port for {@link VirtualModule}. */
public interface VirtualModuleRepository {

    Optional<VirtualModule> findById(ModuleId moduleId);

    /** Every module of a course, ordered by {@code order} ascending. */
    List<VirtualModule> findByCourseId(CourseId courseId);

    int countByCourseId(CourseId courseId);

    VirtualModule save(VirtualModule module);

    void saveAll(List<VirtualModule> modules);

    /**
     * Deletes every module of a course. Used when deleting a {@code DRAFT}
     * course — {@code virtual_modules.course_id} has a {@code NOT NULL} FK
     * with no {@code ON DELETE CASCADE}, so a course with modules must have
     * them removed first or the delete fails with a constraint violation.
     */
    void deleteByCourseId(CourseId courseId);
}
