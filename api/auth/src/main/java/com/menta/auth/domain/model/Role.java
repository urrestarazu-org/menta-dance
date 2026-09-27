package com.menta.auth.domain.model;

/**
 * User roles in the system.
 * Uses English enum values following code conventions.
 */
public enum Role {
    ADMIN,
    INSTRUCTOR,
    STUDENT,
    /** #45, US-PHYSICAL-008: front desk. May record MANUAL physical check-ins. */
    RECEPTIONIST
}
