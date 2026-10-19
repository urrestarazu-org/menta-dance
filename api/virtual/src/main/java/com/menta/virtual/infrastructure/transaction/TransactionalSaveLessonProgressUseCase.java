package com.menta.virtual.infrastructure.transaction;

import com.menta.virtual.application.dto.LessonProgressView;
import com.menta.virtual.application.port.in.SaveLessonProgressUseCase;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Transactional decorator — see {@code TransactionalCreateVirtualCourseUseCase} for the rationale. */
public class TransactionalSaveLessonProgressUseCase implements SaveLessonProgressUseCase {

    private final SaveLessonProgressUseCase delegate;

    public TransactionalSaveLessonProgressUseCase(SaveLessonProgressUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public LessonProgressView save(String lessonId, UUID actingUserId, int positionSeconds) {
        return delegate.save(lessonId, actingUserId, positionSeconds);
    }
}
