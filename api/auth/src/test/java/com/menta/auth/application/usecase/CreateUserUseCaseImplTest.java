package com.menta.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.dto.CreateUserCommand;
import com.menta.auth.application.dto.UserSummary;
import com.menta.auth.application.port.out.ActivationDeliveryCipher;
import com.menta.auth.application.port.out.ActivationTokenGenerator;
import com.menta.auth.application.port.out.ActivationTokenHasher;
import com.menta.auth.application.port.out.ActivationTokenRepository;
import com.menta.auth.application.port.out.Clock;
import com.menta.auth.application.port.out.DeliveryEnvelope;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.application.port.out.PasswordEncoderPort;
import com.menta.auth.application.port.out.UserAuditRepository;
import com.menta.auth.domain.exception.EmailAlreadyRegisteredException;
import com.menta.auth.domain.model.ActivationToken;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserProfile;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreateUserUseCaseImplTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final DeliveryEnvelope ENVELOPE =
        DeliveryEnvelope.of("cipher".getBytes(StandardCharsets.UTF_8), new byte[12], 1);

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoderPort passwordEncoder;
    @Mock private ActivationTokenRepository activationTokenRepository;
    @Mock private ActivationTokenGenerator activationTokenGenerator;
    @Mock private ActivationTokenHasher activationTokenHasher;
    @Mock private ActivationDeliveryCipher activationDeliveryCipher;
    @Mock private OutboxAppender outboxAppender;
    @Mock private UserAuditRepository userAuditRepository;
    @Mock private Clock clock;

    private CreateUserUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateUserUseCaseImpl(
            userRepository,
            passwordEncoder,
            new ActivationTokenIssuer(
                activationTokenRepository, activationTokenGenerator, activationTokenHasher,
                activationDeliveryCipher, outboxAppender, Duration.ofHours(24)
            ),
            userAuditRepository,
            clock
        );
    }

    private static CreateUserCommand command(Role role) {
        return new CreateUserCommand(ACTOR_ID, "  Staff@Example.com ", " Ada ", "Lovelace", role);
    }

    private void stubHappyPath() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encodeUnusable()).thenReturn("unusable-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(activationTokenGenerator.generate()).thenReturn("raw-activation-token");
        when(activationTokenHasher.hash("raw-activation-token")).thenReturn("c".repeat(64));
        when(activationDeliveryCipher.encrypt(anyString())).thenReturn(ENVELOPE);
        when(activationTokenRepository.save(any(ActivationToken.class), any(DeliveryEnvelope.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(clock.now()).thenReturn(FIXED_NOW);
    }

    private void verifyNothingWasWritten() {
        verify(userRepository, never()).save(any());
        verifyNoInteractions(activationTokenRepository, outboxAppender, userAuditRepository);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"INSTRUCTOR", "RECEPTIONIST", "ADMIN"})
    void creates_a_pending_account_that_must_choose_its_password_for_every_staff_role(Role role) {
        stubHappyPath();

        UserSummary result = useCase.create(command(role));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User user = saved.getValue();
        assertThat(user.getEmail().getValue()).isEqualTo("staff@example.com");
        assertThat(user.getRole()).isEqualTo(role);
        assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(user.isMustSetPassword()).isTrue();
        assertThat(user.isDemo()).isFalse();
        assertThat(user.getPasswordHash()).isEqualTo("unusable-hash");
        assertThat(user.getProfile()).contains(new UserProfile("Ada", "Lovelace", null, null));
        assertThat(result.id()).isEqualTo(user.getId().getValue().toString());
        assertThat(result.email()).isEqualTo("staff@example.com");
        assertThat(result.firstName()).isEqualTo("Ada");
        assertThat(result.role()).isEqualTo(role);
        assertThat(result.status()).isEqualTo(UserStatus.PENDING_ACTIVATION);
        assertThat(result.demo()).isFalse();
    }

    @Test
    void never_hashes_a_password_it_was_given_because_it_is_given_none() {
        stubHappyPath();

        useCase.create(command(Role.INSTRUCTOR));

        verify(passwordEncoder).encodeUnusable();
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void queues_the_activation_email_with_the_same_envelope_as_registration() {
        stubHappyPath();

        useCase.create(command(Role.RECEPTIONIST));

        verify(activationDeliveryCipher).encrypt("staff@example.com|raw-activation-token");
        ArgumentCaptor<ActivationToken> token = ArgumentCaptor.forClass(ActivationToken.class);
        verify(activationTokenRepository).save(token.capture(), eq(ENVELOPE));
        assertThat(token.getValue().getTokenHash()).isEqualTo("c".repeat(64));
        assertThat(token.getValue().getExpiresAt()).isEqualTo(FIXED_NOW.plus(Duration.ofHours(24)));
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outboxAppender).append(
            eq(AuthOutboxEventTypes.ACCOUNT_ACTIVATION_REQUESTED),
            eq(token.getValue().getId().toString()),
            payload.capture()
        );
        assertThat(payload.getValue())
            .isEqualTo("{\"activationTokenId\":\"" + token.getValue().getId() + "\"}")
            .doesNotContain("raw-activation-token");
    }

    @Test
    void audits_the_creation_with_the_actor_the_new_role_and_no_previous_value() {
        stubHappyPath();

        UserSummary result = useCase.create(command(Role.INSTRUCTOR));

        ArgumentCaptor<com.menta.auth.domain.model.UserId> userId =
            ArgumentCaptor.forClass(com.menta.auth.domain.model.UserId.class);
        verify(userAuditRepository).append(
            userId.capture(), eq(ACTOR_ID), eq(UserAuditRepository.USER_CREATED),
            eq(null), eq("INSTRUCTOR"), eq(FIXED_NOW)
        );
        assertThat(userId.getValue().getValue().toString()).isEqualTo(result.id());
        assertThat(UserAuditRepository.USER_CREATED).isEqualTo("USER_CREATED");
    }

    @Test
    void writes_the_account_the_token_the_event_and_the_audit_entry_in_that_order() {
        stubHappyPath();

        useCase.create(command(Role.ADMIN));

        InOrder order = inOrder(userRepository, activationTokenRepository, outboxAppender, userAuditRepository);
        order.verify(userRepository).save(any(User.class));
        order.verify(activationTokenRepository).save(any(ActivationToken.class), any(DeliveryEnvelope.class));
        order.verify(outboxAppender).append(anyString(), anyString(), anyString());
        order.verify(userAuditRepository).append(any(), any(), anyString(), any(), anyString(), any());
    }

    @Test
    void rejects_an_email_that_already_has_an_account_without_writing_anything() {
        when(userRepository.existsByEmail(any())).thenReturn(true);

        assertThatThrownBy(() -> useCase.create(command(Role.INSTRUCTOR)))
            .isInstanceOf(EmailAlreadyRegisteredException.class);

        verifyNothingWasWritten();
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void rejects_the_student_role_because_students_register_themselves() {
        assertThatThrownBy(() -> useCase.create(command(Role.STUDENT)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("STUDENT");

        verifyNoInteractions(userRepository, passwordEncoder);
        verifyNothingWasWritten();
    }

    @Test
    void rejects_a_malformed_email_before_any_lookup() {
        CreateUserCommand command = new CreateUserCommand(ACTOR_ID, "not-an-email", "Ada", "Lovelace", Role.ADMIN);

        assertThatThrownBy(() -> useCase.create(command)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(userRepository, passwordEncoder);
        verifyNothingWasWritten();
    }

    @Test
    void rejects_a_blank_name_before_any_lookup() {
        CreateUserCommand command = new CreateUserCommand(ACTOR_ID, "staff@example.com", " ", "Lovelace", Role.ADMIN);

        assertThatThrownBy(() -> useCase.create(command))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("First name");

        verifyNoInteractions(userRepository, passwordEncoder);
        verifyNothingWasWritten();
    }
}
