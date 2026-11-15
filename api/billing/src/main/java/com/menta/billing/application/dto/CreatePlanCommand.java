package com.menta.billing.application.dto;

import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.PlanStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Input of {@code CreatePlanUseCase} (#762): every field of a new plan, already decoded by the
 * web layer, plus the acting admin. The plan id is never part of it — the server generates one.
 *
 * <p>Presence of each field is the web layer's job; the business rules (positive price, positive
 * duration, at least one payment method, non-blank course ids) are checked by the use case and
 * the domain {@code Plan}.</p>
 */
public record CreatePlanCommand(
    String name,
    String description,
    BigDecimal price,
    String currency,
    int durationDays,
    boolean featured,
    PlanStatus status,
    String termsAndConditions,
    String cancellationPolicy,
    Set<PaymentMethod> paymentMethods,
    List<String> courseIds,
    UUID actingAdminId
) {
}
