package com.menta.billing.domain.exception;

import com.menta.billing.domain.model.PurchaseType;
import com.menta.shared.domain.exceptions.BusinessException;
import java.util.Objects;

/**
 * Thrown when a physical purchase would add no session for the student:
 * every session it would cover is already assigned to them. Charging for
 * nothing is refused outright — no quote is persisted at quote time, and no
 * hold or {@code Payment} is created at checkout.
 *
 * <ul>
 *   <li>{@link PurchaseType#MONTHLY} (#541): every session the monthly would
 *       cover is owned. Partial ownership is not this case: the monthly still
 *       adds at least one session and keeps its full price (no proration —
 *       product decision #541).</li>
 *   <li>{@link PurchaseType#INDIVIDUAL} (#545): the selected session is
 *       already assigned to the student. Confirming it would collide with
 *       {@code uq_physical_assignment_session_student} and leave a charged
 *       purchase in {@code EXCEPTION}.</li>
 * </ul>
 *
 * <p>Both share one stable code; {@link #getPurchaseType()} only lets the
 * web layer word the message for what the student tried to buy.</p>
 */
public class NoNewSessionsToPurchaseException extends BusinessException {

    private static final String ERROR_CODE = "NO_NEW_SESSIONS";

    private final PurchaseType purchaseType;

    public NoNewSessionsToPurchaseException(PurchaseType purchaseType) {
        super(ERROR_CODE, "The student already holds every session this "
            + Objects.requireNonNull(purchaseType, "purchaseType cannot be null") + " purchase would cover");
        this.purchaseType = purchaseType;
    }

    public PurchaseType getPurchaseType() {
        return purchaseType;
    }
}
