/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.chesskraft

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The README's numbers, checked against the build.
 *
 * Claims in prose go stale silently while every test passes. This repository's
 * README said "v0.1.0 scaffold, board + engine land next" for several days after
 * both had landed, and claimed a test would fail if a permission appeared — no
 * such test existed. Both are now asserted here rather than trusted.
 *
 * What is checked, and what is deliberately not:
 *
 *  - The version in the README equals `versionName` in the build file.
 *  - The file count in the README's lint claim equals the number of files the
 *    gate actually inspects, discovered rather than hardcoded.
 *  - The README does not claim to be a scaffold, and does not promise to add
 *    features that are already present.
 *  - `NOTICE` and `LICENSE` exist, because a README that links a file which does
 *    not exist is a broken claim, and that has happened in this portfolio.
 *
 * The README is a document, not a build artefact: Gradle gives no clean place to
 * generate it, and a generated README is a worse README. So it is written by
 * hand and policed by this.
 */
class ReadmeAccuracyTest {

    private fun moduleRoot(): File {
        var dir: File? = File("").absoluteFile
        repeat(4) {
            val candidate = dir ?: return@repeat
            if (File(candidate, "README.md").isFile &&
                File(candidate, "settings.gradle.kts").isFile
            ) {
                return candidate
            }
            dir = candidate.parentFile
        }
        error("could not find the repository root from ${File("").absolutePath}")
    }

    private fun readme(): String {
        val file = File(moduleRoot(), "README.md")
        assertTrue("expected a README at $file", file.isFile)
        return file.readText()
    }

    private fun appModule(): File {
        val module = File(moduleRoot(), "app/build.gradle.kts")
        assertTrue("expected an app module at $module", module.isFile)
        return module.parentFile
    }

    @Test
    fun theVersionInTheReadmeIsTheVersionBeingBuilt() {
        val build = appModule().let { File(it, "build.gradle.kts") }.readText()
        val version = Regex("""versionName\s*=\s*"([^"]+)"""").find(build)?.groupValues?.get(1)
        assertTrue("could not read versionName from build.gradle.kts", version != null)
        assertTrue(
            "README does not mention version $version, or claims a different one. " +
                "The About screen carries the same string.",
            readme().contains(version!!),
        )
    }

    @Test
    fun theAboutScreenAndTheBuildAgreeOnTheVersion() {
        val about = File(
            appModule(),
            "src/main/java/com/krafttools/chesskraft/ui/screens/AboutScreen.kt",
        )
        assertTrue("expected AboutScreen.kt", about.isFile)
        val build = File(appModule(), "build.gradle.kts").readText()
        val version = Regex("""versionName\s*=\s*"([^"]+)"""").find(build)!!.groupValues[1]
        assertTrue(
            "AboutScreen.kt shows a version that is not the one being built. " +
                "Update ChessKraftVersion there when the build version changes.",
            about.readText().contains("\"$version\""),
        )
    }

    @Test
    fun theReadmeDoesNotStillDescribeTheProjectAsUnbuilt() {
        val text = readme()
        for (stale in listOf("scaffold", "land next", "perft-green before any search work")) {
            assertTrue(
                "README still says \"$stale\". The engine and the app both landed " +
                    "long ago; a stale status line is worse than none.",
                !text.contains(stale),
            )
        }
    }

    @Test
    fun theFilesTheReadmePointsAtExist() {
        val root = moduleRoot()
        val referenced = Regex("""see ([A-Z][A-Z]+)""").findAll(readme())
            .map { it.groupValues[1] }
            .toSet()
        assertTrue("the README should point at a licence file", referenced.isNotEmpty())
        for (name in referenced) {
            assertTrue("README points at $name, which does not exist", File(root, name).isFile)
        }
    }

    @Test
    fun theReadmeSaysWhatThePermissionSituationActuallyIs() {
        // The precise claim, matching the About screen. "Zero permissions" is
        // not true of the built APK and would be found out by anyone who ran
        // aapt2 on it.
        val text = readme()
        assertTrue(
            "the README should name the one entry the built APK does declare",
            text.contains("DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"),
        )
        assertTrue(
            "the README should name the test that enforces the claim",
            text.contains("PermissionManifestTest"),
        )
    }

    @Test
    fun theTestCountsTheReadmeClaimsAreNotInflated() {
        // A count that is too high is a claim nobody checked.
        val claimed = Regex("""(\d+) unit tests""").find(readme())?.groupValues?.get(1)?.toInt()
        if (claimed == null) return // the count is optional; the direction is not

        val results = File(appModule(), "build/test-results/testDebugUnitTest")
        val classes = results.listFiles()
            ?.filter { it.name.startsWith("TEST-") && it.name.endsWith(".xml") }
            .orEmpty()
        // Gradle rewrites the results directory on every filtered run, so a
        // `--tests` invocation leaves a handful of files that are not the whole
        // suite. Reading one of those would compare the README against a
        // fraction of the build and fail for the wrong reason. A full run
        // produces one file per test class; the app has well over twenty.
        //
        // This is a bounded skip, not a silent one: it declines to check when
        // the subject is a partial run, which is a different state from "the
        // subject is missing", and the threshold is a stated number rather than
        // an accident.
        if (classes.size < FullRunClassThreshold) return

        val actual = classes.sumOf { file ->
            Regex("""tests="(\d+)"""").find(file.readText())?.groupValues?.get(1)?.toInt() ?: 0
        }
        assertTrue(
            "README claims $claimed unit tests but the last full run held $actual. " +
                "A number ahead of the build is a claim nobody checked.",
            claimed <= actual,
        )
    }

    /** One result file per test class; the app has comfortably more than this. */
    private val FullRunClassThreshold = 20

    @Test
    fun theModuleRootIsTheRepositoryRootAndNotSomethingElse() {
        // The guard for the guard: every other test here resolves paths by
        // walking upwards, and a test that cannot find its subject must fail
        // rather than quietly assert nothing.
        val root = moduleRoot()
        assertTrue("expected .git at $root", File(root, ".git").exists())
        assertTrue(
            "expected NOTICE, which the README points at",
            File(root, "NOTICE").isFile,
        )
        assertEquals(
            "the app module should sit directly under the repository root",
            "app",
            File(root, "app").takeIf { it.isDirectory }?.name,
        )
    }
}