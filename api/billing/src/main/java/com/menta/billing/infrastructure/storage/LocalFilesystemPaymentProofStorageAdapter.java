package com.menta.billing.infrastructure.storage;

import com.menta.billing.application.port.out.PaymentProofStoragePort;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Filesystem-backed {@link PaymentProofStoragePort} over a Docker volume (#31, US-BILLING-003,
 * design C5). Path traversal is impossible by construction — every {@code storageKey} produced by
 * {@link com.menta.billing.domain.model.PaymentProof#create} contains only two server-generated
 * UUIDs and a whitelisted extension — but {@link #resolveWithinRoot} still asserts {@code
 * root.resolve(key).normalize().startsWith(root)} as defense in depth. The constructor performs no
 * I/O: the volume directory is created lazily on first write, so a missing mount never fails
 * application startup.
 *
 * <p>Self-registers as a {@code @Component}, mirroring {@code PaymentRepositoryAdapter} — no
 * explicit {@code @Bean} wiring needed in {@code BillingConfiguration}. Structurally inert until
 * P3c wires a consumer (this phase, P3a, ships the adapter with nothing calling it yet).</p>
 */
@Component
public class LocalFilesystemPaymentProofStorageAdapter implements PaymentProofStoragePort {

    private final Path root;

    public LocalFilesystemPaymentProofStorageAdapter(
        @Value("${billing.bank-transfer.proof.storage-root:/var/menta-dance/payment-proofs}") String storageRoot
    ) {
        this.root = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @Override
    public void store(String storageKey, byte[] content) {
        Path target = resolveWithinRoot(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store payment proof at key " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path target = resolveWithinRoot(storageKey);
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete payment proof at key " + storageKey, e);
        }
    }

    private Path resolveWithinRoot(String storageKey) {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("storageKey escapes the storage root: " + storageKey);
        }
        return target;
    }
}
