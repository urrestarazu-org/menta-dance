package com.menta.bff.application.usecase;

import com.menta.bff.application.dto.VideoListQuery;
import com.menta.bff.application.dto.VideoPage;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.VideoManagementException;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.VideoManagementException.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link ListManagedVideosUseCase}. Which videos the caller sees, their order and the lessons of
 * each are the API's; this class only normalizes what comes from the query string and words the answer. A failed
 * lookup is logged without token, search or rows and reads as an unavailable view.
 */
@Slf4j
@RequiredArgsConstructor
public class ListManagedVideosUseCaseImpl implements ListManagedVideosUseCase {

    /** Fixed page size: the user never chooses it, and it is inside the API's range of 10 to 100. */
    static final int PAGE_SIZE = 20;

    /** The API rejects a longer search text, so it is cut here. */
    static final int MAX_SEARCH_LENGTH = 100;

    private final VirtualVideoManagementApiClient videoApiClient;

    @Override
    public VideosListView execute(String accessToken, String search, Integer page) {
        String text = normalize(search);
        try {
            String token = VideoSupport.token(accessToken);
            int pageIndex = page == null ? 0 : Math.max(page, 0);
            VideoPage result = videoApiClient.listVideos(token, new VideoListQuery(text, pageIndex, PAGE_SIZE));
            if (result.items().isEmpty() && result.totalPages() > 0 && pageIndex >= result.totalPages()) {
                result = videoApiClient.listVideos(token,
                        new VideoListQuery(text, result.totalPages() - 1, PAGE_SIZE));
            }
            return new VideosListView(text, result.items().stream().map(VideoRow::of).toList(), result.page(),
                    result.totalPages(), result.totalElements(), false, false);
        } catch (VideoManagementException e) {
            if (e.getCode() == Code.ACCESS_DENIED) {
                throw e;
            }
            log.warn("Videos unavailable, rendering the screen without them: {}", e.getMessage());
            return VideosListView.unavailableView(text, e.getCode() == Code.VIDEO_LIBRARY_UNAVAILABLE);
        } catch (ServiceUnavailableException e) {
            log.warn("Videos unavailable, rendering the screen without them: {}", e.getMessage());
            return VideosListView.unavailableView(text, false);
        }
    }

    /** Trims, treats blank as no filter and cuts to the API's limit without splitting a surrogate pair. */
    private static String normalize(String search) {
        if (search == null) {
            return null;
        }
        String trimmed = search.strip();
        if (trimmed.length() > MAX_SEARCH_LENGTH) {
            int end = Character.isHighSurrogate(trimmed.charAt(MAX_SEARCH_LENGTH - 1))
                    ? MAX_SEARCH_LENGTH - 1 : MAX_SEARCH_LENGTH;
            trimmed = trimmed.substring(0, end).strip();
        }
        return trimmed.isEmpty() ? null : trimmed;
    }
}
