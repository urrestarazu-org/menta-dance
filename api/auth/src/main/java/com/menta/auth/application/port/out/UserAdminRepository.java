package com.menta.auth.application.port.out;

import com.menta.auth.application.dto.ListUsersQuery;
import com.menta.auth.application.dto.UserPage;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import java.util.Optional;
import java.util.Set;

/**
 * Administrative access to users: the paged listing and the row locks that serialize role changes.
 *
 * <p>The two locking methods MUST be called inside a transaction, because the locks last until it ends.
 */
public interface UserAdminRepository {

    /**
     * Pages through the users matching the query, ordered by email and then id.
     *
     * @return the requested page; a page past the end has no items but still reports the totals
     */
    UserPage search(ListUsersQuery query);

    /**
     * Reads a user taking a write lock on its row, so that concurrent role changes of the same user queue up.
     *
     * @return the user as currently stored, or empty when none has that id
     */
    Optional<User> findByIdForUpdate(UserId id);

    /**
     * Locks every active administrator and returns their ids.
     *
     * <p>Role changes call this BEFORE reading their target so that two of them can never both decide that
     * another active administrator remains and then remove each other. The rows are locked in a fixed order
     * (by id) so concurrent callers queue up instead of deadlocking.
     *
     * @return the ids of the users that are {@code ADMIN} and {@code ACTIVE} at the moment the locks are granted
     */
    Set<UserId> lockActiveAdminIds();
}
