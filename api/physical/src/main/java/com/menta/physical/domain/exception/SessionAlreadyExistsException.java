package com.menta.physical.domain.exception;

import com.menta.shared.domain.exceptions.BusinessException;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Thrown when a course already has a session at an instant a creation would use (#743). A
 * {@code CANCELLED} session counts as existing, so the instant stays taken.
 *
 * <p>Carries every conflicting instant, sorted ascending and in UTC, so the caller can show the
 * dates that were rejected. The message is fixed and carries no request data.
 */
public class SessionAlreadyExistsException extends BusinessException {

    private static final String ERROR_CODE = "SESSION_ALREADY_EXISTS";

    private final List<Instant> conflictingInstants;

    public SessionAlreadyExistsException(Collection<Instant> conflictingInstants) {
        super(ERROR_CODE, "The course already has a session at one of the requested instants");
        this.conflictingInstants = conflictingInstants.stream().sorted().toList();
    }

    /** The instants that already have a session, sorted ascending. */
    public List<Instant> getConflictingInstants() {
        return conflictingInstants;
    }
}
