package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ClassPurchaseType;
import com.menta.bff.application.dto.ClassQuote;
import com.menta.bff.application.port.out.PhysicalPurchaseApiClient;
import java.time.ZoneId;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link QuoteClassUseCase}.
 *
 * <p>The idempotency key is minted here, once per rendered confirmation page,
 * rather than at payment time: a resubmitted payment form then carries the
 * same key and billing replays the first checkout instead of creating a
 * second payment.
 * </p>
 */
@RequiredArgsConstructor
public class QuoteClassUseCaseImpl implements QuoteClassUseCase {

    private final PhysicalPurchaseApiClient physicalPurchaseApiClient;
    private final ZoneId displayZone;
    private final Supplier<String> idempotencyKeys;

    @Override
    public ClassQuoteView execute(
            String courseId, ClassPurchaseType purchaseType, String sessionId, String accessToken) {
        // Billing rejects a session id on a monthly quote, so a stray form field never reaches it.
        String selectedSessionId = purchaseType == ClassPurchaseType.INDIVIDUAL ? sessionId : null;

        ClassQuote quote;
        try {
            quote = physicalPurchaseApiClient.createQuote(courseId, purchaseType, selectedSessionId, accessToken);
        } catch (PhysicalPurchaseApiClient.QuoteRejectedException e) {
            return new ClassQuoteView.Rejected(courseId);
        }

        if (!quote.available()) {
            return new ClassQuoteView.SoldOut(courseId);
        }
        return new ClassQuoteView.Payable(
                courseId, quote, ClassLabels.expiry(quote.expiresAt(), displayZone), idempotencyKeys.get());
    }
}
