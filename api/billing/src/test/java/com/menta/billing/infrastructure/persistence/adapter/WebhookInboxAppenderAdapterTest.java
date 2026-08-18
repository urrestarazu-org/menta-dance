package com.menta.billing.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.billing.infrastructure.persistence.repository.WebhookInboxJpaRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class WebhookInboxAppenderAdapterTest {

    private static PlatformTransactionManager transactionManager() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return manager;
    }

    @Test
    void appendIfNew_returns_true_when_the_row_is_persisted() {
        WebhookInboxJpaRepository jpaRepository = mock(WebhookInboxJpaRepository.class);
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        WebhookInboxAppenderAdapter adapter = new WebhookInboxAppenderAdapter(jpaRepository, transactionManager());

        assertThat(adapter.appendIfNew("k", "mp-1", "req-1", Instant.now())).isTrue();
    }

    @Test
    void appendIfNew_returns_false_on_a_duplicate_dedupe_key() {
        WebhookInboxJpaRepository jpaRepository = mock(WebhookInboxJpaRepository.class);
        when(jpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new DataIntegrityViolationException("duplicate")).when(jpaRepository).flush();
        WebhookInboxAppenderAdapter adapter = new WebhookInboxAppenderAdapter(jpaRepository, transactionManager());

        assertThat(adapter.appendIfNew("k", "mp-1", "req-1", Instant.now())).isFalse();
    }
}
