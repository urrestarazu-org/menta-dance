package com.menta.auth;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ArchUnit tests to validate Clean Architecture rules.
 * These tests ensure architectural boundaries are respected.
 */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.menta.auth");
    }

    @Test
    void domain_should_not_depend_on_application() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..application..")
            .check(classes);
    }

    @Test
    void domain_should_not_depend_on_infrastructure() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .check(classes);
    }

    @Test
    void application_should_not_depend_on_infrastructure() {
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
            .check(classes);
    }

    @Test
    void domain_should_not_use_spring_annotations() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .check(classes);
    }

    @Test
    void domain_should_not_use_jpa_annotations() {
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
            .check(classes);
    }

    @Test
    void application_should_not_use_spring_web_annotations() {
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.web..")
            .check(classes);
    }

    @Test
    void application_should_not_use_jpa() {
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("jakarta.persistence..")
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
            .check(classes);
    }

    @Test
    void entities_should_reside_in_domain_model_package() {
        classes()
            .that().haveSimpleNameEndingWith("Entity")
            .and().haveSimpleNameNotEndingWith("JpaEntity")
            .should().resideInAPackage("..domain.model..")
            .allowEmptyShould(true)
            .check(classes);
    }

    @Test
    void jpa_entities_should_reside_in_infrastructure() {
        classes()
            .that().haveSimpleNameEndingWith("JpaEntity")
            .should().resideInAPackage("..infrastructure.persistence.entity..")
            .check(classes);
    }

    @Test
    void controllers_should_reside_in_infrastructure_web() {
        classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("..infrastructure.web.controller..")
            .check(classes);
    }

    @Test
    void use_cases_should_reside_in_application() {
        classes()
            .that().haveSimpleNameEndingWith("UseCase")
            .or().haveSimpleNameEndingWith("UseCaseImpl")
            .should().resideInAPackage("..application..")
            .check(classes);
    }
}
