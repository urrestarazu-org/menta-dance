package com.menta.billing.infrastructure.web.dto;

import com.menta.billing.application.dto.CreatePlanCommand;
import com.menta.billing.domain.model.PaymentMethod;
import com.menta.billing.domain.model.PlanStatus;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Body of {@code POST /api/v1/admin/billing/plans} (#762). Every field is required; {@code
 * courseIds} may be an empty list.
 *
 * <p>Bean validation covers presence and the column bounds of {@code billing_plans} and {@code
 * billing_plan_courses} ({@code VARCHAR(255)} name, {@code DECIMAL(10,2)} price, {@code
 * VARCHAR(64)} course id, {@code TEXT} long texts), so an oversized value is a 400 instead of a
 * database error. The business rules — positive price, positive duration, ISO-4217 currency, at
 * least one payment method — stay in the use case and the domain, which this record never
 * duplicates. {@code status} and {@code paymentMethods} are the domain enums themselves: an
 * unknown value fails while decoding the body.</p>
 */
public record CreatePlanRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = MAX_TEXT_LENGTH) String description,
    @NotNull @Digits(integer = 8, fraction = 2) BigDecimal price,
    @NotBlank String currency,
    @NotNull Integer durationDays,
    @NotNull Boolean featured,
    @NotNull PlanStatus status,
    @NotBlank @Size(max = MAX_TEXT_LENGTH) String termsAndConditions,
    @NotBlank @Size(max = MAX_TEXT_LENGTH) String cancellationPolicy,
    @NotNull Set<@NotNull PaymentMethod> paymentMethods,
    @NotNull List<@NotBlank @Size(max = 64) String> courseIds
) {

    /** Fits a MySQL {@code TEXT} column (65,535 bytes) even at four bytes per character. */
    public static final int MAX_TEXT_LENGTH = 16_000;

    /** Builds the use-case command for the acting admin. */
    public CreatePlanCommand toCommand(UUID actingAdminId) {
        return new CreatePlanCommand(
            name, description, price, currency, durationDays, featured, status, termsAndConditions,
            cancellationPolicy, paymentMethods, courseIds, actingAdminId
        );
    }
}
