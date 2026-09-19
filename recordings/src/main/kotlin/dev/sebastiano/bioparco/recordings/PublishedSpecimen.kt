package dev.sebastiano.bioparco.recordings

class PublishedSpecimen(val sourceHash: String, val url: String) {
    init {
        require(sourceHash.isNotBlank()) { "sourceHash is blank" }
        require(url.startsWith(HOSTED_PREFIX) && url.endsWith(".mp4")) {
            "README movies must be hosted on static.sebastiano.dev, not GitHub releases: $url"
        }
    }

    companion object {
        const val HOSTED_PREFIX = "https://static.sebastiano.dev/public/"
    }
}
