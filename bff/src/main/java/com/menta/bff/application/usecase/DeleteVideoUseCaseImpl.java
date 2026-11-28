package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.VirtualVideoManagementApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link DeleteVideoUseCase}. */
@RequiredArgsConstructor
public class DeleteVideoUseCaseImpl implements DeleteVideoUseCase {

    private final VirtualVideoManagementApiClient videoApiClient;

    @Override
    public void execute(String accessToken, String videoId) {
        String token = VideoSupport.token(accessToken);
        videoApiClient.deleteVideo(token, VideoSupport.videoId(videoId));
    }
}
