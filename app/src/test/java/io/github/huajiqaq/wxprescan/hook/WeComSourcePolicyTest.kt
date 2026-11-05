package io.github.huajiqaq.wxprescan.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class WeComSourcePolicyTest {

    @Test
    fun albumAndLongPressOpenTypesBecomeCamera() {
        assertEquals(1011, WeComSourcePolicy.openType(1012))
        assertEquals(1011, WeComSourcePolicy.openType(1013))
    }

    @Test
    fun cameraOpenTypeIsUnchanged() {
        assertEquals(1011, WeComSourcePolicy.openType(1011))
    }

    @Test
    fun unknownOpenTypesAreUnchanged() {
        for (source in listOf(Int.MIN_VALUE, -1, 0, 1, 4, 1007, 1014, Int.MAX_VALUE)) {
            assertEquals("source=$source", source, WeComSourcePolicy.openType(source))
        }
    }

    @Test
    fun albumAndLongPressWxCodeSourcesBecomeCamera() {
        for (source in listOf(2, 3, 4)) {
            assertEquals(1, WeComSourcePolicy.wxCodeSource(source))
        }
    }

    @Test
    fun cameraWxCodeSourceIsUnchanged() {
        assertEquals(1, WeComSourcePolicy.wxCodeSource(1))
    }

    @Test
    fun unknownWxCodeSourcesAreUnchanged() {
        for (source in listOf(Int.MIN_VALUE, -1, 0, 5, 1013, Int.MAX_VALUE)) {
            assertEquals("source=$source", source, WeComSourcePolicy.wxCodeSource(source))
        }
    }
}
