package com.menta.virtual.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.usecase.VirtualCourseCatalogPortImpl;
import org.junit.jupiter.api.Test;

class VirtualConfigurationTest {

    @Test
    void wires_the_catalog_port_bean_with_the_given_repository() {
        VirtualCourseRepository repository = mock(VirtualCourseRepository.class);

        VirtualCourseCatalogPort port = new VirtualConfiguration().virtualCourseCatalogPort(repository);

        assertThat(port).isInstanceOf(VirtualCourseCatalogPortImpl.class);
    }
}
