package cz.hspinovace.psmf.ui.common

/** One option: what it means, what it is called, and -- for a pitch -- its name beside its code. */
data class Choice<T>(
    val value: T,
    val text: String,
    val detail: String? = null,
) {
    val closedText: String get() = listOfNotNull(text, detail).joinToString(" · ")
}
