package com.menta.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordEncoderAdapterTest {

    @Test
    void delegates_encoding_and_matching_to_the_spring_encoder() {
        PasswordEncoder delegate = mock(PasswordEncoder.class);
        when(delegate.encode("Secret1Pass")).thenReturn("encoded");
        when(delegate.matches("Secret1Pass", "encoded")).thenReturn(true);
        PasswordEncoderAdapter adapter = new PasswordEncoderAdapter(delegate);

        assertThat(adapter.encode("Secret1Pass")).isEqualTo("encoded");
        assertThat(adapter.matches("Secret1Pass", "encoded")).isTrue();
    }

    @Test
    void encodes_a_fresh_random_secret_of_at_least_32_bytes_every_time() {
        PasswordEncoder delegate = mock(PasswordEncoder.class);
        when(delegate.encode(anyString())).thenReturn("encoded");
        PasswordEncoderAdapter adapter = new PasswordEncoderAdapter(delegate);

        assertThat(adapter.encodeUnusable()).isEqualTo("encoded");
        adapter.encodeUnusable();

        ArgumentCaptor<String> secrets = ArgumentCaptor.forClass(String.class);
        verify(delegate, times(2)).encode(secrets.capture());
        List<String> raw = secrets.getAllValues();
        assertThat(raw.get(0)).isNotEqualTo(raw.get(1));
        assertThat(Base64.getUrlDecoder().decode(raw.get(0))).hasSizeGreaterThanOrEqualTo(32);
    }

    @Test
    void the_unusable_hash_is_a_real_hash_that_no_guessable_password_matches() {
        PasswordEncoder bcrypt = new BCryptPasswordEncoder();
        PasswordEncoderAdapter adapter = new PasswordEncoderAdapter(bcrypt);

        String first = adapter.encodeUnusable();
        String second = adapter.encodeUnusable();

        assertThat(first).startsWith("$2").isNotEqualTo(second);
        assertThat(adapter.matches("", first)).isFalse();
        assertThat(adapter.matches("Password1", first)).isFalse();
    }
}
