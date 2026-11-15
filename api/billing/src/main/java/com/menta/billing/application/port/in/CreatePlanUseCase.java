package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.CreatePlanCommand;
import com.menta.billing.application.dto.PlanDetailResult;

/** Application-layer entry point for an admin creating a billing plan (#762). */
public interface CreatePlanUseCase {

    /**
     * Creates a plan with a server-generated id and returns it with the same shape as the public
     * plan detail.
     *
     * @throws IllegalArgumentException if the price is not positive, the currency is not an
     *     ISO-4217 code, the duration is not positive, there is no payment method, or a course id
     *     is blank; nothing is persisted in that case.
     */
    PlanDetailResult create(CreatePlanCommand command);
}
