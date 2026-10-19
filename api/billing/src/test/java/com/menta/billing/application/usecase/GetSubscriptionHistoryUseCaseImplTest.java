package com.menta.billing.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.menta.billing.application.dto.SubscriptionHistoryEntry;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.model.SubscriptionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GetSubscriptionHistoryUseCaseImplTest {

    private static final Instant NOW = Instant.parse("2026-09-01T12:00:00Z");

    private SubscriptionRepository subscriptionRepository;
    private GetSubscriptionHistoryUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        useCase = new GetSubscriptionHistoryUseCaseImpl(subscriptionRepository);
    }

    @Test
    void delegates_to_the_repository_and_preserves_its_order() {
        UUID userId = UUID.randomUUID();
        SubscriptionHistoryEntry newest = new SubscriptionHistoryEntry(
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), SubscriptionStatus.ACTIVE,
            NOW.minusSeconds(86400), NOW.plusSeconds(86400 * 10), NOW
        );
        SubscriptionHistoryEntry oldest = new SubscriptionHistoryEntry(
            UUID.randomUUID().toString(), UUID.randomUUID().toString(), SubscriptionStatus.EXPIRED,
            NOW.minusSeconds(86400 * 60), NOW.minusSeconds(86400 * 30), NOW.minusSeconds(86400 * 60)
        );
        when(subscriptionRepository.findHistoryByUserId(userId)).thenReturn(List.of(newest, oldest));

        List<SubscriptionHistoryEntry> result = useCase.history(userId);

        assertThat(result).containsExactly(newest, oldest);
    }

    @Test
    void an_empty_repository_result_stays_an_empty_list_without_throwing() {
        UUID userId = UUID.randomUUID();
        when(subscriptionRepository.findHistoryByUserId(userId)).thenReturn(List.of());

        List<SubscriptionHistoryEntry> result = useCase.history(userId);

        assertThat(result).isEmpty();
    }
}
