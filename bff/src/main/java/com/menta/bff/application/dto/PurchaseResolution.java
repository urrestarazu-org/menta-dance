package com.menta.bff.application.dto;

/** How the administrator closes a purchase stranded in {@code EXCEPTION}: the money went back, or it was made up. */
public enum PurchaseResolution {
    /** The money was returned outside the platform. */
    REFUNDED,
    /** The student was compensated in another way. */
    COMPENSATED
}
