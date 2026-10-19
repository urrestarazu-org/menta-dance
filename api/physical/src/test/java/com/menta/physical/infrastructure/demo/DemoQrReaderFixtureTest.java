package com.menta.physical.infrastructure.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.DeviceSecretHasher;
import com.menta.physical.application.port.out.PhysicalDeviceRepository;
import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.domain.model.DeviceStatus;
import com.menta.physical.domain.model.PhysicalDevice;
import com.menta.shared.demo.DemoSeedOrder;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

class DemoQrReaderFixtureTest {

    private static final Instant NOW = Instant.parse("2026-10-07T17:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-10-01T10:00:00Z");
    private static final String SECRET = "s3cret-value";
    private static final String HASH = "hash-of-current-secret";
    private static final String OLD_HASH = "hash-of-old-secret";

    private final PhysicalDeviceRepository devices = mock(PhysicalDeviceRepository.class);
    private final DeviceSecretHasher hasher = mock(DeviceSecretHasher.class);
    private final Clock clock = () -> NOW;
    private final Logger logger = (Logger) LoggerFactory.getLogger(DemoQrReaderFixture.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void releaseLogs() {
        logger.detachAppender(logs);
    }

    private DemoQrReaderFixture fixtureWith(String secret) {
        return new DemoQrReaderFixture(devices, hasher, clock, secret);
    }

    private PhysicalDevice stored(String hash, DeviceStatus status) {
        return new PhysicalDevice(
            DeviceId.of(DemoQrReaderFixture.DEVICE_ID), "Demo QR reader", "Demo entrance", hash, status, null,
            EARLIER, EARLIER);
    }

    @Test
    void blank_secret_logs_a_warning_and_makes_no_repository_calls() {
        fixtureWith("  ").run(mock());

        verifyNoInteractions(devices, hasher);
        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void empty_secret_is_skipped_too() {
        fixtureWith("").run(mock());

        verifyNoInteractions(devices, hasher);
    }

    @Test
    void null_secret_is_skipped_too() {
        fixtureWith(null).run(mock());

        verifyNoInteractions(devices, hasher);
    }

    @Test
    void absent_device_is_saved_active_with_the_hash_and_never_the_plaintext() {
        when(devices.findById(DeviceId.of(DemoQrReaderFixture.DEVICE_ID))).thenReturn(Optional.empty());
        when(hasher.hash(SECRET)).thenReturn(HASH);

        fixtureWith(SECRET).run(mock());

        ArgumentCaptor<PhysicalDevice> captor = ArgumentCaptor.forClass(PhysicalDevice.class);
        verify(devices).save(captor.capture());
        PhysicalDevice saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(DeviceId.of(DemoQrReaderFixture.DEVICE_ID));
        assertThat(saved.getSecretHash()).isEqualTo(HASH).isNotEqualTo(SECRET);
        assertThat(saved.getStatus()).isEqualTo(DeviceStatus.ACTIVE);
        assertThat(saved.getExpiresAt()).isNull();
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void same_hash_is_a_no_op() {
        when(devices.findById(DeviceId.of(DemoQrReaderFixture.DEVICE_ID)))
            .thenReturn(Optional.of(stored(HASH, DeviceStatus.ACTIVE)));
        when(hasher.hash(SECRET)).thenReturn(HASH);

        fixtureWith(SECRET).run(mock());

        verify(devices, never()).save(any());
    }

    @Test
    void different_hash_on_an_active_device_rotates_the_secret() {
        when(devices.findById(DeviceId.of(DemoQrReaderFixture.DEVICE_ID)))
            .thenReturn(Optional.of(stored(OLD_HASH, DeviceStatus.ACTIVE)));
        when(hasher.hash(SECRET)).thenReturn(HASH);

        fixtureWith(SECRET).run(mock());

        ArgumentCaptor<PhysicalDevice> captor = ArgumentCaptor.forClass(PhysicalDevice.class);
        verify(devices).save(captor.capture());
        assertThat(captor.getValue().getSecretHash()).isEqualTo(HASH);
        assertThat(captor.getValue().getStatus()).isEqualTo(DeviceStatus.ACTIVE);
        assertThat(captor.getValue().getUpdatedAt()).isEqualTo(NOW);
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(EARLIER);
    }

    @Test
    void revoked_device_is_left_untouched_with_a_warning() {
        when(devices.findById(DeviceId.of(DemoQrReaderFixture.DEVICE_ID)))
            .thenReturn(Optional.of(stored(OLD_HASH, DeviceStatus.REVOKED)));
        when(hasher.hash(SECRET)).thenReturn(HASH);

        fixtureWith(SECRET).run(mock());

        verify(devices, never()).save(any());
        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.get(0).getLevel()).isEqualTo(Level.WARN);
    }

    @Test
    void neither_the_secret_nor_the_hash_is_ever_logged() {
        when(devices.findById(DeviceId.of(DemoQrReaderFixture.DEVICE_ID)))
            .thenReturn(Optional.of(stored(OLD_HASH, DeviceStatus.REVOKED)));
        when(hasher.hash(SECRET)).thenReturn(HASH);

        fixtureWith(SECRET).run(mock());
        fixtureWith(" ").run(mock());

        assertThat(logs.list).isNotEmpty().allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain(SECRET, HASH, OLD_HASH);
            assertThat(event.getArgumentArray()).isNull();
        });
    }

    @Test
    void runs_in_the_content_phase_of_the_seed() {
        assertThat(fixtureWith(SECRET).getOrder()).isEqualTo(DemoSeedOrder.CONTENT);
    }
}
