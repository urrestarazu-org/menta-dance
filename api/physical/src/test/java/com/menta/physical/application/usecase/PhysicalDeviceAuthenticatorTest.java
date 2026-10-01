package com.menta.physical.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.menta.physical.application.dto.DeviceAuthenticationRejection;
import com.menta.physical.application.dto.DeviceAuthenticationRejectionReason;
import com.menta.physical.application.port.out.Clock;
import com.menta.physical.application.port.out.DeviceAuthenticationRejectionPort;
import com.menta.physical.application.port.out.DeviceSecretHasher;
import com.menta.physical.application.port.out.PhysicalDeviceRepository;
import com.menta.physical.domain.exception.CheckInDeviceRevokedException;
import com.menta.physical.domain.exception.DeviceExpiredException;
import com.menta.physical.domain.exception.InvalidDeviceTokenException;
import com.menta.physical.domain.model.DeviceId;
import com.menta.physical.domain.model.DeviceStatus;
import com.menta.physical.domain.model.PhysicalDevice;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PhysicalDeviceAuthenticatorTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final UUID DEVICE_UUID = UUID.fromString("0b4e7a52-8c3d-4f6e-9a1b-2d5c7e9f1a30");
    private static final String SECRET = "reader-secret";
    private static final String SECRET_HASH = "a".repeat(64);
    private static final String OTHER_SECRET = "not-the-secret";
    private static final String OTHER_HASH = "b".repeat(64);

    private PhysicalDeviceRepository repository;
    private DeviceSecretHasher hasher;
    private Clock clock;
    private DeviceAuthenticationRejectionPort rejectionPort;
    private PhysicalDeviceAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        repository = mock(PhysicalDeviceRepository.class);
        hasher = mock(DeviceSecretHasher.class);
        clock = mock(Clock.class);
        rejectionPort = mock(DeviceAuthenticationRejectionPort.class);
        when(clock.now()).thenReturn(NOW);
        when(hasher.hash(SECRET)).thenReturn(SECRET_HASH);
        when(hasher.hash(OTHER_SECRET)).thenReturn(OTHER_HASH);
        authenticator = new PhysicalDeviceAuthenticator(repository, hasher, clock, rejectionPort);
    }

    private static PhysicalDevice device(DeviceStatus status, Instant expiresAt) {
        return new PhysicalDevice(
            DeviceId.of(DEVICE_UUID), "Front door", "Lobby", SECRET_HASH, status, expiresAt,
            NOW.minus(Duration.ofDays(30)), NOW.minus(Duration.ofDays(30))
        );
    }

    private void givenStoredDevice(DeviceStatus status, Instant expiresAt) {
        when(repository.findById(DeviceId.of(DEVICE_UUID)))
            .thenReturn(Optional.of(device(status, expiresAt)));
    }

    private void assertReportedOnce(DeviceAuthenticationRejectionReason reason, UUID deviceId) {
        verify(rejectionPort, times(1)).report(new DeviceAuthenticationRejection(reason, deviceId));
        verifyNoMoreInteractions(rejectionPort);
    }

    // --- pre-check: nothing reaches the database ---

    @ParameterizedTest
    @ValueSource(strings = {
        "reader-1", "not-a-uuid", "1-1-1-1-1", "", "   ",
        "0b4e7a52-8c3d-4f6e-9a1b-2d5c7e9f1a3",
        "0b4e7a52-8c3d-4f6e-9a1b-2d5c7e9f1a300",
        "0b4e7a52-8c3d-4f6e-9a1b-2d5c7e9f1a30\n",
        "0b4e7a52-8c3d-4f6e-9a1b-2d5c7e9f1a3g"
    })
    void a_malformed_device_id_is_rejected_before_any_database_access(String rawDeviceId) {
        assertThatThrownBy(() -> authenticator.authenticate(rawDeviceId, SECRET))
            .isInstanceOf(InvalidDeviceTokenException.class);

        verifyNoInteractions(repository);
        assertReportedOnce(DeviceAuthenticationRejectionReason.UNKNOWN_OR_INVALID, null);
    }

    @ParameterizedTest
    @NullSource
    void a_null_device_id_is_rejected_before_any_database_access(String rawDeviceId) {
        assertThatThrownBy(() -> authenticator.authenticate(rawDeviceId, SECRET))
            .isInstanceOf(InvalidDeviceTokenException.class);

        verifyNoInteractions(repository);
        assertReportedOnce(DeviceAuthenticationRejectionReason.UNKNOWN_OR_INVALID, null);
    }

    @Test
    void a_null_secret_is_rejected_before_any_database_access_keeping_the_valid_id() {
        assertThatThrownBy(() -> authenticator.authenticate(DEVICE_UUID.toString(), null))
            .isInstanceOf(InvalidDeviceTokenException.class);

        verifyNoInteractions(repository);
        assertReportedOnce(DeviceAuthenticationRejectionReason.UNKNOWN_OR_INVALID, DEVICE_UUID);
    }

    // --- secret proof ---

    @Test
    void an_unknown_uuid_still_pays_the_hash_cost_and_reports_unknown_or_invalid() {
        when(repository.findById(DeviceId.of(DEVICE_UUID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticator.authenticate(DEVICE_UUID.toString(), SECRET))
            .isInstanceOf(InvalidDeviceTokenException.class);

        verify(hasher).hash(SECRET);
        assertReportedOnce(DeviceAuthenticationRejectionReason.UNKNOWN_OR_INVALID, DEVICE_UUID);
    }

    @Test
    void a_wrong_secret_reports_unknown_or_invalid_with_the_device_id() {
        givenStoredDevice(DeviceStatus.ACTIVE, null);

        assertThatThrownBy(() -> authenticator.authenticate(DEVICE_UUID.toString(), OTHER_SECRET))
            .isInstanceOf(InvalidDeviceTokenException.class);

        verify(hasher).hash(OTHER_SECRET);
        assertReportedOnce(DeviceAuthenticationRejectionReason.UNKNOWN_OR_INVALID, DEVICE_UUID);
    }

    // --- status and expiry, only after the secret is proven ---

    @Test
    void a_revoked_device_with_the_correct_secret_is_reported_as_revoked() {
        givenStoredDevice(DeviceStatus.REVOKED, null);

        assertThatThrownBy(() -> authenticator.authenticate(DEVICE_UUID.toString(), SECRET))
            .isInstanceOf(CheckInDeviceRevokedException.class);

        assertReportedOnce(DeviceAuthenticationRejectionReason.REVOKED, DEVICE_UUID);
    }

    @Test
    void a_revoked_device_with_a_wrong_secret_reveals_nothing() {
        givenStoredDevice(DeviceStatus.REVOKED, null);

        assertThatThrownBy(() -> authenticator.authenticate(DEVICE_UUID.toString(), OTHER_SECRET))
            .isInstanceOf(InvalidDeviceTokenException.class);

        assertReportedOnce(DeviceAuthenticationRejectionReason.UNKNOWN_OR_INVALID, DEVICE_UUID);
    }

    @Test
    void an_expiry_equal_to_now_is_expired_inclusive() {
        givenStoredDevice(DeviceStatus.ACTIVE, NOW);

        assertThatThrownBy(() -> authenticator.authenticate(DEVICE_UUID.toString(), SECRET))
            .isInstanceOf(DeviceExpiredException.class);

        assertReportedOnce(DeviceAuthenticationRejectionReason.EXPIRED, DEVICE_UUID);
    }

    @Test
    void an_expiry_in_the_past_is_expired() {
        givenStoredDevice(DeviceStatus.ACTIVE, NOW.minusSeconds(1));

        assertThatThrownBy(() -> authenticator.authenticate(DEVICE_UUID.toString(), SECRET))
            .isInstanceOf(DeviceExpiredException.class);

        assertReportedOnce(DeviceAuthenticationRejectionReason.EXPIRED, DEVICE_UUID);
    }

    @Test
    void an_expired_device_with_a_wrong_secret_reveals_nothing() {
        givenStoredDevice(DeviceStatus.ACTIVE, NOW.minusSeconds(1));

        assertThatThrownBy(() -> authenticator.authenticate(DEVICE_UUID.toString(), OTHER_SECRET))
            .isInstanceOf(InvalidDeviceTokenException.class);

        assertReportedOnce(DeviceAuthenticationRejectionReason.UNKNOWN_OR_INVALID, DEVICE_UUID);
    }

    @Test
    void revoked_takes_precedence_over_expired() {
        givenStoredDevice(DeviceStatus.REVOKED, NOW.minus(Duration.ofDays(1)));

        assertThatThrownBy(() -> authenticator.authenticate(DEVICE_UUID.toString(), SECRET))
            .isInstanceOf(CheckInDeviceRevokedException.class);

        assertReportedOnce(DeviceAuthenticationRejectionReason.REVOKED, DEVICE_UUID);
    }

    // --- success ---

    @Test
    void an_active_device_without_expiry_authenticates_and_reports_nothing() {
        givenStoredDevice(DeviceStatus.ACTIVE, null);

        DeviceId authenticated = authenticator.authenticate(DEVICE_UUID.toString(), SECRET);

        assertThat(authenticated).isEqualTo(DeviceId.of(DEVICE_UUID));
        verifyNoInteractions(rejectionPort);
    }

    @Test
    void an_active_device_with_a_future_expiry_authenticates_and_reports_nothing() {
        givenStoredDevice(DeviceStatus.ACTIVE, NOW.plusSeconds(1));

        DeviceId authenticated = authenticator.authenticate(DEVICE_UUID.toString(), SECRET);

        assertThat(authenticated).isEqualTo(DeviceId.of(DEVICE_UUID));
        verifyNoInteractions(rejectionPort);
    }

    @Test
    void an_uppercase_device_id_is_normalized_to_the_canonical_lowercase_uuid() {
        givenStoredDevice(DeviceStatus.ACTIVE, null);

        DeviceId authenticated =
            authenticator.authenticate(DEVICE_UUID.toString().toUpperCase(), SECRET);

        assertThat(authenticated.toString()).isEqualTo(DEVICE_UUID.toString());
        verifyNoInteractions(rejectionPort);
    }

    @Test
    void an_uppercase_device_id_is_reported_in_its_canonical_form() {
        givenStoredDevice(DeviceStatus.ACTIVE, null);

        assertThatThrownBy(
            () -> authenticator.authenticate(DEVICE_UUID.toString().toUpperCase(), OTHER_SECRET)
        ).isInstanceOf(InvalidDeviceTokenException.class);

        assertReportedOnce(DeviceAuthenticationRejectionReason.UNKNOWN_OR_INVALID, DEVICE_UUID);
    }
}
