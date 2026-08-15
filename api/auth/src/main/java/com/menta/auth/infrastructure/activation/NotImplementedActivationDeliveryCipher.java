package com.menta.auth.infrastructure.activation;

import com.menta.auth.application.port.out.ActivationDeliveryCipher;
import com.menta.auth.application.port.out.DeliveryEnvelope;

/**
 * Compile-boundary placeholder for {@link ActivationDeliveryCipher}.
 *
 * // TODO(PR2 task 2.3): replace with real AES-GCM adapter.
 */
public class NotImplementedActivationDeliveryCipher implements ActivationDeliveryCipher {

    private static final String MESSAGE =
        "ActivationDeliveryCipher AES-GCM adapter not implemented yet — see task 2.3";

    @Override
    public DeliveryEnvelope encrypt(String plaintext) {
        throw new UnsupportedOperationException(MESSAGE);
    }

    @Override
    public String decrypt(DeliveryEnvelope envelope) {
        throw new UnsupportedOperationException(MESSAGE);
    }
}
