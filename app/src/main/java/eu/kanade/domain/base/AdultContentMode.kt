package eu.kanade.domain.base

enum class AdultContentMode(val value: Int) {
    SHOW(0),
    BLUR(1),
    HIDE(2),
    ;

    companion object {
        fun from(value: Int): AdultContentMode = entries.firstOrNull { it.value == value } ?: BLUR
    }
}
