package com.menta.billing.application.port.out;

/**
 * Out-port for the blob storage backing a {@link com.menta.billing.domain.model.PaymentProof}
 * (#31, US-BILLING-003, design C5/C11). {@code storageKey} is the opaque string {@link
 * com.menta.billing.domain.model.PaymentProof#create} computes — never a filesystem path. No
 * implementation of this port may let a filesystem path cross back into the domain or application
 * layers.
 */
public interface PaymentProofStoragePort {

    void store(String storageKey, byte[] content);

    /** Silent no-op when {@code storageKey} does not exist — mirrors delete-if-present semantics. */
    void delete(String storageKey);
}
