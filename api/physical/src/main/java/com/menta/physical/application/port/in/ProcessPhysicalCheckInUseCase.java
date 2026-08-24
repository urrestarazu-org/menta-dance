package com.menta.physical.application.port.in;

import com.menta.physical.application.dto.CheckInCommand;
import com.menta.physical.application.dto.CheckInResult;

/**
 * Entry point the door reader calls to redeem a scanned QR for attendance
 * (US-PHYSICAL-001 escenario 2). {@code POST
 * /api/v1/physical/sessions/{sessionId}/check-ins} is {@code permitAll()} at
 * the security-filter level — the reader authenticates with a shared
 * device token, not a user session — so every authorization decision this
 * flow makes happens inside the use case, not the filter chain.
 */
public interface ProcessPhysicalCheckInUseCase {

    CheckInResult checkIn(CheckInCommand command);
}
