package com.menta.virtual.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VideoLibraryQueryTest {

    @Test
    void keeps_a_valid_query_and_trims_the_search_text() {
        VideoLibraryQuery query = new VideoLibraryQuery(2, 20, "  tango  ", LibraryVideoOrder.TITLE);

        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(20);
        assertThat(query.search()).isEqualTo("tango");
        assertThat(query.order()).isEqualTo(LibraryVideoOrder.TITLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t\n"})
    void a_blank_search_means_no_filter(String blank) {
        assertThat(new VideoLibraryQuery(0, 10, blank, LibraryVideoOrder.DATE).search()).isNull();
    }

    @Test
    void a_null_search_and_a_null_order_fall_back_to_no_filter_and_date() {
        VideoLibraryQuery query = new VideoLibraryQuery(0, 10, null, null);

        assertThat(query.search()).isNull();
        assertThat(query.order()).isEqualTo(LibraryVideoOrder.DATE);
    }

    @Test
    void rejects_a_negative_page() {
        assertThatThrownBy(() -> new VideoLibraryQuery(-1, 10, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 9, 1001})
    void rejects_a_size_outside_the_range_the_library_accepts(int size) {
        assertThatThrownBy(() -> new VideoLibraryQuery(0, size, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accepts_the_library_size_bounds() {
        assertThat(new VideoLibraryQuery(0, VideoLibraryQuery.MIN_PAGE_SIZE, null, null).size()).isEqualTo(10);
        assertThat(new VideoLibraryQuery(0, VideoLibraryQuery.MAX_PAGE_SIZE, null, null).size()).isEqualTo(1000);
    }

    @Test
    void rejects_a_search_longer_than_the_maximum() {
        String tooLong = "a".repeat(VideoLibraryQuery.MAX_SEARCH_LENGTH + 1);

        assertThatThrownBy(() -> new VideoLibraryQuery(0, 10, tooLong, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(new VideoLibraryQuery(0, 10, "a".repeat(VideoLibraryQuery.MAX_SEARCH_LENGTH), null).search())
            .hasSize(VideoLibraryQuery.MAX_SEARCH_LENGTH);
    }

    @Test
    void parses_the_order_names_ignoring_case_and_rejects_the_unknown_ones() {
        assertThat(LibraryVideoOrder.parse("date")).isEqualTo(LibraryVideoOrder.DATE);
        assertThat(LibraryVideoOrder.parse("TITLE")).isEqualTo(LibraryVideoOrder.TITLE);
        assertThat(LibraryVideoOrder.parse(null)).isEqualTo(LibraryVideoOrder.DATE);
        assertThat(LibraryVideoOrder.parse(" ")).isEqualTo(LibraryVideoOrder.DATE);
        assertThatThrownBy(() -> LibraryVideoOrder.parse("size")).isInstanceOf(IllegalArgumentException.class);
        assertThat(LibraryVideoOrder.TITLE.wireValue()).isEqualTo("title");
    }

    @Test
    void a_page_computes_its_total_pages() {
        assertThat(new LibraryVideoPage(java.util.List.of(), 0, 20, 0).totalPages()).isZero();
        assertThat(new LibraryVideoPage(java.util.List.of(), 0, 20, 20).totalPages()).isEqualTo(1);
        assertThat(new LibraryVideoPage(java.util.List.of(), 0, 20, 21).totalPages()).isEqualTo(2);
    }
}
