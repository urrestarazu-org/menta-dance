package com.menta.virtual.application.dto;

/**
 * Access-decision block surfaced when the visitor is authenticated and
 * eligible to upgrade. The {@code reason} is a stable machine string
 * that other modules (BFF / Android) can key off; the {@code message} is
 * the user-facing copy that the frontend is allowed to render as-is or
 * translate. The {@code plansUrl} always points at billing — there is no
 * embedded checkout.
 *
 * <p>Application-layer name is {@code LessonAccessDecisionDto} so it does
 * not collide with the wire-level {@code PublicLessonAccessDto} in
 * {@code com.menta.virtual.infrastructure.web.dto}; both records carry
 * the same JSON shape, with the wire DTO being a JSON-friendly mirror
 * over this one.</p>
 */
public record LessonAccessDecisionDto(
    boolean allowed,
    String reason,
    String message,
    String plansUrl
) {

    public static LessonAccessDecisionDto requiresSubscription(String plansUrl) {
        return new LessonAccessDecisionDto(
            false,
            "SUBSCRIPTION_REQUIRED",
            "Esta lección requiere una suscripción activa",
            plansUrl
        );
    }
}
