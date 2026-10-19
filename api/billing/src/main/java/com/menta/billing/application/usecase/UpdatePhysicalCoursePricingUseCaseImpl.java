package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.PhysicalCoursePricingResult;
import com.menta.billing.application.dto.UpdatePhysicalCoursePricingCommand;
import com.menta.billing.application.port.in.UpdatePhysicalCoursePricingUseCase;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.PhysicalCourseOwnershipPort;
import com.menta.billing.application.port.out.PhysicalCoursePricingRepository;
import com.menta.billing.application.port.out.PhysicalCoursePricingRevisionRepository;
import com.menta.billing.domain.exception.PhysicalCourseNotFoundException;
import com.menta.billing.domain.exception.PricingNotOwnedException;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.PhysicalCoursePricing;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link UpdatePhysicalCoursePricingUseCase} (US-BILLING-009
 * escenario 1). Ownership is resolved through {@link
 * PhysicalCourseOwnershipPort} — a cross-module out port {@code api:app}
 * implements by calling Physical's entry port directly, never HTTP.
 *
 * <p>{@link PhysicalCoursePricing}'s constructor validates {@code
 * individualSurchargePercent} before this method ever calls {@code save} or
 * {@code append} — escenario 2 requires that an invalid surcharge creates no
 * revision at all.</p>
 */
public class UpdatePhysicalCoursePricingUseCaseImpl implements UpdatePhysicalCoursePricingUseCase {

    private static final String ACTION = "UPDATE_PRICING";

    private final PhysicalCourseOwnershipPort ownershipPort;
    private final PhysicalCoursePricingRepository pricingRepository;
    private final PhysicalCoursePricingRevisionRepository revisionRepository;
    private final Clock clock;

    public UpdatePhysicalCoursePricingUseCaseImpl(
        PhysicalCourseOwnershipPort ownershipPort, PhysicalCoursePricingRepository pricingRepository,
        PhysicalCoursePricingRevisionRepository revisionRepository, Clock clock
    ) {
        this.ownershipPort = ownershipPort;
        this.pricingRepository = pricingRepository;
        this.revisionRepository = revisionRepository;
        this.clock = clock;
    }

    @Override
    public PhysicalCoursePricingResult update(
        String courseId, UpdatePhysicalCoursePricingCommand command, UUID actingUserId, boolean actingAsAdmin
    ) {
        UUID professorId = ownershipPort.findProfessorId(courseId).orElseThrow(PhysicalCourseNotFoundException::new);
        if (!actingAsAdmin && !professorId.equals(actingUserId)) {
            throw new PricingNotOwnedException();
        }

        Instant now = clock.now();
        Money monthlyPrice = Money.of(command.monthlyPrice(), command.currency());
        Optional<PhysicalCoursePricing> existing = pricingRepository.findByCourseId(courseId);
        String previousSnapshot = existing.map(PhysicalCoursePricingResultMapper::toAuditSnapshot).orElse(null);

        // Constructed before any persistence call — an invalid surcharge throws here,
        // never touching pricingRepository or revisionRepository (escenario 2).
        PhysicalCoursePricing updated = existing
            .map(current -> current.revise(monthlyPrice, command.individualSurchargePercent(), now))
            .orElseGet(() -> PhysicalCoursePricing.createFirstVersion(
                courseId, monthlyPrice, command.individualSurchargePercent(), now
            ));

        PhysicalCoursePricing saved = pricingRepository.save(updated);
        revisionRepository.append(
            courseId, actingUserId, command.reason(), saved.getVersion(), previousSnapshot,
            PhysicalCoursePricingResultMapper.toAuditSnapshot(saved), now
        );
        return PhysicalCoursePricingResultMapper.toResult(saved);
    }
}
