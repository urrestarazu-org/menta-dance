package com.menta.billing.infrastructure.persistence.adapter;

import com.menta.billing.application.port.out.PurchaseRepository;
import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import com.menta.billing.infrastructure.persistence.entity.PurchaseJpaEntity;
import com.menta.billing.infrastructure.persistence.entity.PurchaseSessionJpaEntity;
import com.menta.billing.infrastructure.persistence.mapper.PurchaseJpaMapper;
import com.menta.billing.infrastructure.persistence.repository.PurchaseJpaRepository;
import com.menta.billing.infrastructure.persistence.repository.PurchaseSessionJpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * JPA adapter for {@link PurchaseRepository}. Flat queries (purchase, then its ordered sessions),
 * joined in memory — same discipline as {@code PlanRepositoryAdapter}: no {@code @OneToMany}
 * relationship on {@link PurchaseJpaEntity}.
 *
 * <p>{@code physicalSessionIds} is immutable across a {@link Purchase}'s lifecycle — {@code
 * assigned()}/{@code exception()} only ever change {@code status} — so {@link #save(Purchase)}
 * inserts the child rows exactly once, on the first save, and never again on a later
 * status-transition save.</p>
 *
 * <p>{@link #saveIsolated(Purchase)} (#245) uses a programmatic {@link TransactionTemplate}
 * with {@code PROPAGATION_REQUIRES_NEW} rather than a {@code @Transactional} method,
 * deliberately mirroring {@code WebhookInboxAppenderAdapter} (#242): once the ambient
 * transaction's {@code @Transactional(MANDATORY)} {@link #save(Purchase)} throws {@link
 * DataIntegrityViolationException} against {@code uq_billing_purchases_payment_id}, Spring's
 * own AOP advice marks that ambient transaction rollback-only the instant the exception
 * escapes the MANDATORY proxy — regardless of a caller catching it afterward — so it fails
 * at commit with {@code UnexpectedRollbackException}. Running the risky insert in its own
 * {@code REQUIRES_NEW} transaction, with an explicit {@code flush()} to force the constraint
 * violation to surface synchronously, means that sub-transaction rolls back and closes
 * BEFORE the catch block here runs, so nothing can poison the caller's transaction.</p>
 *
 * <p>{@link #findByPaymentIdForUpdate(PaymentId)} is a LOCKING read ({@code FOR UPDATE}, on
 * {@link PurchaseJpaRepository}) for the same reason #216 already forced one onto {@code
 * AssignCapacityUseCase}'s writer: under REPEATABLE READ, a plain consistent read fixes this
 * transaction's MVCC snapshot at the point it runs, so it would silently miss a row {@link
 * #saveIsolated(Purchase)} committed moments earlier on a genuinely different connection.
 * {@link #findByPaymentId(PaymentId)} stays a PLAIN read on purpose: it is the pre-insert
 * existence check {@code CreatePurchaseFromPaymentEventUseCase} runs immediately BEFORE a
 * possible {@link #saveIsolated(Purchase)} for the SAME {@code paymentId} — locking it would
 * self-block that very insert via InnoDB's gap lock on the non-existent row's index range (see
 * {@link PurchaseJpaRepository}'s javadoc; measured as a 50s "Lock wait timeout exceeded" on
 * every happy-path confirmation test before this was split into two methods).</p>
 */
@Component
public class PurchaseRepositoryAdapter implements PurchaseRepository {

    private final PurchaseJpaRepository jpaRepository;
    private final PurchaseSessionJpaRepository sessionJpaRepository;
    private final TransactionTemplate requiresNewTransaction;

    public PurchaseRepositoryAdapter(
        PurchaseJpaRepository jpaRepository, PurchaseSessionJpaRepository sessionJpaRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.jpaRepository = jpaRepository;
        this.sessionJpaRepository = sessionJpaRepository;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Purchase save(Purchase purchase) {
        return insert(purchase);
    }

    @Override
    public Optional<Purchase> saveIsolated(Purchase purchase) {
        try {
            return Optional.of(requiresNewTransaction.execute(status -> {
                Purchase saved = insert(purchase);
                jpaRepository.flush();
                return saved;
            }));
        } catch (DataIntegrityViolationException lostRace) {
            return Optional.empty();
        }
    }

    private Purchase insert(Purchase purchase) {
        PurchaseJpaEntity savedEntity = jpaRepository.save(PurchaseJpaMapper.toEntity(purchase));
        if (sessionJpaRepository.findByPurchaseIdOrderByPositionAsc(purchase.getId()).isEmpty()) {
            sessionJpaRepository.saveAll(PurchaseJpaMapper.toSessionEntities(purchase));
        }
        return PurchaseJpaMapper.toDomain(savedEntity, purchase.getPhysicalSessionIds());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public Optional<Purchase> findByPaymentId(PaymentId paymentId) {
        return jpaRepository.findByPaymentId(paymentId.getValue()).map(this::toDomain);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Purchase> findByPaymentIdForUpdate(PaymentId paymentId) {
        return jpaRepository.findByPaymentIdForUpdate(paymentId.getValue()).map(this::toDomain);
    }

    private Purchase toDomain(PurchaseJpaEntity entity) {
        List<String> physicalSessionIds = sessionJpaRepository
            .findByPurchaseIdOrderByPositionAsc(entity.getId())
            .stream()
            .map(PurchaseSessionJpaEntity::getPhysicalSessionId)
            .toList();
        return PurchaseJpaMapper.toDomain(entity, physicalSessionIds);
    }
}
