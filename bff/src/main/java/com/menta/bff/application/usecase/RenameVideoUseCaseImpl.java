package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.VirtualVideoManagementApiClient;
import lombok.RequiredArgsConstructor;

/** Implementation of {@link RenameVideoUseCase}. */
@RequiredArgsConstructor
public class RenameVideoUseCaseImpl implements RenameVideoUseCase {

    private final VirtualVideoManagementApiClient videoApiClient;

    @Override
    public void execute(String accessToken, String videoId, String title) {
        String token = VideoSupport.token(accessToken);
        String id = VideoSupport.videoId(videoId);
        videoApiClient.renameVideo(token, id, VideoSupport.title(title));
    }
}
