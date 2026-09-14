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

    PhysicalCourseQuote save(PhysicalCourseQuote quote);

    Optional<PhysicalCourseQuote> findById(String quoteId);
}
