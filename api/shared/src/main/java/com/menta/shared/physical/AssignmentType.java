package com.menta.shared.physical;

/**
 * Neutral classification of how a physical capacity assignment was acquired,
 * shared by {@code api:physical} (persists and exposes it) and {@code api:app}
 * (maps it from billing's purchase type). Lives here so physical never has to
 * depend on billing.
 */
public enum AssignmentType {

    /** The seat comes from a monthly plan purchase. */
    MONTHLY,

    /** The seat comes from a single individual-class purchase. */
    INDIVIDUAL
}
