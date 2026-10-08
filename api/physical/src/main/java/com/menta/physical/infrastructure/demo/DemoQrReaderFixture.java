package com.menta.physical.infrastructure.demo;

import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.DeviceSecretHasher;
import com.menta.physical.application.port.out.PhysicalDeviceRepository;
import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.domain.model.DeviceStatus;
import com.menta.physical.domain.model.PhysicalDevice;
import com.menta.shared.demo.DemoSeedOrder;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Seeds one demo QR reader device whose secret is supplied by the operator.
 *
 * <p>No use case lets the caller choose the device secret (registration and rotation both generate it), so the
 * fixture uses {@link PhysicalDeviceRepository} and {@link DeviceSecretHasher} directly, inside this module. The
 * secret comes only from {@code demo.qr-reader.secret} ({@code DEMO_QR_READER_SECRET}):
 * <ul>
 *   <li>blank: the reader is skipped with a WARN and nothing is read or written;</li>
 *   <li>device absent: created {@code ACTIVE} with the hashed secret;</li>
 *   <li>device {@code ACTIVE} and hash differs: the secret hash is rotated;</li>
 *   <li>same hash: nothing happens;</li>
 *   <li>device {@code REVOKED}: left untouched with a WARN ({@code REVOKED} is absorbing).</li>
 * </ul>
 * No audit row is written because the audit snapshot helper is package-private to the use case package. Neither
 * the secret nor its hash is ever logged. The runner is intentionally not transactional: each repository call is
 * its own unit and the operation is idempotent.
 */
@Component
@Profile("demo")
@Slf4j
public final class DemoQrReaderFixture implements ApplicationRunner, Ordered {

    static final UUID DEVICE_ID = UUID.fromString("de000000-0000-4000-8000-0000000005a1");

    private static final String NAME = "Demo QR reader";
    private static final String LOCATION = "Demo entrance";

    private final PhysicalDeviceRepository deviceRepository;
    private final DeviceSecretHasher secretHasher;
    private final Clock clock;
    private final String secret;

    public DemoQrReaderFixture(
        PhysicalDeviceRepository deviceRepository, DeviceSecretHasher secretHasher, Clock clock,
        @Value("${demo.qr-reader.secret:}") String secret
    ) {
        this.deviceRepository = deviceRepository;
        this.secretHasher = secretHasher;
        this.clock = clock;
        this.secret = secret;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (secret == null || secret.isBlank()) {
            log.warn("DEMO_QR_READER_SECRET is not set: skipping the demo QR reader seed");
            return;
        }
        String hash = secretHasher.hash(secret);
        DeviceId id = DeviceId.of(DEVICE_ID);
        deviceRepository.findById(id).ifPresentOrElse(
            existing -> reconcile(existing, hash),
            () -> create(id, hash));
    }

    @Override
    public int getOrder() {
        return DemoSeedOrder.CONTENT;
    }

    private void create(DeviceId id, String hash) {
        Instant now = clock.now();
        deviceRepository.save(new PhysicalDevice(id, NAME, LOCATION, hash, DeviceStatus.ACTIVE, null, now, now));
    }

    private void reconcile(PhysicalDevice existing, String hash) {
        if (existing.getStatus() == DeviceStatus.REVOKED) {
            log.warn("The demo QR reader is REVOKED: leaving it untouched");
            return;
        }
        if (!existing.getSecretHash().equals(hash)) {
            deviceRepository.save(existing.rotateSecret(hash, clock.now()));
        }
    }
}
