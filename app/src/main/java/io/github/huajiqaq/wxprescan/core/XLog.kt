package io.github.huajiqaq.wxprescan.core

import android.util.Log

object XLog {

    const val TAG = "WxPreScan"

    fun i(msg: String) = Log.println(Log.INFO, TAG, msg)

    fun w(msg: String) = Log.println(Log.WARN, TAG, msg)

    fun e(msg: String, t: Throwable? = null) =
        Log.println(Log.ERROR, TAG, if (t == null) msg else "$msg -> $t")
}
