package com.menta.virtual.infrastructure.config;

import com.menta.virtual.application.port.in.VirtualCourseCatalogPort;
import com.menta.virtual.application.port.out.VirtualCourseRepository;
import com.menta.virtual.application.usecase.VirtualCourseCatalogPortImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link VirtualCourseCatalogPort}. {@code VirtualCourseRepositoryAdapter}
 * is {@code @Component}-scanned; the use case is a plain Java class composed
 * here, mirroring {@code AuthConfiguration}/{@code BillingConfiguration}'s
 * rationale: no implicit {@code @Autowired} on use-case classes.
 */
@Configuration
public class VirtualConfiguration {

    @Bean
    public VirtualCourseCatalogPort virtualCourseCatalogPort(VirtualCourseRepository virtualCourseRepository) {
        return new VirtualCourseCatalogPortImpl(virtualCourseRepository);
    }
}
