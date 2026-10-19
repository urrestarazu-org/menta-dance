package com.menta.billing.domain.model;

import java.time.Instant;

/**
 * {@code Payment}'s state, as a sealed hierarchy (ADR-0038): a terminal
 * record exposes no transition method at all, so applying a provider
 * outcome to an already-terminal payment does not compile from any caller
 * that statically knows it holds one. The only runtime guard left is the
 * exhaustive {@code switch} in {@link Payment#applyProviderOutcome}, which
 * treats every terminal branch as a no-op — a late or duplicate webhook is
 * an expected condition (US-BILLING-002: "las transiciones... son
 * monotónicas"), never an error.
 */
public sealed interface PaymentStatus {

    /**
     * Non-terminal statuses. All three resolve the same way against a fresh
     * {@link ProviderOutcome} (docs/06-BILLING-API.md's provider→status
     * table applies uniformly) — including {@link ReconciliationRequired}:
     * a later webhook that now matches exactly is a legitimate automatic
     * recovery, not a correction. {@code
     * POST /billing/admin/payments/{id}/corrections} (US-BILLING-005, out of
     * this issue's scope) exists for cases the system cannot resolve on its
     * own, not to gate every recovery behind a human.
     */
    sealed interface Pending extends PaymentStatus permits AwaitingProvider, AwaitingManualVerification,
        ReconciliationRequired {

        default PaymentStatus resolve(ProviderOutcome outcome, Instant now) {
            return switch (outcome.providerStatus()) {
                case "approved" -> new Completed(now);
                case "rejected" -> new Rejected(now);
                case "cancelled" -> new Cancelled(now);
                case "expired" -> new Expired(now);
                case "pending", "in_process" -> new AwaitingProvider();
                default -> new ReconciliationRequired("unknown provider status: " + outcome.providerStatus());
            };
        }
    }

    record AwaitingProvider() implements Pending {
    }

    record AwaitingManualVerification() implements Pending {
    }

    record ReconciliationRequired(String reason) implements Pending {
    }

    record Completed(Instant confirmedAt) implements PaymentStatus {
    }

    record Rejected(Instant rejectedAt) implements PaymentStatus {
    }

    record Cancelled(Instant cancelledAt) implements PaymentStatus {
    }

    record Expired(Instant expiredAt) implements PaymentStatus {
    }
}
