package com.menta.physical.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Value object identifying a {@link PhysicalDevice} (#44, US-PHYSICAL-007). */
public final class DeviceId {

    private final UUID value;

    private DeviceId(UUID value) {
        this.value = value;
    }

    public static DeviceId of(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("DeviceId cannot be null");
        }
        return new DeviceId(value);
    }

    public static DeviceId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DeviceId cannot be null or empty");
        }
        try {
            return new DeviceId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid DeviceId format: " + value, e);
        }
    }

    public static DeviceId generate() {
        return new DeviceId(UUID.randomUUID());
    }

    public UUID getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DeviceId deviceId = (DeviceId) o;
        return Objects.equals(value, deviceId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
