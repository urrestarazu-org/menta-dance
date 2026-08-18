package com.menta.billing.application.port.out;

import com.menta.billing.domain.model.Payment;
import com.menta.billing.domain.model.PaymentId;
import java.util.Optional;

public interface PaymentRepository {

    Payment save(Payment payment);

    Optional<Payment> findById(PaymentId id);

    Optional<Payment> findByProviderPaymentId(String providerPaymentId);
}
