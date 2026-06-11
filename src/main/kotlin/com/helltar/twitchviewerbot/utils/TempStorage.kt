package com.helltar.twitchviewerbot.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path

object TempStorage {

    private val log = KotlinLogging.logger {}

    // dedicated subdirectory so clip files are grouped and can be wiped wholesale on startup
    val clipsDir: Path = Path.of(System.getProperty("java.io.tmpdir"), "twitchviewerbot")

    // recreate a clean working directory, removing leftovers from a previous run (e.g. after a hard crash);
    // the per-request finally block still handles normal cleanup
    fun init() {
        Files.createDirectories(clipsDir)

        Files.newDirectoryStream(clipsDir).use { entries ->
            var removed = 0

            entries.forEach { entry ->
                runCatching { Files.deleteIfExists(entry) }
                    .onSuccess { if (it) removed++ }
                    .onFailure { log.warn { "failed to delete leftover temp file $entry: ${it.message}" } }
            }

            if (removed > 0)
                log.info { "removed $removed leftover temp file(s) from $clipsDir" }
        }
    }
}
