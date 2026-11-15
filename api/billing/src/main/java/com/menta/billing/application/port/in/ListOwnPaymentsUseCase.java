package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.OwnPaymentEntry;
import java.util.List;
import java.util.UUID;

/** The authenticated student's own payments, newest first (#767). */
public interface ListOwnPaymentsUseCase {

    /** Number of payments returned when the caller does not choose one. */
    int DEFAULT_LIMIT = 20;

    /** Largest number of payments a caller may ask for. */
    int MAX_LIMIT = 100;

    /**
     * Returns at most {@code limit} payments of the user, newest created first and, for the same
     * instant, by id descending. An empty list when the user has none. A {@code limit} outside
     * {@code [1, MAX_LIMIT]} fails with {@code InvalidPaymentsLimitException}.
     */
    List<OwnPaymentEntry> list(UUID actingUserId, int limit);
}
