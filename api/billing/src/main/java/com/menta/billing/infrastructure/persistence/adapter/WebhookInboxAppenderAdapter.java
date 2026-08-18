package com.menta.billing.infrastructure.persistence.adapter;

import com.menta.billing.application.port.out.WebhookInboxAppender;
import com.menta.billing.infrastructure.persistence.entity.WebhookInboxJpaEntity;
import com.menta.billing.infrastructure.persistence.repository.WebhookInboxJpaRepository;
import com.menta.billing.infrastructure.webhook.WebhookInboxStatus;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The unique constraint on {@code dedupe_key} is the actual dedup
 * mechanism — catching the resulting {@link DataIntegrityViolationException}
 * on a concurrent duplicate insert is race-free, unlike an
 * exists-then-insert check.
 *
 * <p>Uses a programmatic {@link TransactionTemplate} with {@code
 * PROPAGATION_REQUIRES_NEW} rather than a {@code @Transactional} method,
 * deliberately: once {@link jakarta.persistence.EntityManager#flush()}
 * throws, JPA requires that persistence context's transaction to roll
 * back — a {@code @Transactional} method that merely catches the exception
 * still gets marked rollback-only by Spring's own AOP advice around {@code
 * JpaRepository.flush()}, so the surrounding method's own transactional
 * proxy fails at commit with {@code UnexpectedRollbackException} even
 * though the exception was "caught". {@code TransactionTemplate.execute}
 * performs the rollback of ONLY this nested transaction synchronously
 * inside {@code execute(...)} and re-throws afterward — by the time this
 * class's catch block runs, that transaction is already fully closed, so
 * nothing here can poison the caller's (from {@code
 * TransactionalReceiveWebhookUseCase}) transaction.</p>
 */
@Component
public class WebhookInboxAppenderAdapter implements WebhookInboxAppender {

    private final WebhookInboxJpaRepository jpaRepository;
    private final TransactionTemplate requiresNewTransaction;

    public WebhookInboxAppenderAdapter(
        WebhookInboxJpaRepository jpaRepository, PlatformTransactionManager transactionManager
    ) {
        this.jpaRepository = jpaRepository;
        this.requiresNewTransaction = new TransactionTemplate(transactionManager);
        this.requiresNewTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public boolean appendIfNew(String dedupeKey, String providerPaymentId, String requestId, Instant receivedAt) {
        try {
            requiresNewTransaction.executeWithoutResult(status -> {
                jpaRepository.save(new WebhookInboxJpaEntity(
                    dedupeKey, providerPaymentId, requestId, WebhookInboxStatus.RECEIVED, 0, null, null,
                    receivedAt, null
                ));
                jpaRepository.flush();
            });
            return true;
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
    }
}
