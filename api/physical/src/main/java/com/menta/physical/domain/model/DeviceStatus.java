package com.menta.physical.domain.model;

/** Lifecycle state of a {@link PhysicalDevice} (#44, US-PHYSICAL-007). {@code REVOKED} is absorbing (D5). */
public enum DeviceStatus {
    ACTIVE,
    REVOKED
}
