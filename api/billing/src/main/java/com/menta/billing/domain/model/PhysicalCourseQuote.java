package com.menta.billing.domain.model;

import com.menta.billing.domain.exception.IndividualSurchargeTooSmallException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable, persistible price quote for a physical course's MONTHLY or
 * INDIVIDUAL modality (US-BILLING-006). Snapshots the {@link
 * PhysicalCoursePricing} operands it was computed from — {@code
 * monthlyPrice}, {@code individualSurchargePercent}, {@code pricingVersion}
 * — so it can be audited or recomputed later, but never persists an
 * intermediate periodic quotient (e.g. {@code monthlyPrice /
 * scheduledSessionCount}) as if it were exact; only the final rounded {@code
 * amount} is stored.
 *
 * <p>Valid for exactly one hour from {@code createdAt} and never reserves
 * capacity — {@code availability} is purely informative for the UI; the
 * checkout flow (out of scope here) is what actually holds or rejects a
 * spot.</p>
 */
public final class PhysicalCourseQuote {

    private static final int MONETARY_SCALE = 2;
    private static final BigDecimal CURRENCY_MINOR_UNIT = new BigDecimal("0.01");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final MathContext HIGH_PRECISION = new MathContext(50, RoundingMode.HALF_UP);
    private static final Duration VALIDITY = Duration.ofHours(1);

    private final UUID id;
    private final String courseId;
    private final PurchaseType purchaseType;
    private final Money monthlyPrice;
    private final BigDecimal individualSurchargePercent;
    private final int pricingVersion;
    private final int scheduledSessionCount;
    private final String selectedSessionId;
    private final Money amount;
    private final QuoteAvailability availability;
    private final Instant createdAt;
    private final Instant expiresAt;

    private PhysicalCourseQuote(
        UUID id, String courseId, PurchaseType purchaseType, Money monthlyPrice,
        BigDecimal individualSurchargePercent, int pricingVersion, int scheduledSessionCount,
        String selectedSessionId, Money amount, QuoteAvailability availability, Instant createdAt, Instant expiresAt
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.courseId = Objects.requireNonNull(courseId, "courseId cannot be null");
        this.purchaseType = Objects.requireNonNull(purchaseType, "purchaseType cannot be null");
        this.monthlyPrice = Objects.requireNonNull(monthlyPrice, "monthlyPrice cannot be null");
        this.individualSurchargePercent =
            Objects.requireNonNull(individualSurchargePercent, "individualSurchargePercent cannot be null");
        this.pricingVersion = pricingVersion;
        if (scheduledSessionCount <= 0) {
            throw new IllegalArgumentException("scheduledSessionCount must be greater than zero");
        }
        this.scheduledSessionCount = scheduledSessionCount;
        this.selectedSessionId = selectedSessionId;
        this.amount = Objects.requireNonNull(amount, "amount cannot be null");
        this.availability = Objects.requireNonNull(availability, "availability cannot be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
    }

    /**
     * Reconstructs an already-persisted quote — used by the repository
     * adapter. Bypasses recomputation since every value was already computed
     * (and validated) once, at creation time.
     */
    public static PhysicalCourseQuote reconstitute(
        UUID id, String courseId, PurchaseType purchaseType, Money monthlyPrice,
        BigDecimal individualSurchargePercent, int pricingVersion, int scheduledSessionCount,
        String selectedSessionId, Money amount, QuoteAvailability availability, Instant createdAt, Instant expiresAt
    ) {
        return new PhysicalCourseQuote(
            id, courseId, purchaseType, monthlyPrice, individualSurchargePercent, pricingVersion,
            scheduledSessionCount, selectedSessionId, amount, availability, createdAt, expiresAt
        );
    }

    /**
     * Escenario 1: the monthly quote's amount is the pricing's monthly price
     * verbatim — no coverage or {@code sessionIds} are fixed; those are
     * calculated only when the payment is confirmed (out of scope here).
     */
    public static PhysicalCourseQuote monthly(
        String courseId, PhysicalCoursePricing pricing, int scheduledSessionCount, QuoteAvailability availability,
        Instant now
    ) {
        return new PhysicalCourseQuote(
            UUID.randomUUID(), courseId, PurchaseType.MONTHLY, pricing.getMonthlyPrice(),
            pricing.getIndividualSurchargePercent(), pricing.getVersion(), scheduledSessionCount, null,
            pricing.getMonthlyPrice(), availability, now, now.plus(VALIDITY)
        );
    }

    /**
     * Escenario 2: divides the monthly price by {@code scheduledSessionCount}
     * and applies the surcharge with high in-memory precision, then rounds
     * HALF_UP to two decimals for the final, persisted amount.
     *
     * <p>Business rule (confirmed decision): if the rounded individual price
     * does not exceed the rounded per-session price (no surcharge) by at
     * least one currency minor unit, the surcharge is invisible after
     * rounding and the quote is rejected — a real situation for a cheap
     * course with many monthly sessions and a small surcharge.</p>
     */
    public static PhysicalCourseQuote individual(
        String courseId, PhysicalCoursePricing pricing, int scheduledSessionCount, String selectedSessionId,
        QuoteAvailability availability, Instant now
    ) {
        BigDecimal perSessionHighPrecision = pricing.getMonthlyPrice().getAmount()
            .divide(BigDecimal.valueOf(scheduledSessionCount), HIGH_PRECISION);
        BigDecimal surchargeMultiplier =
            BigDecimal.ONE.add(pricing.getIndividualSurchargePercent().divide(ONE_HUNDRED, HIGH_PRECISION));
        BigDecimal individualPriceHighPrecision = perSessionHighPrecision.multiply(surchargeMultiplier);

        BigDecimal individualPriceRounded = individualPriceHighPrecision.setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
        BigDecimal effectiveRounded = perSessionHighPrecision.setScale(MONETARY_SCALE, RoundingMode.HALF_UP);

        if (individualPriceRounded.compareTo(effectiveRounded.add(CURRENCY_MINOR_UNIT)) < 0) {
            // Escenario límite: the surcharge disappears after rounding — reject before persisting anything.
            throw new IndividualSurchargeTooSmallException();
        }

        Money amount = Money.of(individualPriceRounded, pricing.getMonthlyPrice().getCurrency());
        return new PhysicalCourseQuote(
            UUID.randomUUID(), courseId, PurchaseType.INDIVIDUAL, pricing.getMonthlyPrice(),
            pricing.getIndividualSurchargePercent(), pricing.getVersion(), scheduledSessionCount, selectedSessionId,
            amount, availability, now, now.plus(VALIDITY)
        );
    }

    public UUID getId() {
        return id;
    }

    public String getCourseId() {
        return courseId;
    }

    public PurchaseType getPurchaseType() {
        return purchaseType;
    }

    public Money getMonthlyPrice() {
        return monthlyPrice;
    }

    public BigDecimal getIndividualSurchargePercent() {
        return individualSurchargePercent;
    }

    public int getPricingVersion() {
        return pricingVersion;
    }

    public int getScheduledSessionCount() {
        return scheduledSessionCount;
    }

    public String getSelectedSessionId() {
        return selectedSessionId;
    }

    public Money getAmount() {
        return amount;
    }

    public QuoteAvailability getAvailability() {
        return availability;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
