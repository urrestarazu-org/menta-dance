package com.menta.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UserProfileTest {

    private static final String PHONE = "+5491100000000";

    @Test
    void keeps_the_given_values() {
        LocalDate birthDate = LocalDate.of(1990, 5, 17);

        UserProfile profile = new UserProfile("Ada", "Lovelace", PHONE, birthDate);

        assertThat(profile.firstName()).isEqualTo("Ada");
        assertThat(profile.lastName()).isEqualTo("Lovelace");
        assertThat(profile.phone()).isEqualTo(PHONE);
        assertThat(profile.birthDate()).isEqualTo(birthDate);
    }

    @Test
    void trims_the_names() {
        UserProfile profile = new UserProfile("  Ada ", "\tLovelace  ", PHONE, null);

        assertThat(profile.firstName()).isEqualTo("Ada");
        assertThat(profile.lastName()).isEqualTo("Lovelace");
    }

    @Test
    void accepts_a_missing_birth_date() {
        UserProfile profile = new UserProfile("Ada", "Lovelace", PHONE, null);

        assertThat(profile.birthDate()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   \t"})
    void rejects_a_blank_first_name(String firstName) {
        assertThatThrownBy(() -> new UserProfile(firstName, "Lovelace", PHONE, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("First name");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   \t"})
    void rejects_a_blank_last_name(String lastName) {
        assertThatThrownBy(() -> new UserProfile("Ada", lastName, PHONE, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Last name");
    }

    @Test
    void accepts_names_of_exactly_the_maximum_length() {
        String name = "a".repeat(UserProfile.NAME_MAX_LENGTH);

        UserProfile profile = new UserProfile(name, name, PHONE, null);

        assertThat(profile.firstName()).hasSize(100);
        assertThat(profile.lastName()).hasSize(100);
    }

    @Test
    void rejects_a_first_name_over_the_maximum_length() {
        String tooLong = "a".repeat(UserProfile.NAME_MAX_LENGTH + 1);

        assertThatThrownBy(() -> new UserProfile(tooLong, "Lovelace", PHONE, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("First name");
    }

    @Test
    void rejects_a_last_name_over_the_maximum_length() {
        String tooLong = "a".repeat(UserProfile.NAME_MAX_LENGTH + 1);

        assertThatThrownBy(() -> new UserProfile("Ada", tooLong, PHONE, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Last name");
    }

    @ParameterizedTest
    @ValueSource(strings = {"+12345678", "+5491100000000", "+123456789012345"})
    void accepts_phones_in_e164_format(String phone) {
        assertThat(new UserProfile("Ada", "Lovelace", phone, null).phone()).isEqualTo(phone);
    }

    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {
        "5491100000000",
        "+54 9 11 0000 0000",
        "+54-911-0000-0000",
        "+(54)91100000000",
        "+54abc1100000",
        "+1234567",
        "+1234567890123456",
        "+0123456789",
        "++5491100000000",
        " +5491100000000",
        "+5491100000000 ",
        "+5491100000000\n"
    })
    void rejects_phones_that_are_not_e164(String phone) {
        assertThatThrownBy(() -> new UserProfile("Ada", "Lovelace", phone, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Phone");
    }

    @Test
    void the_canonical_constructor_does_not_compare_the_birth_date_with_the_current_day() {
        // It also hydrates persisted data, which must never be re-checked against today's date.
        LocalDate farFuture = LocalDate.of(2999, 12, 31);

        assertThat(new UserProfile("Ada", "Lovelace", PHONE, farFuture).birthDate()).isEqualTo(farFuture);
    }

    @Nested
    class Of {

        private static final Instant NOW = Instant.parse("2030-06-15T12:00:00Z");

        @Test
        void keeps_the_given_values_and_trims_the_names() {
            LocalDate birthDate = LocalDate.of(1990, 5, 17);

            UserProfile profile = UserProfile.of("  Ada ", "\tLovelace  ", PHONE, birthDate, NOW);

            assertThat(profile).isEqualTo(new UserProfile("Ada", "Lovelace", PHONE, birthDate));
        }

        @Test
        void accepts_a_missing_birth_date() {
            assertThat(UserProfile.of("Ada", "Lovelace", PHONE, null, NOW).birthDate()).isNull();
        }

        @Test
        void rejects_a_birth_date_of_today_in_utc() {
            LocalDate today = LocalDate.of(2030, 6, 15);

            assertThatThrownBy(() -> UserProfile.of("Ada", "Lovelace", PHONE, today, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Birth date must be in the past");
        }

        @Test
        void rejects_a_future_birth_date() {
            LocalDate tomorrow = LocalDate.of(2030, 6, 16);

            assertThatThrownBy(() -> UserProfile.of("Ada", "Lovelace", PHONE, tomorrow, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Birth date must be in the past");
        }

        @Test
        void accepts_yesterday_as_birth_date() {
            LocalDate yesterday = LocalDate.of(2030, 6, 14);

            assertThat(UserProfile.of("Ada", "Lovelace", PHONE, yesterday, NOW).birthDate())
                .isEqualTo(yesterday);
        }

        @Test
        void early_in_the_utc_day_a_zone_behind_utc_still_sees_the_previous_day_but_utc_decides() {
            // 2030-01-01T00:30Z is still 2029-12-31 in Buenos Aires (UTC-3). A check against a JVM-zone
            // "today" there would reject 2029-12-31; the UTC day (2030-01-01) accepts it.
            Instant earlyUtc = Instant.parse("2030-01-01T00:30:00Z");

            assertThatThrownBy(() ->
                UserProfile.of("Ada", "Lovelace", PHONE, LocalDate.of(2030, 1, 1), earlyUtc))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Birth date must be in the past");
            assertThat(UserProfile.of("Ada", "Lovelace", PHONE, LocalDate.of(2029, 12, 31), earlyUtc).birthDate())
                .isEqualTo(LocalDate.of(2029, 12, 31));
        }

        @Test
        void late_in_the_utc_day_a_zone_ahead_of_utc_sees_the_next_day_but_utc_decides() {
            // 2029-12-31T23:30Z is already 2030-01-01 in Tokyo (UTC+9): UTC still says it is 2029-12-31.
            Instant lateUtc = Instant.parse("2029-12-31T23:30:00Z");

            assertThatThrownBy(() ->
                UserProfile.of("Ada", "Lovelace", PHONE, LocalDate.of(2029, 12, 31), lateUtc))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Birth date must be in the past");
            assertThat(UserProfile.of("Ada", "Lovelace", PHONE, LocalDate.of(2029, 12, 30), lateUtc).birthDate())
                .isEqualTo(LocalDate.of(2029, 12, 30));
        }

        @Test
        void keeps_the_name_and_phone_validation_of_the_canonical_constructor() {
            assertThatThrownBy(() -> UserProfile.of(" ", "Lovelace", PHONE, null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("First name");
            assertThatThrownBy(() -> UserProfile.of("Ada", "Lovelace", "5491100000000", null, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Phone");
        }
    }

    @Test
    void to_string_does_not_leak_personal_data() {
        UserProfile profile = new UserProfile("Ada", "Lovelace", PHONE, LocalDate.of(1990, 5, 17));

        assertThat(profile.toString())
            .doesNotContain("Ada")
            .doesNotContain("Lovelace")
            .doesNotContain(PHONE)
            .doesNotContain("1990");
    }

    @Test
    void the_phone_is_optional_for_accounts_created_by_an_administrator() {
        UserProfile profile = new UserProfile("Ada", "Lovelace", null, null);

        assertThat(profile.phone()).isNull();
        assertThat(profile.toString()).doesNotContain("Ada");
    }
}
