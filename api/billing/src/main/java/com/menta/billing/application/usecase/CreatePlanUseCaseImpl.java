package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.CreatePlanCommand;
import com.menta.billing.application.dto.PlanDetailResult;
import com.menta.billing.application.port.in.CreatePlanUseCase;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.domain.model.Money;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanCourse;
import com.menta.billing.domain.model.PlanId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link CreatePlanUseCase} (#762).
 *
 * <p>The domain {@code Plan}, {@code Money} and {@code PlanCourse} already reject a non-positive
 * duration, an empty payment-method set, a malformed currency and a blank course id; this class
 * adds the one creation rule they do not hold: the price must be strictly positive. {@code Money}
 * deliberately accepts zero, and plans already stored with that price must keep reading, so the
 * rule belongs to creation rather than to the domain type.</p>
 *
 * <p>No transaction here: the adapter writes the plan and its rows atomically on its own, and the
 * course names are resolved afterwards, outside any transaction, for the reason {@link
 * PlanCourseResolver} documents. There is no audit table for plans, so the creation is recorded
 * with one INFO log line naming the plan and the admin.</p>
 */
@RequiredArgsConstructor
public class CreatePlanUseCaseImpl implements CreatePlanUseCase {

    private static final Logger log = LoggerFactory.getLogger(CreatePlanUseCaseImpl.class);

    private final PlanRepository planRepository;
    private final CourseCatalogPort courseCatalogPort;
    private final Clock clock;

    @Override
    public PlanDetailResult create(CreatePlanCommand command) {
        if (command.price() == null || command.price().signum() <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        Plan plan = new Plan(
            PlanId.generate(),
            command.name(),
            command.description(),
            Money.of(command.price(), command.currency()),
            command.durationDays(),
            command.featured(),
            command.status(),
            command.termsAndConditions(),
            command.cancellationPolicy(),
            command.courseIds().stream().map(PlanCourse::of).toList(),
            command.paymentMethods()
        );
        planRepository.save(plan, clock.now());
        log.info("Plan created; planId={}, status={}, adminId={}", plan.getId(), plan.getStatus(),
            command.actingAdminId());
        return PlanDetails.of(plan, PlanCourseResolver.resolveNames(List.of(plan), courseCatalogPort));
    }
}
