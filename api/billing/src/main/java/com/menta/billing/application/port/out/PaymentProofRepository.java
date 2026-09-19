package com.menta.billing.application.port.out;

import com.menta.billing.domain.model.PaymentId;
import com.menta.billing.domain.model.PaymentProof;
import java.util.Optional;

/**
 * Out-port for {@link PaymentProof} persistence (#31, US-BILLING-003, design C5).
 *
 * <p>{@code billing_payment_proofs} carries a unique key on {@code payment_id}, so {@link #save}
 * for a payment that already has a proof overwrites the existing row — the prior proof is no
 * longer retrievable, a structural property of the schema rather than a cleanup step a caller
 * must remember.</p>
 */
public interface PaymentProofRepository {

    PaymentProof save(PaymentProof proof);

    Optional<PaymentProof> findByPaymentId(PaymentId paymentId);
}
