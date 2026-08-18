package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.PlanCourseJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanCourseJpaRepository extends JpaRepository<PlanCourseJpaEntity, Long> {

    List<PlanCourseJpaEntity> findByPlanId(UUID planId);

    List<PlanCourseJpaEntity> findByPlanIdIn(List<UUID> planIds);
}
