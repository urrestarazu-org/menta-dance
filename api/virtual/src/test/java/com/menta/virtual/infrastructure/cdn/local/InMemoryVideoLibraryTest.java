package com.menta.virtual.infrastructure.cdn.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.virtual.application.dto.LibraryVideo;
import com.menta.virtual.application.dto.LibraryVideoOrder;
import com.menta.virtual.application.dto.LibraryVideoPage;
import com.menta.virtual.application.dto.LibraryVideoStatus;
import com.menta.virtual.application.dto.VideoLibraryQuery;
import com.menta.virtual.domain.exception.VideoLibraryUnavailableException;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryVideoLibraryTest {

    private final InMemoryVideoLibrary library = new InMemoryVideoLibrary();

    private static LibraryVideo video(String id, String title) {
        return new LibraryVideo(id, title, LibraryVideoStatus.FINISHED, 30, null, true, List.of("720p"));
    }

    private static VideoLibraryQuery query(int page, int size, String search, LibraryVideoOrder order) {
        return new VideoLibraryQuery(page, size, search, order);
    }

    @Test
    void starts_empty() {
        LibraryVideoPage page = library.list(query(0, 10, null, null));

        assertThat(page.items()).isEmpty();
        assertThat(page.totalItems()).isZero();
        assertThat(library.find("x")).isEmpty();
    }

    @Test
    void lists_the_newest_first_by_default_and_by_title_on_request() {
        library.add(video("a", "Charleston"));
        library.add(video("b", "alegría"));
        library.add(video("c", "Bachata"));

        assertThat(library.list(query(0, 10, null, LibraryVideoOrder.DATE)).items())
            .extracting(LibraryVideo::videoId).containsExactly("c", "b", "a");
        assertThat(library.list(query(0, 10, null, LibraryVideoOrder.TITLE)).items())
            .extracting(LibraryVideo::videoId).containsExactly("b", "c", "a");
    }

    @Test
    void the_search_matches_the_title_as_a_case_insensitive_substring_and_totals_count_the_matches() {
        library.add(video("a", "Tango Básico"));
        library.add(video("b", "Bachata"));
        library.add(video("c", "tango avanzado"));

        LibraryVideoPage page = library.list(query(0, 10, "TANGO", LibraryVideoOrder.TITLE));

        assertThat(page.items()).extracting(LibraryVideo::videoId).containsExactly("c", "a");
        assertThat(page.totalItems()).isEqualTo(2);
    }

    @Test
    void pages_the_matches_and_a_page_past_the_end_is_empty_with_the_right_total() {
        for (int i = 0; i < 25; i++) {
            library.add(video("v" + i, String.format("T%02d", i)));
        }

        assertThat(library.list(query(0, 10, null, LibraryVideoOrder.TITLE)).items()).hasSize(10);
        assertThat(library.list(query(2, 10, null, LibraryVideoOrder.TITLE)).items()).hasSize(5);
        LibraryVideoPage beyond = library.list(query(9, 10, null, LibraryVideoOrder.TITLE));
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.totalItems()).isEqualTo(25);
    }

    @Test
    void adding_a_video_with_an_existing_id_replaces_it_and_find_returns_it() {
        library.add(video("a", "Viejo"));
        library.add(video("a", "Nuevo"));

        assertThat(library.find("a")).map(LibraryVideo::title).contains("Nuevo");
        assertThat(library.list(query(0, 10, null, null)).totalItems()).isEqualTo(1);
    }

    @Test
    void remove_and_clear_empty_the_library() {
        library.add(video("a", "A"));
        library.add(video("b", "B"));

        library.remove("a");
        assertThat(library.find("a")).isEmpty();
        library.clear();
        assertThat(library.list(query(0, 10, null, null)).items()).isEmpty();
    }

    @Test
    void a_simulated_outage_makes_every_call_fail_until_it_is_lifted() {
        library.add(video("a", "A"));
        library.simulateOutage(true);

        assertThatThrownBy(() -> library.list(query(0, 10, null, null)))
            .isInstanceOf(VideoLibraryUnavailableException.class);
        assertThatThrownBy(() -> library.find("a")).isInstanceOf(VideoLibraryUnavailableException.class);
        assertThatThrownBy(() -> library.rename("a", "B")).isInstanceOf(VideoLibraryUnavailableException.class);
        assertThatThrownBy(() -> library.delete("a")).isInstanceOf(VideoLibraryUnavailableException.class);

        library.simulateOutage(false);
        assertThat(library.find("a")).isPresent();
    }

    @Test
    void rename_changes_only_the_title_and_keeps_the_position() {
        library.add(video("a", "A"));
        library.add(video("b", "B"));

        assertThat(library.rename("a", "Nuevo")).isTrue();

        assertThat(library.find("a")).contains(video("a", "Nuevo"));
        assertThat(library.list(query(0, 10, null, null)).items()).extracting(LibraryVideo::videoId)
            .containsExactly("b", "a");
    }

    @Test
    void rename_of_an_unknown_video_answers_false() {
        assertThat(library.rename("nope", "Nuevo")).isFalse();
        assertThat(library.find("nope")).isEmpty();
    }

    @Test
    void delete_removes_the_video_and_deleting_twice_is_not_an_error() {
        library.add(video("a", "A"));

        library.delete("a");
        library.delete("a");

        assertThat(library.find("a")).isEmpty();
    }
}
