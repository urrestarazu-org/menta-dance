package com.menta.android.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The domain layer must stay free of Android framework dependencies (no ArchUnit on Android). */
class DomainPurityTest {
    private val domainRoot = File("src/main/java/com/menta/android/domain")
    private val androidImport = Regex("""^\s*import\s+(android|androidx)\.""", RegexOption.MULTILINE)

    @Test
    fun domain_sources_are_present_so_the_scan_cannot_pass_vacuously() {
        assertTrue("Expected domain sources under ${domainRoot.path}", domainSources().isNotEmpty())
    }

    @Test
    fun domain_has_no_android_imports() {
        val offenders = domainSources()
            .filter { androidImport.containsMatchIn(it.readText()) }
            .map { it.relativeTo(domainRoot).path }

        assertEquals("Android imports found in domain/", emptyList<String>(), offenders)
    }

    private fun domainSources() = domainRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
}
