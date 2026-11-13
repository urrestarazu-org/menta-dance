package com.menta.auth.application.usecase;

import com.menta.auth.application.dto.UserSummary;
import com.menta.auth.application.port.in.ResolveUserDisplayNamesUseCase;
import com.menta.auth.application.port.out.UserAdminRepository;
import com.menta.auth.domain.model.UserId;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link ResolveUserDisplayNamesUseCase}: a read-only query that asks the repository once for
 * every distinct id, so its cost does not grow with the number of users. It needs no transaction of its own.
 */
@RequiredArgsConstructor
public class ResolveUserDisplayNamesUseCaseImpl implements ResolveUserDisplayNamesUseCase {

    private final UserAdminRepository userAdminRepository;

    @Override
    public Map<UUID, String> resolve(Collection<UUID> userIds) {
        Objects.requireNonNull(userIds, "userIds");
        Set<UserId> ids = userIds.stream()
            .filter(Objects::nonNull)
            .map(UserId::of)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userAdminRepository.findSummariesByIds(ids).stream()
            .collect(Collectors.toUnmodifiableMap(
                summary -> UUID.fromString(summary.id()), ResolveUserDisplayNamesUseCaseImpl::displayName
            ));
    }

    private static String displayName(UserSummary summary) {
        if (summary.firstName() == null || summary.lastName() == null) {
            return summary.email();
        }
        return summary.firstName() + " " + summary.lastName();
    }
}
