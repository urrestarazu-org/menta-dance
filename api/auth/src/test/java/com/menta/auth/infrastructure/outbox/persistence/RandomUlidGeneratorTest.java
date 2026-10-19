package com.menta.auth.infrastructure.outbox.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RandomUlidGeneratorTest {

    private static final String CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    private final RandomUlidGenerator generator = new RandomUlidGenerator();

    @Test
    void produces_a_26_character_crockford_base32_string() {
        String ulid = generator.next();

        assertThat(ulid).hasSize(26);
        assertThat(ulid.chars().allMatch(c -> CROCKFORD_BASE32.indexOf(c) >= 0)).isTrue();
    }

    @Test
    void consecutive_generations_differ() {
        String first = generator.next();
        String second = generator.next();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void generates_unique_values_across_many_calls() {
        Set<String> generated = new HashSet<>();
        IntStream.range(0, 200).forEach(i -> generated.add(generator.next()));

        assertThat(generated).hasSize(200);
    }
}
