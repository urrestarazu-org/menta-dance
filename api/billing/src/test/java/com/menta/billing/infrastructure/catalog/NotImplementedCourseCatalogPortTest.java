package com.menta.billing.infrastructure.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NotImplementedCourseCatalogPortTest {

    @Test
    void throws_until_40_46_replace_it() {
        NotImplementedCourseCatalogPort port = new NotImplementedCourseCatalogPort();

        assertThatThrownBy(() -> port.courseName("course-1"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
