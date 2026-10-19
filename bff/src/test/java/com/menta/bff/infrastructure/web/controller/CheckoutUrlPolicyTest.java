package com.menta.bff.infrastructure.web.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CheckoutUrlPolicy")
class CheckoutUrlPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "https://pay.example.com/checkout/1?pref=abc",
        "HTTPS://pay.example.com/checkout/1",
        "http://localhost:8081/e2e/checkout",
        "http://LOCALHOST/checkout",
        "http://127.0.0.1:9999/checkout",
        "http://[::1]:9999/checkout"})
    @DisplayName("accepts https URLs and http only for a loopback host")
    void allowed(String url) {
        assertThat(CheckoutUrlPolicy.isAllowed(url)).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "",
        "/relative/path",
        "//pay.example.com/checkout",
        "http://pay.example.com/checkout",
        "http://localhost.example.com/checkout",
        "http://localhost@pay.example.com/checkout",
        "javascript:alert(1)",
        "data:text/html,hi",
        "ftp://localhost/file",
        "https:///nohost",
        "https://pay.example.com/a b",
        "https://pay.example.com/{x}"})
    @DisplayName("refuses everything else")
    void refused(String url) {
        assertThat(CheckoutUrlPolicy.isAllowed(url)).isFalse();
    }
}
