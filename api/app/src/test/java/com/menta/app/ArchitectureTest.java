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
            .or().haveSimpleName("CourseCatalogPortAdapter")
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
    void course_catalog_port_adapter_uses_the_virtual_and_physical_in_ports() {
        assertDirectDependency(
            "com.menta.app.billing.CourseCatalogPortAdapter",
            "com.menta.virtual.application.port.in.VirtualCourseCatalogPort"
        );
        assertDirectDependency(
            "com.menta.app.billing.CourseCatalogPortAdapter",
            "com.menta.physical.application.port.in.PhysicalCourseAvailabilityPort"
        );
    }

    @Test
    void course_catalog_port_adapter_reaches_virtual_and_physical_only_through_their_in_ports() {
        noClasses()
            .that().haveFullyQualifiedName("com.menta.app.billing.CourseCatalogPortAdapter")
            .should().dependOnClassesThat().resideInAnyPackage(
                "com.menta.virtual.domain..",
                "com.menta.virtual.application.usecase..",
                "com.menta.virtual.application.port.out..",
                "com.menta.virtual.infrastructure..",
                "com.menta.physical.domain..",
                "com.menta.physical.application.usecase..",
                "com.menta.physical.application.port.out..",
                "com.menta.physical.infrastructure.."
            )
            .check(classes);
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
