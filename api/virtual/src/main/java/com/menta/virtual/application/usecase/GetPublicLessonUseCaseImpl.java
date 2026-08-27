package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.LessonAccessDecision;
import com.menta.virtual.application.dto.LessonAccessDecisionDto;
import com.menta.virtual.application.dto.PublicCourseRef;
import com.menta.virtual.application.dto.PublicLessonDetailView;
import com.menta.virtual.application.dto.PublicLessonFreeView;
import com.menta.virtual.application.dto.PublicLessonNavigation;
import com.menta.virtual.application.dto.PublicLessonNavigationRef;
import com.menta.virtual.application.dto.PublicLessonPremiumAccessibleView;
import com.menta.virtual.application.dto.PublicLessonPreviewView;
import com.menta.virtual.application.dto.PublicLessonRequiresSubscriptionView;
import com.menta.virtual.application.dto.PublicLessonSubscriptionPrompt;
import com.menta.virtual.application.dto.PublicLessonView;
import com.menta.virtual.application.dto.PublicModuleRef;
import com.menta.virtual.application.port.in.GetPublicLessonUseCase;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.port.out.VirtualLessonRepository;
import com.menta.virtual.application.port.out.VirtualModuleRepository;
import com.menta.virtual.domain.exception.ForbiddenLessonAccessException;
import com.menta.virtual.domain.exception.LessonNotFoundException;
import com.menta.virtual.domain.model.CourseId;
import com.menta.virtual.domain.model.LessonId;
import com.menta.virtual.domain.model.ModuleId;
import com.menta.virtual.domain.model.VirtualCourse;
import com.menta.virtual.domain.model.VirtualLesson;
import com.menta.virtual.domain.model.VirtualModule;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default {@link GetPublicLessonUseCase}. Walks the domain hierarchy
 * lesson → module → course, decides the access shape, and — when the
 * decay pathway through entitlement returns negative — folds the three
 * outcomes into the {@link PublicLessonView} tree.
 *
 * <p>Stage-by-stage:</p>
 * <ol>
 *   <li>parse the path variable into a {@link LessonId}, swallowing
 *       {@link IllegalArgumentException} so a malformed UUID looks
 *       identical to "not found" to the caller — the anti-enumeration
 *       discipline also requires that, mirroring
 *       {@link VirtualCourseCatalogPortImpl#findByIdForAdmin(String)};</li>
 *   <li>load the lesson — absent row ⇒ {@code Optional.empty()};</li>
 *   <li>load the parent module — invariant: a lesson without a module
 *       would have failed the FK constraint on insert, so an empty
 *       module lookup is treated as a data inconsistency and surfaces as
 *       {@code Optional.empty()} too;</li>
 *   <li>load the parent course via
 *       {@link VirtualCourseRepository#findPublishedById(CourseId)} —
 *       un-flagged by status just like the public catalog path (#124
 *       US-VIRTUAL-002 escenario 1). A {@code DRAFT} or {@code ARCHIVED}
 *       course produces the same 404 as a missing lesson; the use does
 *       not currently distinguish, deliberate trade-off documented at the
 *       port level;</li>
 *   <li>compute {@code canSeeVideo} and the navigation pointers:</li>
 * </ol>
 *
 * <p><em>Navigation:</em> the previous / next pointers come from
 * {@link VirtualLessonRepository#findByModuleId(ModuleId)} — already
 * ordered ASC by the persistence layer, so an O(N) scan against the
 * list of siblings is acceptable. A {@code findAdjacentByModuleId}
 * shortcut was considered; for the typical module size (≤ 12 lessons)
 * the difference would be measured in microseconds while the code
 * stays clearer with the single repository call.</p>
 *
 * <p><em>Entitlement:</em> the billing port is {@code *only*} consulted
 * when {@code lesson.isFree() == false}. Free lessons short-circuit to
 * {@link PublicLessonFreeView} so the billing dependency does not leak
 * into the hottest read path; this is also why every test asserts
 * {@code verify(entitlement, never()).hasActiveEntitlement(...)}
 * on free branches.</p>
 */
@Component
public class GetPublicLessonUseCaseImpl implements GetPublicLessonUseCase {

    private static final Logger LOG = LoggerFactory.getLogger(GetPublicLessonUseCaseImpl.class);

    private static final String BILLING_PLANS_URL = "/api/v1/billing/plans";

    private static final String FREE_UPGRADE_PROMPT =
        "¡Suscríbete para acceder a todas las lecciones!";
    private static final String SUBSCRIPTION_GATE_PROMPT =
        "Esta lección requiere una suscripción activa";

    private final VirtualLessonRepository lessonRepository;
    private final VirtualModuleRepository moduleRepository;
    private final VirtualCourseRepository courseRepository;
    private final LessonAccessPolicy accessPolicy;

    public GetPublicLessonUseCaseImpl(
        VirtualLessonRepository lessonRepository,
        VirtualModuleRepository moduleRepository,
        VirtualCourseRepository courseRepository,
        LessonAccessPolicy accessPolicy
    ) {
        this.lessonRepository = lessonRepository;
        this.moduleRepository = moduleRepository;
        this.courseRepository = courseRepository;
        this.accessPolicy = accessPolicy;
    }

    @Override
    public Optional<PublicLessonView> get(String lessonId, UUID actingUserId) {
        LessonId id = parseLessonId(lessonId);
        if (id == null) {
            return Optional.empty();
        }

        VirtualLesson lesson = lessonRepository.findById(id).orElse(null);
        if (lesson == null) {
            return Optional.empty();
        }

        VirtualModule module = moduleRepository.findById(lesson.getModuleId()).orElse(null);
        if (module == null) {
            // FK invariant: virtual_lessons.module_id NOT NULL without ON DELETE SET NULL,
            // and we never delete a module with lessons attached. Treat as data drift anyway.
            LOG.warn("lesson {} resolved to module {} but the module lookup was empty", id, lesson.getModuleId());
            return Optional.empty();
        }

        VirtualCourse course = courseRepository.findPublishedById(lesson.getCourseId()).orElse(null);
        if (course == null) {
            // The lesson exists but its parent course is DRAFT or ARCHIVED — collapse to 404
            // so a public visitor cannot enumerate hidden courses via their (logged-out)
            // lesson paths. Same anti-enumeration discipline this module already applies in
            // VirtualCourseCatalogPortImpl#findPublishedById(String).
            LOG.debug("lesson {} belongs to non-published course {} — returning empty", id, lesson.getCourseId());
            return Optional.empty();
        }

        PublicLessonNavigation navigation = navigationOf(lesson, lessonRepository.findByModuleId(module.getId()));
        PublicCourseRef courseRef = PublicCourseRef.of(course.getId(), course.getTitle());
        PublicModuleRef moduleRef = PublicModuleRef.of(module.getId(), module.getTitle());
        String thumbnailUrl = course.getImageUrl();
        String duration = formatDuration(lesson.getDurationMinutes());

        LessonAccessDecision accessDecision = accessPolicy.decide(lesson, module, actingUserId);
        if (accessDecision == LessonAccessDecision.SUBSCRIPTION_REQUIRED) {
            throw new ForbiddenLessonAccessException();
        }

        if (accessDecision == LessonAccessDecision.PUBLIC_FREE) {
            PublicLessonDetailView detail = PublicLessonDetailView.withoutVideoId(
                lesson.getId().toString(), lesson.getTitle(), lesson.getDescription(),
                duration, true, lesson.getOrder(),
                moduleRef, courseRef, thumbnailUrl
            );
            return Optional.of(new PublicLessonFreeView(
                detail, navigation, new PublicLessonSubscriptionPrompt(FREE_UPGRADE_PROMPT, BILLING_PLANS_URL)
            ));
        }

        PublicLessonDetailView detail = PublicLessonDetailView.withVideoId(
            lesson.getId().toString(), lesson.getTitle(), lesson.getDescription(),
            duration, false, lesson.getOrder(),
            moduleRef, courseRef, lesson.getVideoId(), thumbnailUrl
        );
        return Optional.of(new PublicLessonPremiumAccessibleView(detail, navigation));
    }

    private static LessonId parseLessonId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LessonId.of(raw);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
    }

    private static PublicLessonNavigation navigationOf(
        VirtualLesson target, List<VirtualLesson> siblings
    ) {
        // siblings is already ASC by displayOrder, so locate the current
        // lesson position and pick the contiguous neighbours.
        int targetIdx = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (siblings.get(i).getId().equals(target.getId())) {
                targetIdx = i;
                break;
            }
        }
        if (targetIdx < 0) {
            // Module scan came back without the target — defensive only,
            // since target came out of the same module lookup. Surface empty
            // pointers rather than throw: any error visible to the caller
            // here would leak the same anti-enumeration leak we just closed.
            return new PublicLessonNavigation(null, null);
        }
        PublicLessonNavigationRef prev = targetIdx > 0
            ? PublicLessonNavigationRef.of(
                siblings.get(targetIdx - 1).getId(),
                siblings.get(targetIdx - 1).getTitle(),
                siblings.get(targetIdx - 1).isFree()
            )
            : null;
        PublicLessonNavigationRef next = targetIdx < siblings.size() - 1
            ? PublicLessonNavigationRef.of(
                siblings.get(targetIdx + 1).getId(),
                siblings.get(targetIdx + 1).getTitle(),
                siblings.get(targetIdx + 1).isFree()
            )
            : null;
        return new PublicLessonNavigation(prev, next);
    }

    /**
     * Render {@code durationMinutes} as {@code mm:ss}. The DB stores whole
     * minutes only (see {@code V6__virtual_courses.sql}), so the seconds
     * slot is always zero — but the format is {@code mm:ss} so a future
     * migration that starts tracking seconds can swap the formatter
     * without touching contract callers.
     */
    private static String formatDuration(int durationMinutes) {
        return String.format(Locale.ROOT, "%02d:%02d", durationMinutes, 0);
    }

    /**
     * Swallow {@link LessonNotFoundException} silently — this path is only
     * reachable through the type system in tests; in production the
     * controller maps {@code Optional.empty()} into the exception. Kept
     * here so an explicit {@code throws} on the port contract does not
     * stall callers that want to {@code .map(...)} on the result.
     */
    @SuppressWarnings("unused")
    private static void unused(LessonNotFoundException ex) {
        // intentionally empty
    }
}
