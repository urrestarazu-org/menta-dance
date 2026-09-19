package com.menta.billing.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit coverage for {@link LocalFilesystemPaymentProofStorageAdapter} (#31, US-BILLING-003, phase
 * P3a, design C5). No Spring context — the root is injected directly, mirroring the constructor's
 * {@code @Value}-bound shape.
 */
class LocalFilesystemPaymentProofStorageAdapterTest {

    @TempDir
    Path volumeRoot;

    @Test
    void store_writes_the_file_under_the_volume_root() throws IOException {
        LocalFilesystemPaymentProofStorageAdapter adapter =
            new LocalFilesystemPaymentProofStorageAdapter(volumeRoot.toString());
        byte[] content = "comprobante".getBytes(StandardCharsets.UTF_8);

        adapter.store("11111111-1111-1111-1111-111111111111/22222222-2222-2222-2222-222222222222.png", content);

        Path written = volumeRoot.resolve(
            "11111111-1111-1111-1111-111111111111/22222222-2222-2222-2222-222222222222.png"
        );
        assertThat(written).exists();
        assertThat(Files.readAllBytes(written)).isEqualTo(content);
    }

    @Test
    void store_refuses_a_hand_crafted_key_that_escapes_the_root() {
        LocalFilesystemPaymentProofStorageAdapter adapter =
            new LocalFilesystemPaymentProofStorageAdapter(volumeRoot.toString());
        byte[] content = "evil".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> adapter.store("../escaped.png", content))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(volumeRoot.resolveSibling("escaped.png")).doesNotExist();
    }

    @Test
    void delete_refuses_a_hand_crafted_key_that_escapes_the_root() {
        LocalFilesystemPaymentProofStorageAdapter adapter =
            new LocalFilesystemPaymentProofStorageAdapter(volumeRoot.toString());

        assertThatThrownBy(() -> adapter.delete("../../etc/passwd"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void delete_of_a_missing_key_is_silent() {
        LocalFilesystemPaymentProofStorageAdapter adapter =
            new LocalFilesystemPaymentProofStorageAdapter(volumeRoot.toString());

        assertThatCode(() -> adapter.delete("no-such-payment/no-such-proof.pdf")).doesNotThrowAnyException();
    }

    @Test
    void delete_removes_a_previously_stored_file() {
        LocalFilesystemPaymentProofStorageAdapter adapter =
            new LocalFilesystemPaymentProofStorageAdapter(volumeRoot.toString());
        String key = "33333333-3333-3333-3333-333333333333/44444444-4444-4444-4444-444444444444.pdf";
        adapter.store(key, "contenido".getBytes(StandardCharsets.UTF_8));

        adapter.delete(key);

        assertThat(volumeRoot.resolve(key)).doesNotExist();
    }
}
