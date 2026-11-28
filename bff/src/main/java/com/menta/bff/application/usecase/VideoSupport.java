package com.menta.bff.application.usecase;

import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.ServiceUnavailableException;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.VideoManagementException;
import com.menta.bff.application.port.out.VirtualVideoManagementApiClient.VideoManagementException.Code;
import java.util.regex.Pattern;

/** What the video management use cases share: the checks of what can never be sent to the API. */
final class VideoSupport {

    /** The longest title the API accepts. */
    static final int MAX_TITLE_LENGTH = 255;

    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    private VideoSupport() {
    }

    /** A call without an access token cannot succeed: it reads as unavailable, without calling the API. */
    static String token(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ServiceUnavailableException("No access token for the Virtual API");
        }
        return accessToken;
    }

    /** Only a simple identifier (letters, digits, {@code -} and {@code _}) reaches a path; else it is no video. */
    static String videoId(String videoId) {
        if (videoId == null || !VIDEO_ID.matcher(videoId).matches()) {
            throw new VideoManagementException(Code.VIDEO_NOT_FOUND);
        }
        return videoId;
    }

    /** The title without its ends; blank or too long is the invalid request the API would answer. */
    static String title(String title) {
        String stripped = title == null ? "" : title.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_TITLE_LENGTH) {
            throw new VideoManagementException(Code.INVALID_REQUEST);
        }
        return stripped;
    }
}
