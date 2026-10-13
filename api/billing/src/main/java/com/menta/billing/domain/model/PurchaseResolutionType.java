package com.menta.billing.domain.model;

/**
 * How an admin closed a purchase stuck in {@link FulfillmentStatus#EXCEPTION}. The product never
 * moves money for this: both values only record what happened outside it.
 */
public enum PurchaseResolutionType {

    /** The money was returned to the student outside the product. */
    REFUNDED,

    /** The student was compensated in some other way. */
    COMPENSATED
}
