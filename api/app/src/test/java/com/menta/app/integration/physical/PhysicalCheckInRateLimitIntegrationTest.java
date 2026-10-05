package com.menta.app.integration.physical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.app.integration.support.AbstractPhysicalMySqlIntegrationTest;
import com.menta.auth.application.port.out.ActivationRateLimitPort;
import com.menta.auth.application.port.out.AuthDegradedGuard;
import com.menta.auth.application.port.out.LoginRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetAttemptRateLimitPort;
import com.menta.auth.application.port.out.PasswordResetRequestRateLimitPort;
import com.menta.auth.application.port.out.TokenBlacklistPort;
import com.menta.billing.application.port.out.BankTransferRateLimitPort;
import com.menta.billing.application.port.out.BillingPlansRateLimitPort;
import com.menta.billing.application.port.out.CourseCatalogPort;
import com.menta.physical.application.dto.RateLimitDecision;
import com.menta.physical.application.dto.RateLimitDimension;
import com.menta.physical.application.port.out.CheckInRateLimitPort;
import com.menta.physical.application.port.out.PhysicalDeviceRepository;
import com.menta.physical.domain.exception.CheckInDegradedException;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

/**
 * ADR-0044 over real HTTP: the input caps on the QR check-in body and the rate-limit gate that
 * runs before the device registry is consulted.
 *
 * <p>The rate-limit port is mocked (a real one would sit on the mocked {@code RedisTemplate} and
 * fail closed); its Redis behaviour is covered by {@code RedisCheckInRateLimitPortTest}. What this
 * class proves is the wiring: bean validation first, then the gate, then nothing else.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration-test")
class PhysicalCheckInRateLimitIntegrationTest extends AbstractPhysicalMySqlIntegrationTest {

    private static final String SHA_256_HEX = "[0-9a-f]{64}";
    private static final String COUNTER = "physical.checkin.rate.limited";

    @Autowired private TestRestTemplate http;
    @Autowired private MeterRegistry meterRegistry;

    @SpyBean private PhysicalDeviceRepository deviceRegistry;

    @MockBean private AuthDegradedGuard authDegradedGuard;
    @MockBean private TokenBlacklistPort tokenBlacklistPort;
    @MockBean private LoginRateLimitPort loginRateLimitPort;
    @MockBean private ActivationRateLimitPort activationRateLimitPort;
    @MockBean private PasswordResetRequestRateLimitPort passwordResetRequestRateLimitPort;
    @MockBean private PasswordResetAttemptRateLimitPort passwordResetAttemptRateLimitPort;
    @MockBean private BillingPlansRateLimitPort billingPlansRateLimitPort;
    @MockBean private BankTransferRateLimitPort bankTransferRateLimitPort;
    @MockBean private CourseCatalogPort courseCatalogPort;
    @MockBean private CheckInRateLimitPort checkInRateLimitPort;

    @SuppressWarnings("rawtypes")
    @MockBean
    private RedisTemplate redisTemplate;

    private ResponseEntity<Map> post(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(
            "/api/v1/physical/sessions/" + UUID.randomUUID() + "/check-ins", HttpMethod.POST,
            new HttpEntity<>(body, headers), Map.class
        );
    }

    private ResponseEntity<Map> qrCheckIn(String deviceId, String deviceToken, String credentials) {
        return post(Map.of(
            "type", "QR", "qrCredentials", credentials,
            "deviceId", deviceId, "deviceToken", deviceToken
        ));
    }

    private double limitedCount(String dimension) {
        return meterRegistry.get(COUNTER).tag("dimension", dimension).counter().count();
    }

    private static String sha256Hex(String value) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
        );
    }

    @Test
    void oversized_device_id_token_or_credentials_are_refused_before_any_gate() {
        UUID deviceId = UUID.randomUUID();

        ResponseEntity<Map> longDeviceId = qrCheckIn("d".repeat(37), "token", "qr:x");
        ResponseEntity<Map> longToken = qrCheckIn(deviceId.toString(), "t".repeat(44), "qr:x");
        ResponseEntity<Map> longCredentials =
            qrCheckIn(deviceId.toString(), "token", "q".repeat(513));

        for (ResponseEntity<Map> response : List.of(longDeviceId, longToken, longCredentials)) {
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().get("code")).isEqualTo("INVALID_REQUEST");
        }
        verifyNoInteractions(checkInRateLimitPort, deviceRegistry, redisTemplate);
    }

    @Test
    void a_rate_limited_qr_check_in_returns_429_without_touching_the_device_registry() {
        when(checkInRateLimitPort.consume(any(), any())).thenReturn(
            RateLimitDecision.limited(RateLimitDimension.IP, Duration.ofSeconds(23))
        );
        final double ipBefore = limitedCount("ip");
        final double deviceBefore = limitedCount("device");

        ResponseEntity<Map> response =
            qrCheckIn(UUID.randomUUID().toString(), "token", "qr:payload");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("23");
        assertThat(response.getHeaders().getContentType())
            .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().get("code")).isEqualTo("CHECK_IN_RATE_LIMITED");
        verifyNoInteractions(deviceRegistry, redisTemplate);
        assertThat(limitedCount("ip")).isEqualTo(ipBefore + 1);
        assertThat(limitedCount("device")).isEqualTo(deviceBefore);
    }

    @Test
    void the_gate_receives_bounded_fingerprints_never_the_raw_values() throws Exception {
        when(checkInRateLimitPort.consume(any(), any())).thenReturn(
            RateLimitDecision.limited(RateLimitDimension.DEVICE, Duration.ofSeconds(5))
        );
        String deviceId = UUID.randomUUID().toString();

        qrCheckIn(deviceId, "token", "qr:payload");

        ArgumentCaptor<String> client = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> device = ArgumentCaptor.forClass(String.class);
        verify(checkInRateLimitPort).consume(client.capture(), device.capture());
        assertThat(client.getValue()).matches(SHA_256_HEX);
        assertThat(device.getValue()).isEqualTo(sha256Hex(deviceId));
    }

    @Test
    void a_degraded_rate_limiter_fails_closed_with_503_before_any_device_lookup() {
        when(checkInRateLimitPort.consume(any(), any())).thenThrow(new CheckInDegradedException());

        ResponseEntity<Map> response =
            qrCheckIn(UUID.randomUUID().toString(), "token", "qr:payload");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().get("code")).isEqualTo("CHECK_IN_DEGRADED");
        verifyNoInteractions(deviceRegistry);
    }
}
