package com.menta.bff.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.menta.bff.application.port.out.AccessTokenClaimsReader.UnreadableTokenException;
import com.menta.bff.infrastructure.integration.TestAccessTokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The role claim becomes a Spring Security authority, so anything that does
 * not look like an Auth API role name must fail the login instead of turning
 * into an unexpected authority.
 */
class JwtAccessTokenClaimsReaderTest {

    private final JwtAccessTokenClaimsReader reader = new JwtAccessTokenClaimsReader(new ObjectMapper());

    @ParameterizedTest
    @ValueSource(strings = {"STUDENT", "INSTRUCTOR", "ADMIN"})
    @DisplayName("Reads the role claim issued by the Auth API")
    void reads_the_role_claim(String role) {
        assertThat(reader.roleOf(TestAccessTokens.withRole(role))).isEqualTo(role);
    }

    @Test
    void rejects_a_null_token() {
        assertThatThrownBy(() -> reader.roleOf(null)).isInstanceOf(UnreadableTokenException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"opaque-token", "a.b", "a.b.c.d", "header.%%%.signature", "header.bm90LWpzb24.signature"})
    void rejects_tokens_that_are_not_readable_jwts(String token) {
        assertThatThrownBy(() -> reader.roleOf(token)).isInstanceOf(UnreadableTokenException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"sub\":\"1\"}",
        "{\"role\":null}",
        "{\"role\":42}",
        "{\"role\":[\"ADMIN\"]}",
        "{\"role\":\"\"}",
        "{\"role\":\"student\"}",
        "{\"role\":\"ADMIN,ROLE_X\"}",
        "{\"role\":\"ROLE ADMIN\"}"
    })
    void rejects_payloads_without_a_valid_role(String payload) {
        assertThatThrownBy(() -> reader.roleOf(TestAccessTokens.withPayload(payload)))
            .isInstanceOf(UnreadableTokenException.class);
    }

    @Test
    void rejects_a_payload_that_is_not_a_json_object() {
        assertThatThrownBy(() -> reader.roleOf(TestAccessTokens.withPayload("\"STUDENT\"")))
            .isInstanceOf(UnreadableTokenException.class);
    }

    @Test
    @DisplayName("Reads the subject claim: the id of the account the Auth API issued the token to")
    void reads_the_subject_claim() {
        String token = TestAccessTokens.withPayload("{\"sub\":\"5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f\"}");

        assertThat(reader.subjectOf(token)).isEqualTo("5b6f7c8e-1d2a-4e3b-9c4d-0a1b2c3d4e5f");
    }

    @Test
    void rejects_a_null_or_unreadable_token_for_the_subject() {
        assertThatThrownBy(() -> reader.subjectOf(null)).isInstanceOf(UnreadableTokenException.class);
        assertThatThrownBy(() -> reader.subjectOf("opaque-token")).isInstanceOf(UnreadableTokenException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"role\":\"ADMIN\"}", "{\"sub\":null}", "{\"sub\":42}", "{\"sub\":\"\"}", "{\"sub\":\"  \"}"})
    void rejects_payloads_without_a_subject(String payload) {
        assertThatThrownBy(() -> reader.subjectOf(TestAccessTokens.withPayload(payload)))
            .isInstanceOf(UnreadableTokenException.class);
    }
}
