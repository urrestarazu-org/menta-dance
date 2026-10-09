package com.menta.bff.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CourseModality")
class CourseModalityTest {

    @ParameterizedTest
    @ValueSource(strings = {"virtual", "VIRTUAL", " Virtual "})
    @DisplayName("parses the virtual filter ignoring case and surrounding whitespace")
    void parsesVirtualFilter(String value) {
        assertThat(CourseModality.fromFilter(value)).contains(CourseModality.VIRTUAL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"physical", "PHYSICAL"})
    @DisplayName("parses the physical filter ignoring case")
    void parsesPhysicalFilter(String value) {
        assertThat(CourseModality.fromFilter(value)).contains(CourseModality.PHYSICAL);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "hybrid", "all", "virtual,physical"})
    @DisplayName("yields no filter for null, blank or unknown values")
    void yieldsNoFilterForUnknownValues(String value) {
        Optional<CourseModality> result = CourseModality.fromFilter(value);

        assertThat(result).isEmpty();
    }
}
