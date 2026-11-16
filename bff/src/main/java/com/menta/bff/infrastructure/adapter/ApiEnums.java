package com.menta.bff.infrastructure.adapter;

/** Reads the enum-like strings of the Billing API without breaking on a value added later. */
final class ApiEnums {

    private static final String UNKNOWN = "UNKNOWN";

    private ApiEnums() {
    }

    /**
     * Maps an API string to its constant. A value this BFF does not know maps to the {@code UNKNOWN}
     * constant of the enum, so a new upstream value degrades one label instead of the whole list.
     *
     * @param type  enum with an {@code UNKNOWN} constant
     * @param value non-null API string
     * @param <E>   the enum
     * @return the matching constant, or {@code UNKNOWN}
     */
    static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            return Enum.valueOf(type, UNKNOWN);
        }
    }
}
