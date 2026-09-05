package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.LessonAccessDecision;
import com.menta.virtual.application.dto.LessonProgressView;
import com.menta.virtual.application.port.in.SaveLessonProgressUseCase;
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
import java.time.Instant;
import java.util.UUID;

/**
 * Default {@link SaveLessonProgressUseCase} (US-VIRTUAL-005). Reuses the same access cascade as
 * lesson detail/stream ({@link LessonAccessPolicy}) and clamps the position bound at write time
 * using the lesson's duration at that moment (design's "stale position after lesson edit" rule).
 * Plain class, composed in {@code VirtualConfiguration} — never a bean itself, always wrapped by
 * its transactional and retry decorators.
 */
public class SaveLessonProgressUseCaseImpl implements SaveLessonProgressUseCase {

    private final VirtualLessonRepository lessonRepository;
    private final VirtualModuleRepository moduleRepository;
    private final LessonProgressRepository progressRepository;
    private final LessonAccessPolicy accessPolicy;
    private final Clock clock;

    public SaveLessonProgressUseCaseImpl(
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
    public LessonProgressView save(String lessonId, UUID actingUserId, int positionSeconds) {
        LessonId id = LessonProgressAccess.parseLessonIdOrThrow(lessonId);
        VirtualLesson lesson = lessonRepository.findById(id).orElseThrow(LessonNotFoundException::new);
        VirtualModule module = moduleRepository.findById(lesson.getModuleId())
            .orElseThrow(LessonNotFoundException::new);
        if (accessPolicy.decide(lesson, module, actingUserId) == LessonAccessDecision.SUBSCRIPTION_REQUIRED) {
            throw new ForbiddenLessonAccessException();
        }

        int maxSeconds = lesson.getDurationMinutes() * 60;
        Instant now = clock.now();
        LessonProgress existing = progressRepository.findByUserIdAndLessonId(actingUserId, id)
            .orElseGet(() -> LessonProgress.start(actingUserId, id, lesson.getCourseId()));
        LessonProgress saved = progressRepository.save(existing.withPosition(positionSeconds, maxSeconds, now));
        return LessonProgressViewMapper.from(saved);
    }
}
