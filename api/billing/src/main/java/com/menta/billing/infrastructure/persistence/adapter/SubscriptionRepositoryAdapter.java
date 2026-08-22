package com.menta.billing.infrastructure.persistence.adapter;

import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.exception.SubscriptionAlreadyActiveException;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.infrastructure.persistence.entity.SubscriptionCourseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.SubscriptionJpaEntity;
import com.menta.billing.infrastructure.persistence.mapper.SubscriptionJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionCourseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.SubscriptionJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA adapter for {@link SubscriptionRepository}.
 *
 * <p>The course snapshot is rewritten on every save rather than diffed: it is
 * written exactly once, at activation, and a full replace cannot leave a
 * half-updated snapshot behind.</p>
 */
@Component
public class SubscriptionRepositoryAdapter implements SubscriptionRepository {

    private final SubscriptionJpaRepository jpaRepository;
    private final SubscriptionCourseJpaRepository courseJpaRepository;

    public SubscriptionRepositoryAdapter(
        SubscriptionJpaRepository jpaRepository, SubscriptionCourseJpaRepository courseJpaRepository
    ) {
        this.jpaRepository = jpaRepository;
        this.courseJpaRepository = courseJpaRepository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Subscription save(Subscription subscription) {
        SubscriptionJpaEntity saved = jpaRepository.save(SubscriptionJpaMapper.toEntity(subscription));
        return SubscriptionJpaMapper.toDomain(saved, replaceCourses(subscription));
    }

    /**
     * The insert that claims the user's slot. {@code flush()} forces the
     * unique index to decide <em>here</em> instead of at commit, so the
     * caller sees a domain conflict rather than an opaque commit failure.
     *
     * <p>Not caught-and-continued: the violation must roll the caller's whole
     * transaction back, which is exactly what makes escenario 3's "no crea un
     * Payment ni una Subscription, ni inicia un cobro" hold for the losing
     * side of a race, without any compensating delete.</p>
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Subscription saveNewCheckout(Subscription subscription) {
        try {
            SubscriptionJpaEntity saved = jpaRepository.save(SubscriptionJpaMapper.toEntity(subscription));
            jpaRepository.flush();
            return SubscriptionJpaMapper.toDomain(saved, List.of());
        } catch (DataIntegrityViolationException slotTaken) {
            throw new SubscriptionAlreadyActiveException(null);
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<Subscription> findByPaymentId(PaymentId paymentId) {
        return jpaRepository.findByPaymentId(paymentId.getValue()).map(this::toDomainWithCourses);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<Subscription> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey) {
        return jpaRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey).map(this::toDomainWithCourses);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public Optional<Subscription> findCurrentByUserId(UUID userId) {
        return jpaRepository.findByActiveUserId(userId).map(this::toDomainWithCourses);
    }

    private Subscription toDomainWithCourses(SubscriptionJpaEntity entity) {
        return SubscriptionJpaMapper.toDomain(entity, courseIdsOf(entity.getId()));
    }

    private List<String> courseIdsOf(UUID subscriptionId) {
        return courseJpaRepository.findBySubscriptionId(subscriptionId).stream()
            .map(SubscriptionCourseJpaEntity::getCourseId)
            .toList();
    }

    private List<String> replaceCourses(Subscription subscription) {
        courseJpaRepository.deleteBySubscriptionId(subscription.getId());
        courseJpaRepository.saveAll(subscription.getCourseIds().stream()
            .map(courseId -> new SubscriptionCourseJpaEntity(subscription.getId(), courseId))
            .toList());
        return subscription.getCourseIds();
    }
}
