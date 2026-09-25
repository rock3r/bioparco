package dev.sebastiano.bioparco.recordings

import java.nio.file.Files
import java.nio.file.Path

/**
 * Every specimen shows its animation in two places: its entry in the root README and the Recording
 * section of its own README. Each place shows the specimen's animated WebP preview and links its
 * MP4, both at the stable URLs the recordings CI job publishes to on every run. Because the URLs
 * never change, the READMEs always show the latest recording.
 */
object ReadmeMedia {
    /** Where the recordings job publishes `<specimen>.webp` and `<specimen>.mp4`. */
    const val MEDIA_BASE = "https://static.sebastiano.dev/stable/bioparco/"

    private val image = Regex("""!\[[^\]]*]\(([^)\s]+)\)""")
    // A link, not an image: `![...](...mp4)` renders as a broken image, not a playable video.
    private val link = Regex("""(?<!!)\[[^\]]*]\(([^)\s]+)\)""")
    // Markdown GitHub shows as source, not as media: HTML comments, fenced blocks, code spans.
    private val unrendered =
        Regex("""<!--[\s\S]*?-->|(?m)^(```|~~~)[\s\S]*?^\1[^\n]*$|(`+)(?!`).*?(?<!`)\2(?!`)""")

    /** Human-readable problems, empty when every specimen is covered. */
    fun problems(root: Path, mediaBase: String = MEDIA_BASE): List<String> {
        val rootReadme = rendered(Files.readString(root.resolve("README.md")))
        return RecordingPaths.specimenModules(root.resolve("settings.gradle.kts")).flatMap {
            problemsFor(it, rootReadme, root.resolve(it).resolve("README.md"), mediaBase)
        }
    }

    private fun problemsFor(
        module: String,
        rootReadme: String,
        own: Path,
        mediaBase: String,
    ): List<String> = buildList {
        val preview = "$mediaBase$module.webp"
        val movie = "$mediaBase$module.mp4"
        val entry = rootEntry(rootReadme, module)
        if (entry == null) {
            add("README.md has no `### [...]($module/)` entry")
        } else {
            addAll(check("README.md entry for $module", entry, preview, movie))
        }
        val recording =
            own.takeIf(Files::isRegularFile)?.let {
                recordingSection(rendered(Files.readString(it)))
            }
        when {
            !Files.isRegularFile(own) -> add("$module/README.md is missing")
            recording == null -> add("$module/README.md has no `## Recording` section")
            else -> addAll(check("$module/README.md `## Recording`", recording, preview, movie))
        }
    }

    private fun check(where: String, text: String, preview: String, movie: String): List<String> =
        buildList {
            if (image.findAll(text).none { it.groupValues[1] == preview }) {
                add("$where does not show the preview `![...]($preview)`")
            }
            if (link.findAll(text).none { it.groupValues[1] == movie }) {
                add("$where does not link the recording `[mp4]($movie)`")
            }
            // Anything else that looks like media here is a leftover that would go stale.
            val urls = (image.findAll(text) + link.findAll(text)).map { it.groupValues[1] }
            urls
                .filter { isMedia(it) && it != preview && it != movie }
                .distinct()
                .forEach { add("$where still shows $it; use only the stable URLs") }
        }

    private fun isMedia(url: String): Boolean =
        url.substringBefore('?').lowercase().let { it.endsWith(".webp") || it.endsWith(".mp4") }

    /** [markdown] without the parts GitHub does not render as media. */
    private fun rendered(markdown: String): String = markdown.replace(unrendered, "")

    /** The text of the root README entry for [module], up to the next heading. */
    private fun rootEntry(readme: String, module: String): String? {
        val heading =
            Regex("""(?m)^### \[[^\]]+]\(${Regex.escape(module)}/?\)\s*$""").find(readme)
                ?: return null
        return sectionAfter(readme, heading.range.last + 1, deepestLevel = 3)
    }

    /** The body of a specimen README's `## Recording` section, up to the next `#`/`##` heading. */
    private fun recordingSection(readme: String): String? {
        val heading = Regex("""(?m)^## Recording\s*$""").find(readme) ?: return null
        return sectionAfter(readme, heading.range.last + 1, deepestLevel = 2)
    }

    private fun sectionAfter(text: String, start: Int, deepestLevel: Int): String {
        val rest = text.substring(start)
        val end = Regex("""(?m)^#{1,$deepestLevel} """).find(rest)?.range?.first ?: rest.length
        return rest.substring(0, end)
    }
}
