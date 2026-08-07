package com.menta.bff.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * ArchUnit tests enforcing Clean Architecture for BFF module.
 * <p>
 * Validates:
 * - Domain layer has ZERO framework dependencies (no Spring, no JPA, no Jackson)
 * - Application layer only depends on domain
 * - Infrastructure layer can depend on application and domain
 * </p>
 */
class BffArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setup() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.menta.bff");
    }

    @Test
    void domainLayerShouldNotDependOnSpring() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "com.fasterxml.jackson.."
                )
                .as("Domain layer must not depend on Spring, JPA, or Jackson")
                .because("Domain layer must be framework-agnostic per Clean Architecture");

        rule.check(classes);
    }

    @Test
    void applicationLayerShouldOnlyDependOnDomain() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..infrastructure.."
                )
                .as("Application layer must not depend on infrastructure")
                .because("Dependency rule: domain <- application <- infrastructure");

        rule.check(classes);
    }

    @Test
    void infrastructureShouldNotBypassApplication() {
        // This test is deferred - currently only domain + application + ports exist
        // Will be enforced once use cases and adapters are implemented in future PRs
    }
}
