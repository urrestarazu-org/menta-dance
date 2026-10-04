package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.CreatePhysicalCourseQuoteCommand;
import com.menta.billing.application.dto.PhysicalCourseQuoteResult;

/** Entry point for creating a physical course quote (US-BILLING-006). */
public interface CreatePhysicalCourseQuoteUseCase {

    /**
     * Creates and stores a priced quote for a physical course.
     *
     * <p>A {@code MONTHLY} quote must not name a session and an {@code INDIVIDUAL} one must,
     * otherwise {@code SelectedSessionNotAllowedException} or {@code
     * SelectedSessionRequiredException} is thrown. A course whose pricing was never published fails
     * with {@code PhysicalCoursePricingNotFoundException}, a month without scheduled sessions with
     * {@code NoScheduledSessionsException}, and an individual quote whose session is not found with
     * {@code PhysicalSessionNotFoundException}. Periods are UTC calendar months fixed by the server
     * clock, never by the client.
     */
    PhysicalCourseQuoteResult create(CreatePhysicalCourseQuoteCommand command);
}
