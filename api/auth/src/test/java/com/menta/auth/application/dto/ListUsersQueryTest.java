package com.menta.auth.application.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.menta.auth.domain.model.Role;
import org.junit.jupiter.api.Test;

class ListUsersQueryTest {

    @Test
    void keeps_a_valid_query_as_given() {
        ListUsersQuery query = new ListUsersQuery(Role.INSTRUCTOR, "ada", 2, 50);

        assertThat(query.role()).isEqualTo(Role.INSTRUCTOR);
        assertThat(query.search()).isEqualTo("ada");
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(50);
    }

    @Test
    void trims_the_search_text() {
        assertThat(new ListUsersQuery(null, "  ada  ", 0, 20).search()).isEqualTo("ada");
    }

    @Test
    void a_null_empty_or_blank_search_means_no_filter() {
        assertThat(new ListUsersQuery(null, null, 0, 20).search()).isNull();
        assertThat(new ListUsersQuery(null, "", 0, 20).search()).isNull();
        assertThat(new ListUsersQuery(null, "   ", 0, 20).search()).isNull();
    }

    @Test
    void accepts_a_search_of_exactly_one_hundred_characters() {
        String search = "a".repeat(ListUsersQuery.MAX_SEARCH_LENGTH);

        assertThat(new ListUsersQuery(null, search, 0, 20).search()).isEqualTo(search);
    }

    @Test
    void rejects_a_search_longer_than_one_hundred_characters() {
        String search = "a".repeat(ListUsersQuery.MAX_SEARCH_LENGTH + 1);

        assertThatThrownBy(() -> new ListUsersQuery(null, search, 0, 20))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_negative_page() {
        assertThatThrownBy(() -> new ListUsersQuery(null, null, -1, 20))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_size_below_one() {
        assertThatThrownBy(() -> new ListUsersQuery(null, null, 0, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_a_size_above_the_maximum_instead_of_clamping_it() {
        assertThatThrownBy(() -> new ListUsersQuery(null, null, 0, ListUsersQuery.MAX_PAGE_SIZE + 1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
