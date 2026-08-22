package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.CreatePhysicalCourseQuoteCommand;
import com.menta.billing.application.dto.PhysicalCourseQuoteResult;

/** Entry point for creating a physical course quote (US-BILLING-006). */
public interface CreatePhysicalCourseQuoteUseCase {

    PhysicalCourseQuoteResult create(CreatePhysicalCourseQuoteCommand command);
}
