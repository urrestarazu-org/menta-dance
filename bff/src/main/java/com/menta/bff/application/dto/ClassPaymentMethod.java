package com.menta.bff.application.dto;

/**
 * Payment rail for an in-person class purchase, mirroring billing's {@code
 * PaymentMethod}. A closed enum so an unknown form value fails at binding
 * instead of reaching billing.
 */
public enum ClassPaymentMethod {
    MERCADO_PAGO,
    BANK_TRANSFER
}
