package com.menta.billing.application.usecase;

import com.menta.billing.application.dto.OwnPaymentEntry;
import com.menta.billing.application.port.in.ListOwnPaymentsUseCase;
import com.menta.billing.application.port.out.PaymentRepository;
import com.menta.billing.domain.exception.InvalidPaymentsLimitException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Default {@link ListOwnPaymentsUseCase} (#767): checks the limit and delegates to one bounded
 * repository read, which already returns the projection and the order.
 *
 * <p>No {@code Transactional*} decorator, matching {@link GetSubscriptionHistoryUseCaseImpl}: a
 * single read-only query, covered by the adapter's own {@code readOnly} transaction.</p>
 */
@RequiredArgsConstructor
public class ListOwnPaymentsUseCaseImpl implements ListOwnPaymentsUseCase {

    private final PaymentRepository paymentRepository;

    @Override
    public List<OwnPaymentEntry> list(UUID actingUserId, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidPaymentsLimitException(limit, MAX_LIMIT);
        }
        return paymentRepository.findOwnNewestFirst(actingUserId, limit);
    }
}
