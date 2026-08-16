package com.menta.auth.infrastructure.security;

import com.menta.auth.application.port.out.LoginAttemptAuditPort;
import com.menta.auth.application.port.out.LoginAttemptOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes the login audit trail to the structured application log.
 *
 * <p>The log is deliberately the sink rather than a database table: this must
 * survive the rollback of a failed login, and it must not add a write to the
 * hot path of every authentication attempt. Log shipping already carries these
 * records to the same place operators investigate incidents from.</p>
 *
 * <p>Only fingerprints and an outcome enum reach the log — never an email, IP,
 * password, or token. An audit trail that leaks the secrets it exists to
 * protect is worse than none, because it spreads them to everyone with log
 * access.</p>
 */
public final class LoggingLoginAttemptAuditPort implements LoginAttemptAuditPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingLoginAttemptAuditPort.class);

    @Override
    public void record(
        LoginAttemptOutcome outcome,
        String emailFingerprint,
        String clientFingerprint
    ) {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome cannot be null");
        }
        // Successful logins are routine; failures and throttling are what an
        // operator scans for, so they get a level that survives production
        // log filtering.
        if (outcome == LoginAttemptOutcome.SUCCESS) {
            log.info(
                "login attempt outcome={} emailFingerprint={} clientFingerprint={}",
                outcome, emailFingerprint, clientFingerprint
            );
        } else {
            log.warn(
                "login attempt outcome={} emailFingerprint={} clientFingerprint={}",
                outcome, emailFingerprint, clientFingerprint
            );
        }
    }
}
