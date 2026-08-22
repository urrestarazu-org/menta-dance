package com.menta.billing.domain.model;

/**
 * A payment rail a {@link Plan} may accept (US-BILLING-010, escenario 4b).
 *
 * <p>Kept as a closed enum rather than a free string: the server must be able
 * to reject a method a plan does not accept, and a typo in a client's request
 * has to fail as an unknown value, never as a silently-accepted new rail.</p>
 */
public enum PaymentMethod {

    /** Online checkout through Mercado Pago's hosted flow (Checkout Pro). */
    MERCADO_PAGO,

    /** Manual bank transfer, verified out of band (US-BILLING-003). */
    BANK_TRANSFER
}
