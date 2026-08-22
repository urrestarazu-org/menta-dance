package com.menta.billing.application.port.out;

import com.menta.billing.domain.model.PhysicalCourseQuote;

/**
 * Persistence port for {@link PhysicalCourseQuote} (US-BILLING-006). A quote
 * is a single immutable insert — no {@code findById}/read port is exposed
 * here since this issue only requires the creation endpoint; reading back an
 * already-created quote is a different, later story.
 */
public interface PhysicalCourseQuoteRepository {

    PhysicalCourseQuote save(PhysicalCourseQuote quote);
}
