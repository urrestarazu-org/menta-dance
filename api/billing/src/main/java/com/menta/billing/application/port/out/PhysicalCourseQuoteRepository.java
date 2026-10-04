package com.menta.billing.application.port.out;

import com.menta.billing.domain.model.PhysicalCourseQuote;
import java.util.Optional;

/**
 * Persistence port for {@link PhysicalCourseQuote} (US-BILLING-006).
 *
 * <p>{@link #findById(String)} was added by #41's PR6: the outbox handler
 * resolves {@code PaymentTarget.Physical}'s reference (the quoteId, design
 * A5) back into the concrete quote so it can run Billing's {@code
 * CoveragePlanner}. Reading back a quote for any other purpose remains out
 * of scope.</p>
 */
public interface PhysicalCourseQuoteRepository {

    /** Persists the quote under its own id and returns the stored state. */
    PhysicalCourseQuote save(PhysicalCourseQuote quote);

    /**
     * Finds a quote by its id.
     *
     * <p>Expired quotes are returned too, so callers must check {@code getExpiresAt()} themselves,
     * as the checkout use cases do.
     */
    Optional<PhysicalCourseQuote> findById(String quoteId);
}
