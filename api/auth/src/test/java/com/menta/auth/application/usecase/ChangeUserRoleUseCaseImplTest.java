package com.menta.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.dto.ChangeUserRoleCommand;
import com.menta.auth.application.dto.UserSummary;
import com.menta.auth.application.port.out.Clock;
import com.menta.auth.application.port.out.OutboxAppender;
import com.menta.auth.application.port.out.RefreshTokenRepository;
import com.menta.auth.application.port.out.UserAdminRepository;
import com.menta.auth.application.port.out.UserAuditRepository;
import com.menta.auth.domain.exception.LastAdminCannotBeDemotedException;
import com.menta.auth.domain.exception.UserNotFoundException;
import com.menta.auth.domain.model.Role;
import com.menta.auth.domain.model.User;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.model.UserStatus;
import com.menta.auth.domain.repository.UserRepository;
import com.menta.shared.domain.vo.Email;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChangeUserRoleUseCaseImplTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-10T12:00:00Z");
    private static final UUID ACTOR_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    @Mock private UserAdminRepository userAdminRepository;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private OutboxAppender outboxAppender;
    @Mock private UserAuditRepository userAuditRepository;
    @Mock private Clock clock;

    private ChangeUserRoleUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new ChangeUserRoleUseCaseImpl(
            userAdminRepository, userRepository, refreshTokenRepository, outboxAppender, userAuditRepository, clock
        );
    }

    private static User user(Role role, UserStatus status, long tokenVersion) {
        LocalDateTime created = LocalDateTime.of(2026, 1, 1, 0, 0);
        return User.rehydrate(
            UserId.generate(), Email.of(UUID.randomUUID() + "@example.com"), "hash", role, status,
            created, created, tokenVersion
        );
    }

    private void found(User user) {
        when(userAdminRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
    }

    private void persistsWhatItIsGiven() {
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void withActiveAdmins(UserId... ids) {
        when(userAdminRepository.lockActiveAdminIds()).thenReturn(Set.of(ids));
    }

    private ChangeUserRoleCommand command(User target, Role newRole) {
        return new ChangeUserRoleCommand(ACTOR_ID, target.getId().getValue(), newRole);
    }

    @Test
    void changes_the_role_and_bumps_the_token_version() {
        User target = user(Role.STUDENT, UserStatus.ACTIVE, 4L);
        withActiveAdmins();
        found(target);
        persistsWhatItIsGiven();
        when(clock.now()).thenReturn(FIXED_NOW);

        UserSummary result = useCase.changeRole(command(target, Role.INSTRUCTOR));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.INSTRUCTOR);
        assertThat(saved.getValue().getTokenVersion()).isEqualTo(5L);
        assertThat(result.role()).isEqualTo(Role.INSTRUCTOR);
        assertThat(result.id()).isEqualTo(target.getId().getValue().toString());
    }

    @Test
    void locks_the_active_admins_before_reading_the_target_and_writes_in_order() {
        User target = user(Role.STUDENT, UserStatus.ACTIVE, 1L);
        withActiveAdmins();
        found(target);
        persistsWhatItIsGiven();
        when(clock.now()).thenReturn(FIXED_NOW);

        useCase.changeRole(command(target, Role.RECEPTIONIST));

        InOrder order = inOrder(
            userAdminRepository, userRepository, refreshTokenRepository, outboxAppender, userAuditRepository
        );
        order.verify(userAdminRepository).lockActiveAdminIds();
        order.verify(userAdminRepository).findByIdForUpdate(target.getId());
        order.verify(userRepository).save(any(User.class));
        order.verify(refreshTokenRepository).revokeAllByUserId(target.getId());
        order.verify(outboxAppender).append(anyString(), anyString(), anyString());
        order.verify(userAuditRepository).append(any(), any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void revokes_every_refresh_token_of_the_user() {
        User target = user(Role.STUDENT, UserStatus.ACTIVE, 1L);
        withActiveAdmins();
        found(target);
        persistsWhatItIsGiven();
        when(clock.now()).thenReturn(FIXED_NOW);

        useCase.changeRole(command(target, Role.INSTRUCTOR));

        verify(refreshTokenRepository).revokeAllByUserId(target.getId());
    }

    @Test
    void publishes_a_user_role_changed_event_with_valid_json_carrying_the_new_token_version() throws Exception {
        User target = user(Role.STUDENT, UserStatus.ACTIVE, 4L);
        withActiveAdmins();
        found(target);
        persistsWhatItIsGiven();
        when(clock.now()).thenReturn(FIXED_NOW);

        useCase.changeRole(command(target, Role.INSTRUCTOR));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(outboxAppender).append(
            org.mockito.ArgumentMatchers.eq(AuthOutboxEventTypes.USER_ROLE_CHANGED),
            org.mockito.ArgumentMatchers.eq(target.getId().getValue().toString()),
            payload.capture()
        );
        JsonNode json = new ObjectMapper().readTree(payload.getValue());
        assertThat(json.get("userId").asText()).isEqualTo(target.getId().getValue().toString());
        assertThat(json.get("newTokenVersion").asLong()).isEqualTo(5L);
    }

    @Test
    void audits_who_changed_what_and_when() {
        User target = user(Role.STUDENT, UserStatus.ACTIVE, 1L);
        withActiveAdmins();
        found(target);
        persistsWhatItIsGiven();
        when(clock.now()).thenReturn(FIXED_NOW);

        useCase.changeRole(command(target, Role.INSTRUCTOR));

        verify(userAuditRepository).append(
            target.getId(), ACTOR_ID, "ROLE_CHANGED", "STUDENT", "INSTRUCTOR", FIXED_NOW
        );
    }

    @Test
    void assigning_the_current_role_is_an_idempotent_no_op() {
        User target = user(Role.INSTRUCTOR, UserStatus.ACTIVE, 4L);
        withActiveAdmins();
        found(target);

        UserSummary result = useCase.changeRole(command(target, Role.INSTRUCTOR));

        assertThat(result.role()).isEqualTo(Role.INSTRUCTOR);
        assertThat(target.getTokenVersion()).isEqualTo(4L);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(refreshTokenRepository, outboxAppender, userAuditRepository);
    }

    @Test
    void the_only_active_admin_keeping_the_admin_role_is_a_no_op_not_a_rejection() {
        User onlyAdmin = user(Role.ADMIN, UserStatus.ACTIVE, 2L);
        withActiveAdmins(onlyAdmin.getId());
        found(onlyAdmin);

        UserSummary result = useCase.changeRole(command(onlyAdmin, Role.ADMIN));

        assertThat(result.role()).isEqualTo(Role.ADMIN);
        verify(userRepository, never()).save(any());
    }

    @Test
    void an_unknown_user_is_reported_and_nothing_is_written() {
        UUID unknown = UUID.randomUUID();
        withActiveAdmins();
        when(userAdminRepository.findByIdForUpdate(UserId.of(unknown))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.changeRole(new ChangeUserRoleCommand(ACTOR_ID, unknown, Role.STUDENT)))
            .isInstanceOf(UserNotFoundException.class)
            .extracting("errorCode").isEqualTo("USER_NOT_FOUND");

        verify(userRepository, never()).save(any());
        verifyNoInteractions(refreshTokenRepository, outboxAppender, userAuditRepository);
    }

    @Test
    void the_last_active_admin_cannot_be_demoted() {
        User onlyAdmin = user(Role.ADMIN, UserStatus.ACTIVE, 2L);
        withActiveAdmins(onlyAdmin.getId());
        found(onlyAdmin);

        assertThatThrownBy(() -> useCase.changeRole(command(onlyAdmin, Role.STUDENT)))
            .isInstanceOf(LastAdminCannotBeDemotedException.class)
            .extracting("errorCode").isEqualTo("LAST_ADMIN_CANNOT_BE_DEMOTED");

        assertThat(onlyAdmin.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(refreshTokenRepository, outboxAppender, userAuditRepository);
    }

    @Test
    void an_admin_can_demote_themselves_while_another_active_admin_remains() {
        User self = user(Role.ADMIN, UserStatus.ACTIVE, 2L);
        User other = user(Role.ADMIN, UserStatus.ACTIVE, 2L);
        withActiveAdmins(self.getId(), other.getId());
        found(self);
        persistsWhatItIsGiven();
        when(clock.now()).thenReturn(FIXED_NOW);

        UserSummary result = useCase.changeRole(
            new ChangeUserRoleCommand(self.getId().getValue(), self.getId().getValue(), Role.INSTRUCTOR)
        );

        assertThat(result.role()).isEqualTo(Role.INSTRUCTOR);
    }

    @Test
    void an_admin_can_demote_another_admin_while_an_active_admin_remains() {
        User actor = user(Role.ADMIN, UserStatus.ACTIVE, 2L);
        User target = user(Role.ADMIN, UserStatus.ACTIVE, 2L);
        withActiveAdmins(actor.getId(), target.getId());
        found(target);
        persistsWhatItIsGiven();
        when(clock.now()).thenReturn(FIXED_NOW);

        UserSummary result = useCase.changeRole(command(target, Role.STUDENT));

        assertThat(result.role()).isEqualTo(Role.STUDENT);
    }

    @Test
    void an_inactive_admin_can_be_demoted_even_when_no_active_admin_exists() {
        // Only ACTIVE admins count toward the invariant: demoting an inactive one cannot leave the system
        // with fewer active admins than it already had.
        User inactiveAdmin = user(Role.ADMIN, UserStatus.INACTIVE, 2L);
        withActiveAdmins();
        found(inactiveAdmin);
        persistsWhatItIsGiven();
        when(clock.now()).thenReturn(FIXED_NOW);

        UserSummary result = useCase.changeRole(command(inactiveAdmin, Role.STUDENT));

        assertThat(result.role()).isEqualTo(Role.STUDENT);
        assertThat(result.status()).isEqualTo(UserStatus.INACTIVE);
    }

    @Test
    void a_non_admin_can_be_promoted_to_admin() {
        User student = user(Role.STUDENT, UserStatus.PENDING_ACTIVATION, 1L);
        withActiveAdmins();
        found(student);
        persistsWhatItIsGiven();
        when(clock.now()).thenReturn(FIXED_NOW);

        UserSummary result = useCase.changeRole(command(student, Role.ADMIN));

        assertThat(result.role()).isEqualTo(Role.ADMIN);
        verify(userAuditRepository).append(student.getId(), ACTOR_ID, "ROLE_CHANGED", "STUDENT", "ADMIN", FIXED_NOW);
    }
}
