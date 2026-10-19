package com.menta.app.billing;

import com.menta.billing.domain.exception.PhysicalCapacityUnavailableException;
import com.menta.physical.domain.exception.CapacityBelowAssignedException;
import com.menta.shared.physical.MultiSessionCapacityHoldCommand;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implements Billing's {@code
 * com.menta.billing.application.port.out.PhysicalCapacityHoldPort} out port
 * by calling Physical's entry port directly (#208, US-PHYSICAL-004b, design
 * D4) — same cross-module composition pattern as {@link
 * PhysicalCourseAvailabilityAdapter}: a plain Java call inside {@code
 * api:app}, never HTTP, RabbitMQ or a shared schema (ADR-0037).
 *
 * <p>Both modules expose a type named {@code PhysicalCapacityHoldPort}
 * (Billing's out port and Physical's in port) — fully qualified here since
 * they cannot both be imported under the same simple name, same convention
 * {@link PhysicalCourseAvailabilityAdapter} already established for {@code
 * PhysicalCourseAvailabilityPort}.</p>
 *
 * <p>Translates Physical's {@link CapacityBelowAssignedException} — thrown
 * when a hold claim trips the {@code assigned + activeHolds + 1 > capacity}
 * invariant (design B4) — into Billing's own {@link
 * PhysicalCapacityUnavailableException}, the same {@code 409
 * CAPACITY_UNAVAILABLE} shape the checkout endpoint already returns
 * (design D2). This is the one point where a Physical-owned exception type
 * must not cross the module boundary: {@code
 * CreatePhysicalPurchaseCheckoutUseCaseImpl} is structurally forbidden from
 * importing {@code com.menta.physical..} (see {@code
 * BillingArchitectureTest}), so the translation has to happen here, inside
 * {@code api:app}, which is allowed to see both modules.</p>
 */
@Component
public class PhysicalCapacityHoldAdapter
    implements com.menta.billing.application.port.out.PhysicalCapacityHoldPort {

    private final com.menta.physical.application.port.in.PhysicalCapacityHoldPort physicalCapacityHoldPort;

    public PhysicalCapacityHoldAdapter(
        com.menta.physical.application.port.in.PhysicalCapacityHoldPort physicalCapacityHoldPort
    ) {
        this.physicalCapacityHoldPort = physicalCapacityHoldPort;
    }

    @Override
    public List<UUID> hold(MultiSessionCapacityHoldCommand command, Instant expiresAt) {
        try {
            return physicalCapacityHoldPort.holdAll(command, expiresAt).heldSessionIds();
        } catch (CapacityBelowAssignedException capacityUnavailable) {
            throw new PhysicalCapacityUnavailableException();
        }
    }

    @Override
    public void release(UUID paymentId) {
        physicalCapacityHoldPort.release(paymentId);
    }
}
