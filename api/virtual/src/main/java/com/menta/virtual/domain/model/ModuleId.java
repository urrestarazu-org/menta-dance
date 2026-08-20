package com.menta.virtual.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Value object identifying a {@link VirtualModule}. */
public final class ModuleId {

    private final UUID value;

    private ModuleId(UUID value) {
        this.value = value;
    }

    public static ModuleId of(UUID value) {
        if (value == null) {
            throw new IllegalArgumentException("ModuleId cannot be null");
        }
        return new ModuleId(value);
    }

    public static ModuleId of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ModuleId cannot be null or empty");
        }
        try {
            return new ModuleId(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid ModuleId format: " + value, e);
        }
    }

    public static ModuleId generate() {
        return new ModuleId(UUID.randomUUID());
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
        ModuleId moduleId = (ModuleId) o;
        return Objects.equals(value, moduleId.value);
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
