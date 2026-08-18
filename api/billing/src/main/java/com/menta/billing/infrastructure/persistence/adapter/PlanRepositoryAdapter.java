package com.menta.billing.infrastructure.persistence.adapter;

import com.menta.billing.application.port.out.PlanRepository;
import com.menta.billing.domain.model.Plan;
import com.menta.billing.domain.model.PlanId;
import com.menta.billing.domain.model.PlanStatus;
import com.menta.billing.infrastructure.persistence.entity.PlanCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PlanJpaEntity;
import com.menta.billing.infrastructure.persistence.mapper.PlanJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.PlanCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PlanJpaRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for {@link PlanRepository}.
 *
 * <p>Two flat queries (plans, then their courses), joined in memory —
 * deliberately no {@code @OneToMany} JPA relationship: it would tempt lazy
 * loading and N+1 queries for what is, at this scale, one bounded batch
 * fetch per request.</p>
 */
@Component
public class PlanRepositoryAdapter implements PlanRepository {

    private final PlanJpaRepository planJpaRepository;
    private final PlanCourseJpaRepository planCourseJpaRepository;

    public PlanRepositoryAdapter(
        PlanJpaRepository planJpaRepository, PlanCourseJpaRepository planCourseJpaRepository
    ) {
        this.planJpaRepository = planJpaRepository;
        this.planCourseJpaRepository = planCourseJpaRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public List<Plan> findAllActiveOrderByPriceAsc() {
        List<PlanJpaEntity> plans = planJpaRepository.findAllByStatusOrderByPriceAsc(PlanStatus.ACTIVE);
        if (plans.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<PlanCourseJpaEntity>> coursesByPlanId = planCourseJpaRepository
            .findByPlanIdIn(plans.stream().map(PlanJpaEntity::getId).toList())
            .stream()
            .collect(Collectors.groupingBy(PlanCourseJpaEntity::getPlanId));
        return plans.stream()
            .map(plan -> PlanJpaMapper.toDomain(plan, coursesByPlanId.getOrDefault(plan.getId(), List.of())))
            .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<Plan> findActiveById(PlanId id) {
        return planJpaRepository.findByIdAndStatus(id.getValue(), PlanStatus.ACTIVE)
            .map(entity -> PlanJpaMapper.toDomain(
                entity, planCourseJpaRepository.findByPlanId(entity.getId())
            ));
    }
}
