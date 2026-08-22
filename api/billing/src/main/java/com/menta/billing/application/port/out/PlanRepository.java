package com.menta.billing.application.port.out;

import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanId;
import java.util.List;
import java.util.Optional;

/** Persistence port for {@link Plan}. */
public interface PlanRepository {

    /** Only {@code ACTIVE} plans, ordered by price ascending. */
    List<Plan> findAllActiveOrderByPriceAsc();

    /**
     * @return the plan only if it exists AND is {@code ACTIVE}. An inactive
     *     plan reads as absent — the caller cannot distinguish "never
     *     existed" from "exists but inactive" from this return value alone.
     */
    Optional<Plan> findActiveById(PlanId id);

    /**
     * The plan whatever its status. Used only when activating a subscription
     * whose payment already settled (US-BILLING-010 escenario 2b): the buyer
     * paid while the plan was {@code ACTIVE}, so an admin deactivating it in
     * the meantime must not silently strip the snapshot of its courses.
     */
    Optional<Plan> findById(PlanId id);
}
