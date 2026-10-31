package io.github.itlearner0.tvremote.protocol

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Sur Android, le moteur d'expressions (ICU) lit « [: … :] » comme une
 * propriété Unicode : une expression valide en Java y devient invalide et
 * fait échouer le chargement de la classe. Ce test refuse ces écritures.
 */
class RegexPortabilityTest {

    @Test
    fun noPosixLikeBracketsInRegexes() {
        val sources = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .map { File(it, "app/src/main/java") }
            .plus(generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }.map { File(it, "src/main/java") })
            .firstOrNull { File(it, "io/github/itlearner0/tvremote").isDirectory }
        assumeTrue("sources introuvables depuis ${System.getProperty("user.dir")}", sources != null)
        val offending = sources!!.walkTopDown()
            .filter { it.extension == "kt" }
            .flatMap { file -> file.readLines().mapIndexed { i, line -> "${file.name}:${i + 1}: $line" } }
            .filter { "Regex(" in it && ("[:" in it || ":]" in it) }
            .toList()
        assertTrue("Expressions non portables sur Android :\n" + offending.joinToString("\n"), offending.isEmpty())
    }
}
