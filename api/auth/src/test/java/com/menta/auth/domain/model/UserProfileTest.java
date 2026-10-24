package com.menta.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
    @NullAndEmptySource
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
    void rejects_a_birth_date_of_today() {
        LocalDate today = LocalDate.now();

        assertThatThrownBy(() -> new UserProfile("Ada", "Lovelace", PHONE, today))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Birth date");
    }

    @Test
    void rejects_a_future_birth_date() {
        LocalDate future = LocalDate.now().plusDays(1);

        assertThatThrownBy(() -> new UserProfile("Ada", "Lovelace", PHONE, future))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Birth date");
    }

    @Test
    void accepts_yesterday_as_birth_date() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        assertThat(new UserProfile("Ada", "Lovelace", PHONE, yesterday).birthDate()).isEqualTo(yesterday);
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
}
