package com.menta.physical.application.usecase;

/**
 * Outcome of a capacity assignment attempt (design §4.3):
 *
 * <ul>
 *   <li>{@link #Assigned} — the row was successfully inserted.</li>
 *   <li>{@link #RaceLost} — capacity was still open at read time but another
 *       handler won the V7 UNIQUE INSERT race; lost to {@code EXCEPTION}
 *       routing by the api:app handler (no second row recorded).</li>
 * </ul>
 *
 * <p>Sealed so the producer cannot accidentally widen the result set
 * (e.g. an unwary future change adding {@code UNKNOWN} or {@code
 * INCONCLUSIVE}). Adds a third variant requires an explicit, intentional
 * breaking change and a coverage review.</p>
 */
public sealed interface AssignmentOutcome permits AssignmentOutcome.Assigned,
    AssignmentOutcome.RaceLost {

    final class Assigned implements AssignmentOutcome {
        public static final Assigned INSTANCE = new Assigned();

        private Assigned() {
        }
    }

    final class RaceLost implements AssignmentOutcome {
        public static final RaceLost INSTANCE = new RaceLost();

        private RaceLost() {
        }
    }
}
