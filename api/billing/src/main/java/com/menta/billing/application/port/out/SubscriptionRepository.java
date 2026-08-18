package com.menta.billing.application.port.out;

import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Subscription;
import java.util.Optional;

public interface SubscriptionRepository {

    Subscription save(Subscription subscription);

    Optional<Subscription> findByPaymentId(PaymentId paymentId);
}
