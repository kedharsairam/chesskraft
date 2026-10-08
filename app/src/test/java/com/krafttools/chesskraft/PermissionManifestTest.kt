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
 * The permission claim, enforced.
 *
 * The README promises that if a `uses-permission` ever appears, a test fails.
 * That promise was written before anything enforced it, which makes it a
 * sentence in a README rather than a property of the app. This is the
 * property.
 *
 * Two manifests are involved and the difference matters. What *we* write lives
 * in `src/main/AndroidManifest.xml` and must be empty of permissions outright.
 * What *ships* is the merged manifest, which also contains whatever the
 * dependency graph adds — and on this build that is exactly one entry, the
 * signature-level permission AndroidX merges in so that an app's own
 * non-exported dynamic receivers are safe. It confers nothing on anyone else
 * and never prompts. Asserting the merged set equals that one entry is the
 * claim the About screen makes out loud, checked.
 */
class PermissionManifestTest {

    /**
     * The one entry the merged manifest is allowed to contain.
     *
     * `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, merged in by
     * androidx.core for targetSdk 34 and above. It is a signature-level
     * permission the app defines for itself; it grants the app nothing it did
     * not already have.
     */
    private val selfPermission = "com.krafttools.chesskraft.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"

    /** Walk up from the working directory until the app module is in hand. */
    private fun appModule(): File {
        var dir: File? = File("").absoluteFile
        repeat(4) {
            val candidate = dir ?: return@repeat
            if (File(candidate, "src/main/AndroidManifest.xml").isFile) return candidate
            dir = candidate.parentFile
        }
        error("could not find the app module from ${File("").absolutePath}")
    }

    @Test
    fun theManifestThisProjectWritesAsksForNothing() {
        val manifest = File(appModule(), "src/main/AndroidManifest.xml")
        val permissions = permissionsIn(manifest.readText())
        assertEquals(
            "The app's own manifest must declare no permissions at all. Found: $permissions",
            emptyList<String>(),
            permissions,
        )
    }

    @Test
    fun theMergedManifestAddsOnlyTheAndroidxSelfPermission() {
        val merged = mergedManifest()
        val permissions = permissionsIn(merged.readText())
        assertEquals(
            "The merged manifest should hold exactly the AndroidX self-permission. " +
                "Anything else is a dependency asking for something. Found: $permissions",
            listOf(selfPermission),
            permissions,
        )
    }

    @Test
    fun theSourceManifestIsTheOneThisTestThinksItIsReading() {
        // Guards the guard. Every other case here reads a file found by walking
        // the build directory, and a test that cannot find its subject must
        // fail rather than pass quietly — "nothing to check" and "nothing
        // found" are different states and only one of them is a pass.
        val source = File(appModule(), "src/main/AndroidManifest.xml")
        assertTrue("expected the app manifest at $source", source.isFile)
        // AGP takes the package from `namespace` in the build file, so the
        // manifest itself carries no package attribute. What identifies it is
        // the application tag and this project's own theme.
        val xml = source.readText()
        assertTrue(
            "expected an <application> tag, so this is the app's manifest and " +
                "not some other file",
            xml.contains("<application"),
        )
        assertTrue(
            "expected this project's theme, so this is the app's manifest and " +
                "not some other file",
            xml.contains("Theme.ChessKraft"),
        )
    }

    @Test
    fun noDependencyAsksForAnythingOnTheUsersBehalf() {
        // The specific ones that would break the app's central claim, named so
        // the failure reads as a sentence rather than a diff. A general test
        // above already catches everything; this one exists so that the day it
        // fails, the message says why it matters here.
        val merged = mergedManifest()
        val forbidden = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.REQUEST_INSTALL_PACKAGES",
        )
        val present = permissionsIn(merged.readText())
        val offending = present.filter { it in forbidden }
        assertTrue(
            "This app's claim is that nothing leaves the device and nothing is " +
                "asked of the user. These permissions break it: $offending",
            offending.isEmpty(),
        )
    }

    /**
     * The merged manifest of the *app*, whatever the variant directory is
     * called.
     *
     * AndroidTest variants are excluded on purpose and it matters: the test
     * runner's own manifest asks for REORDER_TASKS, so an indiscriminate walk
     * finds that one first and reports this app as asking for a permission it
     * has never heard of. The manifest we care about is the one that ships.
     */
    private fun mergedManifest(): File {
        val root = File(appModule(), "build/intermediates")
        val candidates = if (root.isDirectory) {
            root.walkTopDown()
                .maxDepth(4)
                .filter { it.isFile && it.name == "AndroidManifest.xml" }
                .filter { !it.path.contains("androidTest", ignoreCase = true) }
                .filter { it.path.contains("merged", ignoreCase = true) }
                .toList()
        } else {
            emptyList()
        }
        val merged = candidates.firstOrNull { it.path.contains("merged_manifest", ignoreCase = true) }
            ?: candidates.firstOrNull()
        assertTrue(
            "No merged manifest under $root. This test would otherwise pass " +
                "without checking anything, which is the exact failure this " +
                "project has now hit four separate times. Run " +
                "./gradlew :app:assembleDebug first — the task this suite normally " +
                "runs after.",
            merged != null,
        )
        return merged!!
    }

    /** Every `uses-permission` name in a manifest, in document order. */
    private fun permissionsIn(xml: String): List<String> =
        Regex("""<uses-permission\s+android:name="([^"]+)"""")
            .findAll(xml)
            .map { it.groupValues[1] }
            .toList()
}