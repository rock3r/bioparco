package dev.sebastiano.bioparco.recordings

import java.nio.file.Files
import java.nio.file.Path

object PublishedManifest {
    private val entry =
        Regex(""""([^"]+)":\s*\{\s*"sourceHash":\s*"([^"]+)",\s*"url":\s*"([^"]+)"\s*}""")

    fun parse(text: String): Map<String, PublishedSpecimen> =
        entry.findAll(text).associate { match ->
            match.groupValues[1] to PublishedSpecimen(match.groupValues[2], match.groupValues[3])
        }

    fun load(path: Path): Map<String, PublishedSpecimen> =
        if (Files.isRegularFile(path)) parse(Files.readString(path)) else emptyMap()

    fun write(path: Path, specimens: Map<String, PublishedSpecimen>) {
        val body =
            specimens.entries
                .sortedBy { it.key }
                .joinToString(",\n") { (name, published) ->
                    """  "$name": {
    "sourceHash": "${published.sourceHash}",
    "url": "${published.url}"
  }"""
                }
        Files.createDirectories(path.parent)
        Files.writeString(path, "{\n$body\n}\n")
    }
}
