package com.menta.app.outbox;

import com.menta.billing.domain.model.PurchaseType;
import com.menta.shared.physical.AssignmentType;

/**
 * Translates Billing's {@link PurchaseType} into Physical's neutral {@link AssignmentType}.
 *
 * <p>Lives in api:app because Physical never imports Billing. The switch is an
 * exhaustive expression with no {@code default}: adding a {@code PurchaseType}
 * constant fails compilation here instead of silently mislabelling assignments.</p>
 */
final class AssignmentTypeMapper {

    private AssignmentTypeMapper() {
    }

    static AssignmentType from(PurchaseType purchaseType) {
        return switch (purchaseType) {
            case MONTHLY -> AssignmentType.MONTHLY;
            case INDIVIDUAL -> AssignmentType.INDIVIDUAL;
        };
    }
}
