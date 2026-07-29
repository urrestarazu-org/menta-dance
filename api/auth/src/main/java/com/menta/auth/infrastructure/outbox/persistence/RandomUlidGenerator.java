package com.menta.auth.infrastructure.outbox.persistence;

import java.security.SecureRandom;
import java.time.Instant;

/**
 * Default UlidGenerator that emits Crockford-base32 ULIDs (26 chars).
 *
 * 80 bits encode ms since UNIX epoch; 48 bits are random — collision odds
 * over the lifetime of the application are astronomically low.
 *
 * Pure dependency-free: no Ulid library required for PR2. PR3 can swap to
 * com.github.f4b6a3:ulid-creator if needed; the interface boundary keeps
 * the swap a one-line bean change.
 */
public class RandomUlidGenerator implements UlidGenerator {

    private static final int ULID_LENGTH = 26;
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String next() {
        long timestamp = Instant.now().toEpochMilli();
        byte[] randomness = new byte[10];
        RANDOM.nextBytes(randomness);
        char[] out = new char[ULID_LENGTH];

        // 10 chars from 48-bit timestamp.
        for (int i = 9; i >= 0; i--) {
            out[i] = ALPHABET[(int) (timestamp & 0x1F)];
            timestamp >>>= 5;
        }
        // 16 chars from 80-bit randomness (10 bytes → 16 base32 chars).
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
            // Carry overflow into the high half.
            for (int i = 24; i >= 10; i--) {
                out[i] = ALPHABET[(int) (hi & 0x1F)];
                hi >>>= 5;
            }
        }
        return new String(out);
    }
}
