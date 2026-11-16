package com.menta.bff.application.dto;

/**
 * The subscription card of a student's dashboard (#363), ready to render.
 *
 * <p>Sealed so the use case must build every state. Each state names its {@link Kind}, which is
 * what the template switches on: {@link None} (never subscribed), {@link Active}, {@link Pending},
 * {@link Expired}, {@link Cancelled} (cancelled but still with access), or {@link Unavailable} when
 * the state could not be read, so the card shows a calm notice and the rest of the dashboard still
 * renders. Dates are already formatted as {@code dd/MM/yyyy} in the academy's display zone.
 * </p>
 */
public sealed interface DashboardView {

    /** The state the card renders. */
    Kind kind();

    /** Discriminator of the card states, for the template. */
    enum Kind {
        NONE,
        ACTIVE,
        PENDING,
        EXPIRED,
        CANCELLED,
        UNAVAILABLE
    }

    /** The student has no subscription at all. */
    record None() implements DashboardView {
        @Override
        public Kind kind() {
            return Kind.NONE;
        }
    }

    /**
     * A subscription in force.
     *
     * @param planName       plan name, or the generic label when it could not be resolved
     * @param endDateLabel   end date as {@code dd/MM/yyyy}
     * @param remainingLabel days left as a sentence, or {@code null} when the API did not send them
     * @param expiringSoon   whether the API flags the subscription as about to expire
     */
    record Active(String planName, String endDateLabel, String remainingLabel, boolean expiringSoon)
            implements DashboardView {
        @Override
        public Kind kind() {
            return Kind.ACTIVE;
        }
    }

    /**
     * A subscription waiting for its payment.
     *
     * @param planName plan name, or the generic label when it could not be resolved
     */
    record Pending(String planName) implements DashboardView {
        @Override
        public Kind kind() {
            return Kind.PENDING;
        }
    }

    /**
     * A subscription that already ended.
     *
     * @param planName     plan name, or the generic label when it could not be resolved
     * @param endDateLabel end date as {@code dd/MM/yyyy}
     */
    record Expired(String planName, String endDateLabel) implements DashboardView {
        @Override
        public Kind kind() {
            return Kind.EXPIRED;
        }
    }

    /**
     * A subscription the student cancelled that still grants access until the end of the paid period.
     * The Billing API never reports it as the current one, so it comes from the history.
     *
     * @param planName       plan name, or the generic label when it could not be resolved
     * @param accessEndsLabel date the access lasts until as {@code dd/MM/yyyy}
     */
    record Cancelled(String planName, String accessEndsLabel) implements DashboardView {
        @Override
        public Kind kind() {
            return Kind.CANCELLED;
        }
    }

    /** The subscription state could not be read right now. */
    record Unavailable() implements DashboardView {
        @Override
        public Kind kind() {
            return Kind.UNAVAILABLE;
        }
    }
}
