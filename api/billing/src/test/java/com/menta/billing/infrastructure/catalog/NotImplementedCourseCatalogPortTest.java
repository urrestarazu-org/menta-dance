package com.menta.billing.infrastructure.catalog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class NotImplementedCourseCatalogPortTest {

    @Test
    void throws_until_40_46_replace_it() {
        NotImplementedCourseCatalogPort port = new NotImplementedCourseCatalogPort();

        assertThatThrownBy(() -> port.courseNames(List.of("course-1")))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
