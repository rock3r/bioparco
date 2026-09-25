package dev.sebastiano.bioparco.recordings

import java.nio.file.Files
import java.nio.file.Path

/**
 * Every specimen shows its animation in two places: its entry in the root README and the Recording
 * section of its own README. Each place needs an animated WebP preview, which GitHub renders
 * inline, and an MP4 link that plays in the browser. Both places show the same preview.
 */
object ReadmeMedia {
    private val webpImage = Regex("""!\[[^\]]*]\((https?://[^)\s]+\.webp)\)""")
    // A link, not an image: `![...](...mp4)` renders as a broken image, not a playable video.
    private val mp4Link = Regex("""(?<!!)\[[^\]]*]\((https?://[^)\s]+\.mp4)\)""")
    private val htmlComment = Regex("""<!--[\s\S]*?-->""")

    /** Hosts that serve MP4s as attachments, so a browser downloads them instead of playing. */
    private val downloadingHost =
        Regex(
            """^https://(raw\.githubusercontent\.com/|github\.com/[^/]+/[^/]+/releases/(latest/)?download/)"""
        )

    /** Human-readable problems, empty when every specimen is covered. */
    fun problems(root: Path): List<String> {
        val rootReadme = rendered(Files.readString(root.resolve("README.md")))
        return RecordingPaths.specimenModules(root.resolve("settings.gradle.kts")).flatMap {
            problemsFor(it, rootReadme, root.resolve(it).resolve("README.md"))
        }
    }

    private fun problemsFor(module: String, rootReadme: String, own: Path): List<String> =
        buildList {
            val entry = rootEntry(rootReadme, module)
            if (entry == null) {
                add("README.md has no `### [...]($module/)` entry")
            } else {
                addAll(check("README.md entry for $module", entry))
            }
            val recording =
                own.takeIf(Files::isRegularFile)?.let {
                    recordingSection(rendered(Files.readString(it)))
                }
            when {
                !Files.isRegularFile(own) -> add("$module/README.md is missing")
                recording == null -> add("$module/README.md has no `## Recording` section")
                else -> addAll(check("$module/README.md `## Recording`", recording))
            }
            if (entry != null && recording != null) {
                if (differ(webpImage, entry, recording)) {
                    add("README.md and $module/README.md should show the same WebP preview")
                }
                if (differ(mp4Link, entry, recording)) {
                    add("README.md and $module/README.md should link the same MP4")
                }
            }
        }

    /** [markdown] without HTML comments, which GitHub does not render. */
    private fun rendered(markdown: String): String = markdown.replace(htmlComment, "")

    /** True when both texts have a [pattern] match and the first matches differ. */
    private fun differ(pattern: Regex, a: String, b: String): Boolean {
        val first = pattern.find(a)?.groupValues?.get(1) ?: return false
        val second = pattern.find(b)?.groupValues?.get(1) ?: return false
        return first != second
    }

    private fun check(where: String, text: String): List<String> = buildList {
        if (webpImage.find(text) == null) {
            add("$where has no animated WebP preview (`![...](...webp)`)")
        }
        val mp4s = mp4Link.findAll(text).map { it.groupValues[1] }.toList()
        if (mp4s.isEmpty()) add("$where has no MP4 link")
        mp4s.filter(downloadingHost::containsMatchIn).forEach {
            add("$where links $it, which downloads instead of playing")
        }
    }

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
