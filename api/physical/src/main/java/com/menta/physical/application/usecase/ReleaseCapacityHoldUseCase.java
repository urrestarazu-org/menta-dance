package com.menta.physical.application.usecase;

import com.menta.physical.application.port.out.PhysicalCapacityHoldWriter;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Releases every hold row for a payment (#208, US-PHYSICAL-004b, design
 * step 3). One {@code REQUIRES_NEW} transaction, delegated whole to
 * {@link PhysicalCapacityHoldWriter#release}, which is already a no-op on
 * an unknown payment id — {@code findByPaymentIdOrdered} returns an empty
 * list and {@code deleteAll} on it does nothing.
 */
@Component
public class ReleaseCapacityHoldUseCase {

    private final PhysicalCapacityHoldWriter holdWriter;

    public ReleaseCapacityHoldUseCase(PhysicalCapacityHoldWriter holdWriter) {
        this.holdWriter = holdWriter;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void release(UUID paymentId) {
        holdWriter.release(paymentId);
    }
}
