package com.menta.billing.infrastructure.persistence.repository;

import com.menta.billing.infrastructure.persistence.entity.PlanCourseJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository over {@code billing_plan_courses}, the courses included in each plan.
 *
 * <p>Used by {@code PlanRepositoryAdapter}, which reads them per plan or, for a list, in one batch
 * by plan ids, and by the E2E billing fixture.
 */
public interface PlanCourseJpaRepository extends JpaRepository<PlanCourseJpaEntity, Long> {

    /** Lists the course rows of one plan. */
    List<PlanCourseJpaEntity> findByPlanId(UUID planId);

    /** Lists the course rows of several plans in one query, which avoids one lookup per plan when listing plans. */
    List<PlanCourseJpaEntity> findByPlanIdIn(List<UUID> planIds);
}
