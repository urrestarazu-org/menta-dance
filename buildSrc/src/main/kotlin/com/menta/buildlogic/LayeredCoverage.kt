package com.menta.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

/**
 * Registers one BUNDLE-aggregated JaCoCo line-coverage gate, scoped to a set
 * of package patterns.
 *
 * <h2>Why BUNDLE and not CLASS</h2>
 *
 * JaCoCo's per-CLASS counter weighs a two-line record and a branch-heavy use
 * case identically. Under Clean Architecture that punishes exactly the style
 * the project wants — small, intention-revealing types, thin decorators,
 * value objects — while saying nothing about whether the layer as a whole is
 * tested. BUNDLE asks the honest question instead: "is the behaviour in this
 * layer covered, with the inevitable record boilerplate riding along?"
 *
 * <h2>Why scoping happens on classDirectories, not on the rule</h2>
 *
 * A `JacocoViolationRule` with `element = BUNDLE` silently stops reporting
 * violations the moment `includes`/`excludes` is set on the rule itself —
 * confirmed empirically on this project's Gradle 9.7.0 / JaCoCo 0.8.12: the
 * same rule over the same data passes even with a deliberately impossible
 * 1.01 minimum, and fails correctly as soon as `includes` is removed (#96).
 * Scoping therefore happens on the task's `classDirectories` INPUT — a
 * FileTree filtered by package path — and the rule stays unscoped, which is
 * the one form proven to actually fail.
 *
 * <h2>Calibrate these like a ratchet, not like a target</h2>
 *
 * A gate's job is to stop coverage from sliding backwards, not to inspire.
 * A minimum set far below what the module already achieves protects nothing:
 * it lets a large fraction of the suite be deleted in silence. Keep each
 * minimum just under the layer's current real number, and raise it when the
 * layer genuinely improves.
 *
 * Note for maintainers editing this doc: Kotlin nests block comments, so an
 * Ant pattern written literally here would open one (a slash followed by two
 * stars) and break the file. Describe patterns in prose instead.
 *
 * @param taskName unique task name within the module.
 * @param minimumLineRatio e.g. `"1.00"`, `"0.90"`.
 * @param packagePatterns Ant-style class patterns — a package path followed
 *     by a recursive wildcard, matching compiled classes under it.
 * @param excludePatterns class patterns to drop from the gate; use sparingly
 *     and always with a written justification at the call site.
 */
fun Project.registerLayeredCoverageVerification(
    taskName: String,
    minimumLineRatio: String,
    packagePatterns: List<String>,
    excludePatterns: List<String> = emptyList()
): TaskProvider<JacocoCoverageVerification> {
    val testTask = tasks.named<Test>("test")
    val mainSourceSet = extensions.getByType<SourceSetContainer>()["main"]

    return tasks.register<JacocoCoverageVerification>(taskName) {
        dependsOn(testTask)
        executionData.setFrom(
            testTask.map { it.extensions.getByType<JacocoTaskExtension>().destinationFile!! }
        )
        sourceDirectories.setFrom(mainSourceSet.allJava.srcDirs)
        classDirectories.setFrom(
            mainSourceSet.output.classesDirs.asFileTree.matching {
                packagePatterns.forEach { include(it) }
                excludePatterns.forEach { exclude(it) }
            }
        )
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = minimumLineRatio.toBigDecimal()
                }
            }
        }
    }
}
