package com.menta.app.demo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ApplicationDemoYmlTest {

    private static final String[] ROLES = {"admin", "instructor", "student", "receptionist"};

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void demo_profile_provides_a_default_password_for_every_role() {
        runner.withPropertyValues("spring.profiles.active=demo").run(context -> {
            for (String role : ROLES) {
                assertThat(context.getEnvironment().getProperty("demo.users." + role + "-password"))
                    .as(role).isNotBlank();
            }
        });
    }

    @Test
    void demo_profile_provides_the_dev_proof_token_secret_and_a_blank_qr_secret() {
        runner.withPropertyValues("spring.profiles.active=demo").run(context -> {
            assertThat(context.getEnvironment().getProperty("billing.bank-transfer.proof.token-secret"))
                .isNotBlank();
            assertThat(context.getEnvironment().getProperty("demo.qr-reader.secret")).isEmpty();
        });
    }

    @Test
    void environment_variables_override_the_defaults() {
        runner.withPropertyValues(
            "spring.profiles.active=demo",
            "DEMO_STUDENT_PASSWORD=from-env",
            "DEMO_QR_READER_SECRET=qr-from-env",
            "BILLING_BANK_TRANSFER_PROOF_TOKEN_SECRET=secret-from-env"
        ).run(context -> {
            assertThat(context.getEnvironment().getProperty("demo.users.student-password"))
                .isEqualTo("from-env");
            assertThat(context.getEnvironment().getProperty("demo.qr-reader.secret"))
                .isEqualTo("qr-from-env");
            assertThat(context.getEnvironment().getProperty("billing.bank-transfer.proof.token-secret"))
                .isEqualTo("secret-from-env");
        });
    }

    @Test
    void no_demo_configuration_is_loaded_without_the_profile() {
        runner.run(context -> {
            for (String role : ROLES) {
                assertThat(context.getEnvironment().containsProperty("demo.users." + role + "-password")).isFalse();
            }
            assertThat(context.getEnvironment().containsProperty("demo.qr-reader.secret")).isFalse();
        });
    }
}
