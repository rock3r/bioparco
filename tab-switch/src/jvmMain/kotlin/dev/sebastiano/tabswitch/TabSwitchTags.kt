package dev.sebastiano.tabswitch

/** Test tags for driving the specimen from Spectre. */
object TabSwitchTags {
    /** The bar. It takes the arrow keys once clicked. */
    const val BAR = "tab-switch-bar"

    /** Its semantics text is the selected tab's label. */
    const val SELECTED = "tab-switch-selected"

    /** Sits over a tab, where it rests with the current selection. A click there picks it. */
    fun tab(label: String): String = "tab-switch-tab-$label"
}
