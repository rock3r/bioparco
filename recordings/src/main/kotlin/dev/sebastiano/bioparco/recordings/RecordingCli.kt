package dev.sebastiano.bioparco.recordings

import java.nio.file.Path

object RecordingCli {
    @JvmStatic
    fun main(args: Array<String>) {
        check(args.size >= 2) { "usage: RecordingCli <stale|readme|publish> <repoRoot> …" }
        val repoRoot = Path.of(args[1]).toAbsolutePath().normalize()
        when (args[0]) {
            "stale" -> print(staleSpecimenNames(repoRoot).joinToString(","))
            "readme" -> ReadmeEnclosures.write(repoRoot)
            "publish" -> {
                check(args.size == 4) { "usage: RecordingCli publish <repoRoot> <specimen> <url>" }
                publish(repoRoot, args[2], args[3])
            }
            else -> error("unknown command: ${args[0]}")
        }
    }

    fun staleSpecimenNames(repoRoot: Path): List<String> {
        val published = PublishedManifest.load(repoRoot.resolve("recordings/published.json"))
        val current =
            RecordingPaths.expectedSpecimens(repoRoot.resolve("settings.gradle.kts"))
                .associateWith { specimen -> SpecimenFingerprint.hash(repoRoot, specimen) }
        return SpecimenFingerprint.staleNames(current, published)
    }

    fun publish(repoRoot: Path, specimen: String, url: String) {
        val path = repoRoot.resolve("recordings/published.json")
        val published = PublishedManifest.load(path).toMutableMap()
        published[specimen] = PublishedSpecimen(SpecimenFingerprint.hash(repoRoot, specimen), url)
        PublishedManifest.write(path, published)
    }
}
