package com.menta.billing;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit tests to validate Clean Architecture rules for Billing module.
 */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.menta.billing");
    }

    @Test
    void domain_should_not_depend_on_application() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..application..")
            .allowEmptyShould(true)
            .check(classes);
    }

    @Test
    void domain_should_not_depend_on_infrastructure() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .allowEmptyShould(true)
            .check(classes);
    }

    @Test
    void application_should_not_depend_on_infrastructure() {
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .allowEmptyShould(true)
            .check(classes);
    }

    @Test
    void domain_should_not_use_spring_annotations() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .allowEmptyShould(true)
            .check(classes);
    }

    @Test
    void domain_should_not_use_jpa_annotations() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
            .allowEmptyShould(true)
            .check(classes);
    }

    /**
     * #41, US-PHYSICAL-004 (design "Checkout never references
     * {@code api:physical}"). Also structurally guaranteed at compile time —
     * {@code api:billing}'s {@code build.gradle.kts} has no dependency on
     * {@code api:physical} at all — but this rule keeps the boundary
     * self-documenting for the specific checkout use case even if that
     * ever changes.
     */
    @Test
    void checkout_use_case_should_not_depend_on_physical_module() {
        noClasses()
            .that().haveSimpleName("CreatePhysicalPurchaseCheckoutUseCaseImpl")
            .should().dependOnClassesThat().resideInAPackage("com.menta.physical..")
            .allowEmptyShould(true)
            .check(classes);
    }

    @Test
    void layered_architecture_should_be_respected() {
        layeredArchitecture()
            .consideringAllDependencies()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..application..")
            .layer("Infrastructure").definedBy("..infrastructure..")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .allowEmptyShould(true)
            .check(classes);
    }
}
