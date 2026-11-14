package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.AccessTokenClaimsReader;
import com.menta.bff.application.port.out.AccessTokenClaimsReader.UnreadableTokenException;
import com.menta.bff.application.port.out.InstructorDisplayNameSource;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link ResolveOwnInstructorUseCase}. */
@RequiredArgsConstructor
public class ResolveOwnInstructorUseCaseImpl implements ResolveOwnInstructorUseCase {

    private final AccessTokenClaimsReader claimsReader;
    private final InstructorDisplayNameSource nameSource;

    @Override
    public Optional<OwnInstructor> execute(String accessToken) {
        String id;
        try {
            id = UuidFormat.normalize(claimsReader.subjectOf(accessToken));
        } catch (UnreadableTokenException e) {
            return Optional.empty();
        }
        if (id == null) {
            return Optional.empty();
        }
        return nameSource.displayNameOf(accessToken)
                .map(String::strip)
                .filter(name -> !name.isEmpty())
                .map(name -> new OwnInstructor(id, name));
    }
}
