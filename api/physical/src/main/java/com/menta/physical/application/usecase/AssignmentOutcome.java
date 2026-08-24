package com.menta.physical.application.usecase;

/**
 * Outcome of a capacity assignment attempt (design §4.3):
 *
 * <ul>
 *   <li>{@link #ASSIGNED} — the row was successfully inserted.</li>
 *   <li>{@link #RACE_LOST} — capacity was still open at read time but another
 *       handler won the V7 UNIQUE INSERT race; lost to {@code EXCEPTION}
 *       routing by the api:app handler (no second row recorded).</li>
 * </ul>
 *
 * <p>Sealed so the producer cannot accidentally widen the result set
 * (e.g. an unwary future change adding {@code UNKNOWN} or {@code
 * INCONCLUSIVE}). Adds a third variant requires an explicit, intentional
 * breaking change and a coverage review.</p>
 */
public sealed interface AssignmentOutcome permits AssignmentOutcome.ASSIGNED,
    AssignmentOutcome.RACE_LOST {

    final class ASSIGNED implements AssignmentOutcome {
        public static final ASSIGNED INSTANCE = new ASSIGNED();
        private ASSIGNED() {
        }
    }

    final class RACE_LOST implements AssignmentOutcome {
        public static final RACE_LOST INSTANCE = new RACE_LOST();
        private RACE_LOST() {
        }
    }
}
