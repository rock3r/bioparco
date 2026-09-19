package dev.sebastiano.bioparco.recordings

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.isRegularFile
import kotlin.streams.asSequence

object SpecimenFingerprint {
    fun hash(repoRoot: Path, specimen: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        sourceFiles(repoRoot, specimen)
            .sortedBy { it.first }
            .forEach { (relative, bytes) ->
                digest.update(relative.toByteArray())
                digest.update(0)
                digest.update(bytes)
            }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    fun staleNames(
        current: Map<String, String>,
        published: Map<String, PublishedSpecimen>,
    ): List<String> =
        current.keys.sorted().filter { name -> published[name]?.sourceHash != current[name] }

    fun sourceFiles(repoRoot: Path, specimen: String): List<Pair<String, ByteArray>> {
        val files = mutableListOf<Pair<String, ByteArray>>()
        val src = repoRoot.resolve(specimen).resolve("src")
        if (Files.isDirectory(src)) {
            Files.walk(src).use { walk ->
                walk
                    .asSequence()
                    .filter { it.isRegularFile() }
                    .forEach { file ->
                        files += relative(repoRoot, file) to Files.readAllBytes(file)
                    }
            }
        }
        val harness =
            repoRoot.resolve(
                "recordings/src/test/kotlin/dev/sebastiano/bioparco/recordings/SpecimenWindow.kt"
            )
        if (harness.isRegularFile()) {
            files += relative(repoRoot, harness) to Files.readAllBytes(harness)
        }
        val recordingTest = findRecordingTest(repoRoot, specimen)
        if (recordingTest != null) {
            files += relative(repoRoot, recordingTest) to Files.readAllBytes(recordingTest)
        }
        check(files.isNotEmpty()) { "no fingerprint sources for $specimen under $repoRoot" }
        return files
    }

    fun findRecordingTest(repoRoot: Path, specimen: String): Path? {
        val movie = "$specimen.mp4"
        val tests = repoRoot.resolve("recordings/src/test")
        if (!Files.isDirectory(tests)) return null
        return Files.walk(tests).use { walk ->
            walk
                .asSequence()
                .filter { it.fileName.toString().endsWith("RecordingTest.kt") }
                .firstOrNull { Files.readString(it).contains(movie) }
        }
    }

    private fun relative(repoRoot: Path, file: Path): String =
        repoRoot.relativize(file).toString().replace('\\', '/')
}
