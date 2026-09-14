package com.menta.app.billing;

import com.menta.physical.application.port.in.PhysicalCapacityAssignmentPort;
import com.menta.physical.application.usecase.AssignmentOutcome;
import com.menta.physical.application.usecase.CapacityAssignments;
import com.menta.shared.physical.CapacityAssignmentCommand;
import com.menta.shared.physical.MultiSessionCapacityAssignmentCommand;
import org.springframework.stereotype.Component;

/**
 * Typed callable inside {@code api:app} that delegates the cross-module
 * call into Physical's {@link PhysicalCapacityAssignmentPort}. Mirrors the
 * pattern of {@code PhysicalCourseAvailabilityAdapter}: a single constructor
 * and delegate call keep the cross-module wiring free of HTTP, JPA, and SQL
 * crossover at the {@code api:app} boundary (ADR-0037).
 */
@Component
public class PhysicalCapacityAssignmentAdapter {

    private final PhysicalCapacityAssignmentPort physicalCapacityAssignmentPort;

    public PhysicalCapacityAssignmentAdapter(
        PhysicalCapacityAssignmentPort physicalCapacityAssignmentPort
    ) {
        this.physicalCapacityAssignmentPort = physicalCapacityAssignmentPort;
    }

    /**
     * Assigns capacity through Physical's public application boundary.
     */
    public AssignmentOutcome assign(CapacityAssignmentCommand command) {
        return physicalCapacityAssignmentPort.assign(command);
    }

    /**
     * Ordered, all-or-nothing multi-session counterpart of {@link #assign}
     * (design A3) — added by #41 PR6 so the outbox handler can claim every
     * session a {@code MONTHLY} or {@code INDIVIDUAL} purchase covers in one
     * call.
     */
    public CapacityAssignments assignAll(MultiSessionCapacityAssignmentCommand command) {
        return physicalCapacityAssignmentPort.assignAll(command);
    }
}
