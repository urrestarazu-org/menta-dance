package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.LessonAccessDecision;
import com.menta.virtual.application.dto.LessonProgressView;
import com.menta.virtual.application.port.in.CompleteLessonUseCase;
import com.menta.virtual.application.port.out.Clock;
import com.menta.virtual.application.port.out.LessonProgressRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.ForbiddenLessonAccessException;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.LessonProgress;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.UUID;

/**
 * Default {@link CompleteLessonUseCase} (US-VIRTUAL-005). Never moves the saved position — see
 * {@link LessonProgress#markCompleted} — and repeating is a no-op (decision 7). Plain class,
 * composed in {@code VirtualConfiguration}, always wrapped by its decorators.
 */
public class CompleteLessonUseCaseImpl implements CompleteLessonUseCase {

    private final VirtualLessonRepository lessonRepository;
    private final VirtualModuleRepository moduleRepository;
    private final LessonProgressRepository progressRepository;
    private final LessonAccessPolicy accessPolicy;
    private final Clock clock;

    public CompleteLessonUseCaseImpl(
        VirtualLessonRepository lessonRepository, VirtualModuleRepository moduleRepository,
        LessonProgressRepository progressRepository, LessonAccessPolicy accessPolicy, Clock clock
    ) {
        this.lessonRepository = lessonRepository;
        this.moduleRepository = moduleRepository;
        this.progressRepository = progressRepository;
        this.accessPolicy = accessPolicy;
        this.clock = clock;
    }

    @Override
    public LessonProgressView complete(String lessonId, UUID actingUserId) {
        LessonId id = LessonProgressAccess.parseLessonIdOrThrow(lessonId);
        VirtualLesson lesson = lessonRepository.findById(id).orElseThrow(LessonNotFoundException::new);
        VirtualModule module = moduleRepository.findById(lesson.getModuleId())
            .orElseThrow(LessonNotFoundException::new);
        if (accessPolicy.decide(lesson, module, actingUserId) == LessonAccessDecision.SUBSCRIPTION_REQUIRED) {
            throw new ForbiddenLessonAccessException();
        }

        LessonProgress existing = progressRepository.findByUserIdAndLessonId(actingUserId, id)
            .orElseGet(() -> LessonProgress.start(actingUserId, id, lesson.getCourseId()));
        LessonProgress saved = progressRepository.save(existing.markCompleted(clock.now()));
        return LessonProgressViewMapper.from(saved);
    }
}
