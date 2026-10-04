package com.menta.auth.domain.repository;

import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.shared.domain.vo.Email;
import java.util.Optional;

/**
 * Repository port for User aggregate.
 * Pure interface without framework dependencies (no Spring, no JPA).
 */
public interface UserRepository {

    /**
     * Persists the user, inserting it or updating the existing row with the same id.
     *
     * @return the user as stored
     */
    User save(User user);

    /**
     * Looks a user up by id.
     *
     * @return the user, or empty when none has that id
     */
    Optional<User> findById(UserId id);

    /**
     * Looks a user up by email.
     *
     * @return the user, or empty when none has that email
     */
    Optional<User> findByEmail(Email email);

    /**
     * Removes the user with the given id.
     *
     * <p>An unknown id is not reported as an error by the JPA adapter, which delegates to Spring Data.
     */
    void deleteById(UserId id);

    /**
     * Tells whether an account is already registered with the email.
     *
     * @return true when a user with that email exists, whatever its status
     */
    boolean existsByEmail(Email email);

    /** D8 (US-BILLING-012): backs the cross-module {@code UserExistencePort}. */
    boolean existsById(UserId id);
}
