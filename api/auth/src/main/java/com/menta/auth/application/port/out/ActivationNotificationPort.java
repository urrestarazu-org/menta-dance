package com.menta.auth.application.port.out;

import java.util.UUID;

/** Delivers the durable activation notification identified by its token ID. */
public interface ActivationNotificationPort {

    /**
     * Sends the activation email and clears its encrypted envelope only after
     * the SMTP server accepts the message.
     *
     * @param activationTokenId durable activation-token identifier
     */
    void sendActivationEmail(UUID activationTokenId);
}
