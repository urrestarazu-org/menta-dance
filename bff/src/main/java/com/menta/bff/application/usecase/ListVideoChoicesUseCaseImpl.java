package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.ManagedVideo;
import com.menta.bff.application.dto.VideoListQuery;
import com.menta.bff.application.dto.VideoPage;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.VideoManagementException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link ListVideoChoicesUseCase}. A failed lookup is logged without token, identifiers or rows
 * and reads as unavailable choices.
 */
@Slf4j
@RequiredArgsConstructor
public class ListVideoChoicesUseCaseImpl implements ListVideoChoicesUseCase {

    /** The biggest page the API accepts (10 to 100). */
    static final int PAGE_SIZE = 100;

    private final VirtualVideoManagementApiClient videoApiClient;

    @Override
    public VideoChoices execute(String accessToken, String selectedVideoId) {
        String selectedId = selectedVideoId == null || selectedVideoId.isBlank() ? null : selectedVideoId.strip();
        try {
            VideoPage page = videoApiClient.listVideos(VideoSupport.token(accessToken),
                    new VideoListQuery(null, 0, PAGE_SIZE));
            List<VideoOption> options = new ArrayList<>();
            VideoOption selected = null;
            for (ManagedVideo video : page.items()) {
                boolean isSelected = video.videoId().equals(selectedId);
                if (!video.missingInLibrary() || isSelected) {
                    VideoOption option = VideoOption.of(video);
                    options.add(option);
                    selected = isSelected ? option : selected;
                }
            }
            if (selectedId != null && selected == null) {
                selected = VideoOption.notInList(selectedId);
                options.add(0, selected);
            }
            return new VideoChoices(List.copyOf(options), selected, page.totalElements(), page.totalPages() > 1,
                    false);
        } catch (VideoManagementException | ServiceUnavailableException e) {
            log.warn("Videos unavailable, rendering the lesson form without the list: {}", e.getMessage());
            return VideoChoices.unavailableChoices();
        }
    }
}
