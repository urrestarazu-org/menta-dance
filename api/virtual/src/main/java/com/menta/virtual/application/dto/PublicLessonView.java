package com.menta.virtual.application.dto;

/**
 * Sealed parent for the three access outcomes of the public lesson read
 * (US-VIRTUAL-003). The use case returns one of these three concrete
 * shapes; the controller translates each into a matching REST response
 * (200 OK with {@code public_lesson_response} subtype, 200 OK with
 * {@code public_lesson_requires_subscription_response}, 403 via
 * {@link com.menta.virtual.domain.exception.ForbiddenLessonAccessException}).
 *
 * <p>{@code Optional.empty()} from
 * {@link com.menta.virtual.application.port.in.GetPublicLessonUseCase} maps
 * to {@link com.menta.virtual.domain.exception.LessonNotFoundException} — the
 * controller throws it lazily so the handler can apply a uniform 404.</p>
 */
public sealed interface PublicLessonView
    permits PublicLessonFreeView,
            PublicLessonPremiumAccessibleView,
            PublicLessonRequiresSubscriptionView {
}
