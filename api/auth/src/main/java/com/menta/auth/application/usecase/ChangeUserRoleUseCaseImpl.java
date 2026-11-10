package com.menta.auth.application.usecase;

import com.menta.auth.application.contract.AuthOutboxEventTypes;
import com.menta.auth.application.dto.ChangeUserRoleCommand;
import com.menta.auth.application.dto.UserSummary;
import com.menta.auth.application.port.in.ChangeUserRoleUseCase;
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
import com.menta.auth.domain.repository.UserRepository;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link ChangeUserRoleUseCase}.
 *
 * <p>The role travels in the access token, so a role change must also close the sessions that carry the old one:
 * the token version is bumped (see {@link User#changeRole}) and published through the outbox so the access-token
 * filter rejects older tokens, and every refresh token is revoked. Nothing here is atomic on its own: the caller
 * MUST wrap this use case in a transaction, like every other write use case in this module.
 *
 * <p>The "at least one active administrator" rule is enforced under row locks. Counting administrators without
 * them would let two administrators demoting each other at the same time both see "another one remains" and leave
 * the system with none, so every active administrator is locked first, then the target, and the count is taken on
 * what was locked.
 */
@RequiredArgsConstructor
public class ChangeUserRoleUseCaseImpl implements ChangeUserRoleUseCase {

    private final UserAdminRepository userAdminRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OutboxAppender outboxAppender;
    private final UserAuditRepository userAuditRepository;
    private final Clock clock;

    @Override
    public UserSummary changeRole(ChangeUserRoleCommand command) {
        UserId targetId = UserId.of(command.targetUserId());

        // Lock order matters: every active admin first, then the target. Reading the target before the admins
        // would hand this transaction a stale view of it once the admin locks are finally granted.
        Set<UserId> lockedActiveAdmins = userAdminRepository.lockActiveAdminIds();
        User user = userAdminRepository.findByIdForUpdate(targetId)
            .orElseThrow(() -> new UserNotFoundException(targetId));

        Role previousRole = user.getRole();
        if (previousRole == command.newRole()) {
            return UserSummary.from(user);
        }
        if (isLastActiveAdmin(user, lockedActiveAdmins)) {
            throw new LastAdminCannotBeDemotedException();
        }

        user.changeRole(command.newRole());
        User saved = userRepository.save(user);

        refreshTokenRepository.revokeAllByUserId(saved.getId());
        // The bump is invisible to the access-token filter until published: without this event a token carrying
        // the old role would keep authorizing until it expires.
        outboxAppender.append(
            AuthOutboxEventTypes.USER_ROLE_CHANGED,
            saved.getId().getValue().toString(),
            "{\"userId\":\"" + saved.getId().getValue() + "\",\"newTokenVersion\":"
                + saved.getTokenVersion() + "}"
        );
        userAuditRepository.append(
            saved.getId(), command.actorId(), UserAuditRepository.ROLE_CHANGED,
            previousRole.name(), command.newRole().name(), clock.now()
        );
        return UserSummary.from(saved);
    }

    /** True when the user is an active administrator and no other active administrator is locked. */
    private static boolean isLastActiveAdmin(User user, Set<UserId> lockedActiveAdmins) {
        return user.getRole() == Role.ADMIN
            && user.isActive()
            && lockedActiveAdmins.stream().noneMatch(id -> !id.equals(user.getId()));
    }
}
