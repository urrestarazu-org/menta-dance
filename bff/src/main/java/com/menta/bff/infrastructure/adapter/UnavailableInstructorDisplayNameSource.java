package com.menta.bff.infrastructure.adapter;

import com.menta.bff.application.port.out.InstructorDisplayNameSource;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Placeholder of the open seam in {@link InstructorDisplayNameSource} (#368): the access token holds no name
 * and the Auth API has no "current user" endpoint, so the instructor's display name cannot be known yet. It
 * is deleted when a real source exists behind the port.
 */
@Component
public class UnavailableInstructorDisplayNameSource implements InstructorDisplayNameSource {

    @Override
    public Optional<String> displayNameOf(String accessToken) {
        return Optional.empty();
    }
}
