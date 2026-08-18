package com.menta.billing.application.port.out;

import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.Purchase;
import java.util.Optional;

public interface PurchaseRepository {

    Purchase save(Purchase purchase);

    Optional<Purchase> findByPaymentId(PaymentId paymentId);
}
