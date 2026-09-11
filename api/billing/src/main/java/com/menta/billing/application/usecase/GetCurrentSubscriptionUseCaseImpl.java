package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.CurrentSubscriptionResult;
import com.menta.billing.application.port.in.GetCurrentSubscriptionUseCase;
import com.menta.billing.application.port.out.Clock;
import com.menta.billing.application.port.out.SubscriptionRepository;
import com.menta.billing.domain.exception.NoSubscriptionException;
import com.menta.billing.domain.model.Subscription;
import com.menta.billing.domain.model.SubscriptionStatus;
import java.util.UUID;

/**
 * Resolves a user's current subscription (US-BILLING-004, design.md A1): a live PENDING/ACTIVE
 * slot always wins over history, and only when the slot is empty does the latest EXPIRED row
 * count. No other state resolves here — a CANCELLED-with-remaining-access row (D1) is
 * deliberately never consulted.
 *
 * <p>No {@code Transactional*} decorator (matches {@link ListPlansUseCaseImpl} / {@link
 * GetPlanUseCaseImpl}): every {@code SubscriptionRepository} query method the adapter implements
 * already declares its own {@code readOnly = true} transaction.</p>
 */
public class GetCurrentSubscriptionUseCaseImpl implements GetCurrentSubscriptionUseCase {

    private final SubscriptionRepository subscriptionRepository;
    private final Clock clock;

    public GetCurrentSubscriptionUseCaseImpl(SubscriptionRepository subscriptionRepository, Clock clock) {
        this.subscriptionRepository = subscriptionRepository;
        this.clock = clock;
    }

    @Override
    public CurrentSubscriptionResult current(UUID userId) {
        return subscriptionRepository.findCurrentByUserId(userId)
            .map(this::fromCurrentSlot)
            .or(() -> subscriptionRepository.findLatestExpiredByUserId(userId).map(this::fromExpired))
            .orElseThrow(NoSubscriptionException::new);
    }

    private CurrentSubscriptionResult fromCurrentSlot(Subscription subscription) {
        if (subscription.getStatus() == SubscriptionStatus.PENDING) {
            return new CurrentSubscriptionResult.PendingPayment(
                subscription.getId().toString(), subscription.getPlanId().toString(),
                subscription.getCheckoutUrl().orElse(null)
            );
        }
        var now = clock.now();
        return new CurrentSubscriptionResult.Active(
            subscription.getId().toString(), subscription.getPlanId().toString(),
            subscription.getStartDate().orElse(null), subscription.getEndDate().orElse(null),
            subscription.daysRemaining(now).orElse(0L), subscription.isExpiringSoon(now)
        );
    }

    private CurrentSubscriptionResult fromExpired(Subscription subscription) {
        return new CurrentSubscriptionResult.Expired(
            subscription.getId().toString(), subscription.getPlanId().toString(),
            subscription.getStartDate().orElse(null), subscription.getEndDate().orElse(null)
        );
    }
}
