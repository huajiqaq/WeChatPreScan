package io.github.huajiqaq.wxprescan.hook

internal object WeComSourcePolicy {

    fun openType(source: Int): Int = when (source) {
        1012, 1013 -> 1011
        else -> source
    }

    fun wxCodeSource(source: Int): Int = when (source) {
        2, 3, 4 -> 1
        else -> source
    }
}
