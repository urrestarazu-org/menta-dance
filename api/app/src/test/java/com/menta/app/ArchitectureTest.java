package com.menta.app;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ArchUnit regressions for api:app's cross-module orchestration boundary. */
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void setUp() {
        classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.menta.app");
    }

    @Test
    void app_should_not_depend_on_physical_infrastructure() {
        noClasses()
            .that().resideInAPackage("com.menta.app..")
            .should().dependOnClassesThat().resideInAPackage("com.menta.physical.infrastructure..")
            .check(classes);
    }

    @Test
    void app_adapters_follow_cross_module_pattern() {
        classes()
            .that().haveSimpleName("PhysicalCapacityAssignmentAdapter")
            .or().haveSimpleName("MarkPurchaseExceptionAdapter")
            .should().resideInAPackage("com.menta.app.billing")
            .check(classes);
    }

    @Test
    void physical_capacity_assignment_adapter_uses_only_the_physical_in_port_boundary() {
        assertDirectDependency(
            "com.menta.app.billing.PhysicalCapacityAssignmentAdapter",
            "com.menta.physical.application.port.in.PhysicalCapacityAssignmentPort"
        );
    }

    @Test
    void mark_purchase_exception_adapter_uses_only_the_billing_in_port_boundary() {
        assertDirectDependency(
            "com.menta.app.billing.MarkPurchaseExceptionAdapter",
            "com.menta.billing.application.port.in.MarkPurchaseExceptionPort"
        );
    }

    @Test
    void app_should_not_own_virtual_lesson_access_policy() {
        noClasses()
            .that().resideInAPackage("com.menta.app..")
            .should().dependOnClassesThat()
            .haveFullyQualifiedName("com.menta.virtual.application.usecase.LessonAccessPolicy")
            .check(classes);
    }

    private void assertDirectDependency(String sourceClassName, String targetClassName) {
        org.assertj.core.api.Assertions.assertThat(
            classes.get(sourceClassName).getDirectDependenciesFromSelf()
        ).anySatisfy(dependency -> org.assertj.core.api.Assertions.assertThat(
            dependency.getTargetClass().getFullName()
        ).isEqualTo(targetClassName));
    }
}
