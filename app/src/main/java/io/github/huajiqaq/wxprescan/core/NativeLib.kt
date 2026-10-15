package io.github.huajiqaq.wxprescan.core

object NativeLib {

    @Volatile
    private var dexKitReady = false

    @Synchronized
    fun ensureDexKit(): Boolean {
        if (dexKitReady) return true
        return try {
            System.loadLibrary("dexkit")
            dexKitReady = true
            XLog.i("libdexkit: loaded")
            true
        } catch (t: Throwable) {
            XLog.e("libdexkit: load failed", t)
            false
        }
    }
}
