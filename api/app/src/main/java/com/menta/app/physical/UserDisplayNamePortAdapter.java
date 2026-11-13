package com.menta.app.physical;

import com.menta.auth.application.port.in.ResolveUserDisplayNamesUseCase;
import com.menta.shared.auth.UserDisplayNamePort;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Implements the {@link UserDisplayNamePort} that Physical's session roster consumes (#355) by calling
 * {@code auth}'s application entry point, {@link ResolveUserDisplayNamesUseCase}, with a plain Java call inside
 * {@code api:app}: never HTTP, never {@code auth}'s infrastructure or database (ADR-0020). The whole roster is
 * resolved in one call, so the cost does not grow with the number of students.
 */
@Component
@RequiredArgsConstructor
public class UserDisplayNamePortAdapter implements UserDisplayNamePort {

    private final ResolveUserDisplayNamesUseCase resolveUserDisplayNames;

    @Override
    public Map<UUID, String> displayNamesOf(Collection<UUID> userIds) {
        return resolveUserDisplayNames.resolve(userIds);
    }
}
