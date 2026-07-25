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

    User save(User user);

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(Email email);

    void deleteById(UserId id);

    boolean existsByEmail(Email email);
}
