package com.menta.virtual.infrastructure.transaction;

import com.menta.virtual.application.port.in.DeleteVirtualLessonUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional decorator (#830). The delegate deletes the lesson's progress rows, then the lesson, then
 * renumbers the remaining lessons and writes the audit row, as separate repository calls: without this
 * wrapper a failure partway through could leave a module with a gap in its order, or a lesson gone with no
 * audit trail. See {@link TransactionalCreateVirtualCourseUseCase} for the general rationale.
 */
@RequiredArgsConstructor
public class TransactionalDeleteVirtualLessonUseCase implements DeleteVirtualLessonUseCase {

    private final DeleteVirtualLessonUseCase delegate;

    @Override
    @Transactional
    public void delete(String lessonId, UUID actingUserId, boolean actingAsAdmin) {
        delegate.delete(lessonId, actingUserId, actingAsAdmin);
    }
}
