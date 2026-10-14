package com.menta.bff.application.dto;

/**
 * Where a purchase stands once its payment is complete. Only the Billing API's
 * status name is read; internal detail (reasons, resolution notes) never
 * reaches this BFF.
 */
public enum PurchaseFulfillment {
    PENDING_FULFILLMENT,
    ASSIGNED,
    EXCEPTION,
    RESOLVED;

    /**
     * Maps an upstream fulfillment name.
     *
     * @param upstream value from the Billing API, may be {@code null}
     * @return the matching constant, or {@code null} when absent or unknown so
     *         the caller falls back to its default rendering
     */
    public static PurchaseFulfillment fromUpstream(String upstream) {
        if (upstream == null) {
            return null;
        }
        return switch (upstream) {
            case "PENDING_FULFILLMENT" -> PENDING_FULFILLMENT;
            case "ASSIGNED" -> ASSIGNED;
            case "EXCEPTION" -> EXCEPTION;
            case "RESOLVED" -> RESOLVED;
            default -> null;
        };
    }
}
