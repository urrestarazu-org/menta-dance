package com.menta.billing.infrastructure.time;

import com.menta.billing.application.port.out.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Production {@link Clock} — mirrors {@code auth}'s identical adapter. Named
 * distinctly (not {@code SystemClock}) because both modules' components are
 * scanned into the same Spring context ({@code api:app}) and Spring's
 * default bean-name-by-simple-class-name would otherwise collide with
 * {@code auth}'s own {@code SystemClock}.
 */
@Component
public class BillingSystemClock implements Clock {

    @Override
    public Instant now() {
        return Instant.now();
    }
}
