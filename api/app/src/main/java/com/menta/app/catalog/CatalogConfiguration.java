package com.menta.app.catalog;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring for the public catalog composition (#107). */
@Configuration
public class CatalogConfiguration {

    /**
     * The {@link Clock} the catalog composition reads {@code now} from, injected
     * by type. Deliberately not named {@code clock}: {@code VirtualConfiguration}
     * already owns that bean name and Spring Boot forbids overriding it.
     */
    @Bean
    public Clock catalogClock() {
        return Clock.systemUTC();
    }
}
