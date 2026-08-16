package com.menta.auth.infrastructure.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Pins the wire shape of the token payload, because it is what the OpenAPI
 * contract and the Bruno collection both declare.
 *
 * <p>{@code expiresIn} is a {@link Duration} in Java but must not reach clients
 * as an ISO-8601 string: Bruno asserts a number, and Android/BFF read it as
 * seconds. A silent change in Jackson configuration would break every consumer
 * while every existing test still passed, so it gets its own guard.</p>
 */
class TokenResponseSerializationTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .build();

    @Test
    void serializes_the_payload_with_snake_case_wire_names() throws Exception {
        String json = objectMapper.writeValueAsString(
            new TokenResponse("compact.jwt.value", "Bearer", Duration.ofMinutes(15))
        );

        assertThat(json).contains("\"access_token\"", "\"token_type\"", "\"expires_in\"");
        assertThat(json).doesNotContain("accessToken", "tokenType", "expiresIn");
    }

    @Test
    void serializes_expires_in_as_a_number_of_seconds_not_an_iso_string() throws Exception {
        String json = objectMapper.writeValueAsString(
            new TokenResponse("compact.jwt.value", "Bearer", Duration.ofMinutes(15))
        );

        assertThat(json).doesNotContain("PT15M");
        assertThat(objectMapper.readTree(json).get("expires_in").isNumber()).isTrue();
        assertThat(objectMapper.readTree(json).get("expires_in").asDouble()).isEqualTo(900.0);
    }

    @Test
    void never_carries_the_refresh_token_in_the_body() {
        // The refresh travels in the X-Refresh-Token header. Adding it to this
        // record would leak it into every response body and into the BFF logs
        // that record bodies.
        assertThat(TokenResponse.class.getRecordComponents())
            .extracting(java.lang.reflect.RecordComponent::getName)
            .containsExactly("accessToken", "tokenType", "expiresIn");
    }
}
