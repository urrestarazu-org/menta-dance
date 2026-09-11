package com.menta.billing.application.port.in;

import com.menta.billing.application.dto.SubscriptionHistoryEntry;
import java.util.List;
import java.util.UUID;

/** Application-layer entry point for a user's full subscription history (US-BILLING-004). */
public interface GetSubscriptionHistoryUseCase {

    /** Newest created first. Empty list, never an exception, when the user has no rows. */
    List<SubscriptionHistoryEntry> history(UUID userId);
}
