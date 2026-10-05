package com.menta.physical.application.port.out;

import com.menta.physical.application.dto.RateLimitDimension;

/** Observability hook invoked when a QR check-in is refused by a rate-limit budget (ADR-0044). */
public interface CheckInRateLimitRejectionPort {

    /** Records that the given budget refused a check-in. */
    void report(RateLimitDimension dimension);
}
