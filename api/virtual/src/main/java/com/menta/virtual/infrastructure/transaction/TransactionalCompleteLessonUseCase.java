package com.menta.virtual.infrastructure.transaction;

import com.menta.virtual.application.dto.LessonProgressView;
import com.menta.virtual.application.port.in.CompleteLessonUseCase;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/** Transactional decorator — see {@code TransactionalCreateVirtualCourseUseCase} for the rationale. */
public class TransactionalCompleteLessonUseCase implements CompleteLessonUseCase {

    private final CompleteLessonUseCase delegate;

    public TransactionalCompleteLessonUseCase(CompleteLessonUseCase delegate) {
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public LessonProgressView complete(String lessonId, UUID actingUserId) {
        return delegate.complete(lessonId, actingUserId);
    }
}
