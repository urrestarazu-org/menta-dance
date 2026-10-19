package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.SubscriptionHistoryEntry;
import com.menta.billing.application.port.in.GetSubscriptionHistoryUseCase;
import com.menta.billing.application.port.out.SubscriptionRepository;
import java.util.List;
import java.util.UUID;

/**
 * Pass-through to {@link SubscriptionRepository#findHistoryByUserId(UUID)} (US-BILLING-004,
 * design.md A3) — the repository already returns the correct projection and order, so no
 * transformation belongs here.
 *
 * <p>No {@code Transactional*} decorator, matching {@link GetCurrentSubscriptionUseCaseImpl}.</p>
 */
public class GetSubscriptionHistoryUseCaseImpl implements GetSubscriptionHistoryUseCase {

    private final SubscriptionRepository subscriptionRepository;

    public GetSubscriptionHistoryUseCaseImpl(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    public List<SubscriptionHistoryEntry> history(UUID userId) {
        return subscriptionRepository.findHistoryByUserId(userId);
    }
}
