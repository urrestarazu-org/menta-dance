package com.menta.bff.application.usecase;

import java.time.Instant;
import java.util.List;

/**
 * Use case that words the instants the API reports as already having a session ({@code conflictingDates}, in
 * UTC) in the academy's zone, so the screens can tell the user which dates collide (#368).
 *
 * <p>Part of Clean Architecture application layer.
 * </p>
 */
public interface DescribeSessionConflictsUseCase {

    /**
     * Words the instants.
     *
     * @param conflictingDates UTC instants that already have a session
     * @return one Spanish label per distinct instant, in chronological order, e.g. {@code "martes 17/11 22:00"}
     */
    List<String> execute(List<Instant> conflictingDates);
}
