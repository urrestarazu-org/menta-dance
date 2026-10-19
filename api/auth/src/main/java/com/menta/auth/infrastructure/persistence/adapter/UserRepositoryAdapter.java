package com.menta.auth.infrastructure.persistence.adapter;

import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.auth.infrastructure.persistence.entity.UserJpaEntity;
import com.menta.auth.infrastructure.persistence.mapper.UserMapper;
import com.menta.auth.infrastructure.persistence.repository.UserJpaRepository;
import com.menta.shared.domain.vo.Email;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing UserRepository port.
 * Infrastructure layer - bridges domain and Spring Data JPA.
 */
@Component
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;

    public UserRepositoryAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public User save(User user) {
        UserJpaEntity entity = UserMapper.toJpaEntity(user);
        UserJpaEntity saved = jpaRepository.save(entity);
        return UserMapper.toDomain(saved);
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpaRepository.findById(id.getValue())
            .map(UserMapper::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpaRepository.findByEmail(email.getValue())
            .map(UserMapper::toDomain);
    }

    @Override
    public void deleteById(UserId id) {
        jpaRepository.deleteById(id.getValue());
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpaRepository.existsByEmail(email.getValue());
    }

    /**
     * D8 (US-BILLING-012): delegates to {@link org.springframework.data.repository.CrudRepository
     * #existsById}, inherited by {@code UserJpaRepository} — no new JPA method needed.
     */
    @Override
    public boolean existsById(UserId id) {
        return jpaRepository.existsById(id.getValue());
    }
}
