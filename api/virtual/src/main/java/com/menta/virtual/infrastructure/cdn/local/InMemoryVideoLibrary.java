package com.menta.virtual.infrastructure.cdn.local;

import com.menta.virtual.application.dto.LibraryVideo;
import com.menta.virtual.application.dto.LibraryVideoOrder;
import com.menta.virtual.application.dto.LibraryVideoPage;
import com.menta.virtual.application.dto.VideoLibraryQuery;
import com.menta.virtual.application.port.out.VideoLibraryPort;
import com.menta.virtual.domain.exception.VideoLibraryUnavailableException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Credential-free, network-free {@link VideoLibraryPort} for tests and for the {@code e2e-bunny-net}, {@code demo}
 * (without an API key), {@code test} and {@code integration-test} profiles (ADR-0049, selected by
 * {@link com.menta.virtual.infrastructure.config.VirtualConfiguration} like the local signer). It starts empty;
 * tests and fixtures add the videos they need. It mimics the library's documented behaviour: {@code search} is a
 * case-insensitive substring of the title, {@code DATE} is newest first (the last added) and {@code TITLE} is A-Z.
 * It is deliberately NOT a model of anything undocumented (see the assumptions listed in ADR-0049's follow-up).
 */
public final class InMemoryVideoLibrary implements VideoLibraryPort {

    private final Map<String, LibraryVideo> videos = new LinkedHashMap<>();
    private boolean outage;

    /** Adds a video, or replaces the one with the same id, making it the newest. */
    public synchronized void add(LibraryVideo video) {
        videos.remove(video.videoId());
        videos.put(video.videoId(), video);
    }

    /** Removes a video if present. */
    public synchronized void remove(String videoId) {
        videos.remove(videoId);
    }

    /** Removes every video and lifts a simulated outage. */
    public synchronized void clear() {
        videos.clear();
        outage = false;
    }

    /** While {@code true}, every call fails like an unreachable library. */
    public synchronized void simulateOutage(boolean value) {
        outage = value;
    }

    @Override
    public synchronized LibraryVideoPage list(VideoLibraryQuery query) {
        failIfOutage();
        List<LibraryVideo> matches = new ArrayList<>(videos.values());
        if (query.search() != null) {
            String needle = query.search().toLowerCase(Locale.ROOT);
            matches.removeIf(video -> !video.title().toLowerCase(Locale.ROOT).contains(needle));
        }
        if (query.order() == LibraryVideoOrder.TITLE) {
            Collator collator = Collator.getInstance(Locale.forLanguageTag("es"));
            matches.sort((left, right) -> collator.compare(left.title(), right.title()));
        } else {
            java.util.Collections.reverse(matches);
        }
        long from = (long) query.page() * query.size();
        int start = (int) Math.min(from, matches.size());
        int end = (int) Math.min(from + query.size(), matches.size());
        return new LibraryVideoPage(matches.subList(start, end), query.page(), query.size(), matches.size());
    }

    @Override
    public synchronized Optional<LibraryVideo> find(String videoId) {
        failIfOutage();
        return Optional.ofNullable(videos.get(videoId));
    }

    private void failIfOutage() {
        if (outage) {
            throw new VideoLibraryUnavailableException("The in-memory video library is simulating an outage");
        }
    }
}
