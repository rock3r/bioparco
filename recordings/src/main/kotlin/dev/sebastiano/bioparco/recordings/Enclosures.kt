package dev.sebastiano.bioparco.recordings

class Enclosure(val module: String, val title: String, val creditHtml: String)

object Enclosures {
    val all: List<Enclosure> =
        listOf(
            Enclosure(
                module = "grabby-stepper",
                title = "Grabby stepper",
                creditHtml =
                    "<strong>Seb</strong> (<a href=\"https://github.com/rock3r\">rock3r</a>) · " +
                        "<a href=\"https://x.com/iamvishal16_ios/status/2100596660032172287\">Vishal’s original</a>",
            ),
            Enclosure(
                module = "chat-bubble-transition",
                title = "Chat bubble transition",
                creditHtml =
                    "<strong>Seb</strong> · Kavsoft “Chat Bubble Transition” " +
                        "(<a href=\"https://gist.github.com/rock3r/4db005c28217aadb9cb7672200f98c2e\">gist</a>)",
            ),
            Enclosure(
                module = "processing-field",
                title = "Processing field",
                creditHtml =
                    "<strong>Chris</strong> (<a href=\"https://github.com/c5inco\">c5inco</a>) · " +
                        "<a href=\"https://github.com/haplollc/ProcessingField\">Haplo ProcessingField</a>",
            ),
        )
}
