package com.menta.auth.application.port.out;

import java.util.UUID;

/** Delivers the durable password-reset notification identified by its token ID. */
public interface PasswordResetNotificationPort {

    /**
     * Sends the reset email and clears its encrypted envelope only after the
     * SMTP server accepts the message.
     *
     * @param passwordResetTokenId durable reset-token identifier
     */
    void sendPasswordResetEmail(UUID passwordResetTokenId);
}
