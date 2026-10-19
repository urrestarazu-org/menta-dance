package com.menta.billing.infrastructure.outbox;

import java.security.SecureRandom;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Production ULID generator for billing's outbox rows. Mirrors
 * {@code com.menta.auth.infrastructure.outbox.persistence.RandomUlidGenerator}:
 * 26-char Crockford-base32 ULID, 80-bit timestamp + 48-bit randomness.
 *
 * <p>The interface lives inside the billing module so each module owns
 * its own outbox boundary despite both writing to the same physical
 * table (design §1 cross-module JPA strategy).</p>
 */
@Component
public class BillingRandomUlidGenerator implements BillingUlidGenerator {

    private static final int ULID_LENGTH = 26;
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String next() {
        long timestamp = Instant.now().toEpochMilli();
        byte[] randomness = new byte[10];
        RANDOM.nextBytes(randomness);
        char[] out = new char[ULID_LENGTH];

        for (int i = 9; i >= 0; i--) {
            out[i] = ALPHABET[(int) (timestamp & 0x1F)];
            timestamp >>>= 5;
        }
        long hi = ((long) randomness[0] << 32)
            | ((long) (randomness[1] & 0xFF) << 24)
            | ((long) (randomness[2] & 0xFF) << 16)
            | ((long) (randomness[3] & 0xFF) << 8)
            | (randomness[4] & 0xFF);
        long lo = ((long) randomness[5] << 32)
            | ((long) (randomness[6] & 0xFF) << 24)
            | ((long) (randomness[7] & 0xFF) << 16)
            | ((long) (randomness[8] & 0xFF) << 8)
            | (randomness[9] & 0xFF);
        for (int i = 25; i >= 10; i--) {
            out[i] = ALPHABET[(int) (lo & 0x1F)];
            lo >>>= 5;
        }
        if (lo != 0) {
            for (int i = 24; i >= 10; i--) {
                out[i] = ALPHABET[(int) (hi & 0x1F)];
                hi >>>= 5;
            }
        }
        return new String(out);
    }
}
