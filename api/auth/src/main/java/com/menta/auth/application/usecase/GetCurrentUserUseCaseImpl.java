package com.menta.auth.application.usecase;

import com.menta.auth.application.dto.CurrentUser;
import com.menta.auth.application.port.in.GetCurrentUserUseCase;
import com.menta.auth.domain.exception.UserNotFoundException;
import com.menta.auth.domain.model.UserId;
import com.menta.auth.domain.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link GetCurrentUserUseCase}: a read-only query, so it needs no transaction of its own. */
@RequiredArgsConstructor
public class GetCurrentUserUseCaseImpl implements GetCurrentUserUseCase {

    private final UserRepository userRepository;

    @Override
    public CurrentUser get(UUID userId) {
        UserId id = UserId.of(userId);
        return userRepository.findById(id)
            .map(CurrentUser::from)
            .orElseThrow(() -> new UserNotFoundException(id));
    }
}
