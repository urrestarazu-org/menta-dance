package com.menta.auth.infrastructure.persistence.repository;

import com.menta.auth.infrastructure.persistence.entity.UserAuditJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Spring Data JPA repository for the user audit trail. */
@Repository
public interface UserAuditJpaRepository extends JpaRepository<UserAuditJpaEntity, UUID> {

    /** Returns the audit entries of a user, oldest first. */
    List<UserAuditJpaEntity> findByUserIdOrderByCreatedAtAsc(UUID userId);
}
