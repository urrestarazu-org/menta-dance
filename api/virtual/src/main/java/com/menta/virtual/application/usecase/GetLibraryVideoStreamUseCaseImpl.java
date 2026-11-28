package com.menta.virtual.application.usecase;

import com.menta.virtual.application.dto.LibraryVideo;
import com.menta.virtual.application.dto.LibraryVideoStreamView;
import com.menta.virtual.application.exception.VideoNotPlayableException;
import com.menta.virtual.application.exception.VideoNotPlayableException.Reason;
import com.menta.virtual.application.port.in.GetLibraryVideoStreamUseCase;
import com.menta.virtual.application.port.out.BunnyNetSignatureService;
import com.menta.virtual.application.port.out.Clock;
import com.menta.virtual.application.port.out.VideoLibraryPort;
import com.menta.virtual.domain.exception.VideoNotFoundException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;

/**
 * Default {@link GetLibraryVideoStreamUseCase} (ADR-0049, #864). A plain read, registered without a transactional
 * decorator and without an audit row: reads are not audited (rename and delete are).
 *
 * <p>Order: validate the id, ask the library for the video, check it plays now, then sign. Neither lessons nor
 * ownership take part: since #861 the whole library is visible to staff, so any video may be previewed, including
 * one no lesson uses yet. The URL is signed by the same {@link BunnyNetSignatureService} and for the same time to
 * live as the student stream ({@link GetPublicLessonStreamUseCaseImpl#SIGNED_URL_TTL}), "now" comes from the
 * module's {@link Clock} (ADR-0048), and the URL is never logged here: it carries the CDN token.</p>
 *
 * <p>Playable means the library finished the video and it has the MP4 copy the CDN serves (ADR-0049, decision 8);
 * the same rule the management screens use to say whether a video plays. Anything else is
 * {@link VideoNotPlayableException} with the reason, and nothing is signed.</p>
 */
@RequiredArgsConstructor
public class GetLibraryVideoStreamUseCaseImpl implements GetLibraryVideoStreamUseCase {

    private final VideoLibraryPort library;
    private final BunnyNetSignatureService signatureService;
    private final Clock clock;

    @Override
    public LibraryVideoStreamView get(String videoId) {
        VideoUsages.requireValidId(videoId);
        LibraryVideo video = library.find(videoId).orElseThrow(VideoNotFoundException::new);
        requirePlayable(video);
        Instant expiresAt = clock.now().plus(GetPublicLessonStreamUseCaseImpl.SIGNED_URL_TTL);
        String signedUrl = signatureService.generateSignedUrl(videoId, expiresAt.getEpochSecond());
        return new LibraryVideoStreamView(
            videoId, video.title(), signedUrl, GetPublicLessonStreamUseCaseImpl.STREAM_TYPE, expiresAt
        );
    }

    private static void requirePlayable(LibraryVideo video) {
        Reason reason = switch (video.status()) {
            case FINISHED -> video.hasMp4Fallback() ? null : Reason.NO_MP4_COPY;
            case CREATED -> Reason.WAITING_FOR_FILE;
            case UPLOADED, PROCESSING, TRANSCODING, JIT_SEGMENTING, JIT_PLAYLISTS_CREATED -> Reason.PROCESSING;
            case ERROR, UPLOAD_FAILED -> Reason.FAILED;
            case UNKNOWN -> Reason.UNKNOWN_STATE;
        };
        if (reason != null) {
            throw new VideoNotPlayableException(reason);
        }
    }
}
