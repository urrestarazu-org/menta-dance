package com.menta.bff.application.dto;

/**
 * Where a payment stands, as far as the web flow cares. The upstream has more
 * pending states (awaiting provider, manual verification, reconciliation); all
 * of them, and any status this BFF does not know, read as {@link #PENDING}, so
 * an unknown value can never be shown as a success.
 */
public enum PaymentProgress {
    PENDING,
    COMPLETED,
    REJECTED,
    CANCELLED,
    EXPIRED;

    /**
     * Maps an upstream payment status name.
     *
     * @param upstreamStatus status name from the Billing API, may be {@code null}
     * @return the matching progress, {@link #PENDING} when unknown
     */
    public static PaymentProgress fromUpstream(String upstreamStatus) {
        if (upstreamStatus == null) {
            return PENDING;
        }
        return switch (upstreamStatus) {
            case "COMPLETED" -> COMPLETED;
            case "REJECTED" -> REJECTED;
            case "CANCELLED" -> CANCELLED;
            case "EXPIRED" -> EXPIRED;
            default -> PENDING;
        };
    }

    /** Whether the payment will not change anymore. */
    public boolean isFinal() {
        return this != PENDING;
    }
}
