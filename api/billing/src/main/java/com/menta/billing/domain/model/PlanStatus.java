package com.menta.billing.domain.model;

/** Lifecycle state of a subscription {@link Plan}. Only {@code ACTIVE} plans are ever shown publicly. */
public enum PlanStatus {
    ACTIVE,
    INACTIVE
}
