package io.github.huajiqaq.wxprescan.core

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.luckypray.dexkit.DexKitCacheBridge

class DexCacheStore(ctx: Context) : DexKitCacheBridge.Cache {

    private val prefs: SharedPreferences =
        ctx.getSharedPreferences("wx_prescan_dexkit_cache", Context.MODE_PRIVATE)

    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getStringList(key: String, default: List<String>?): List<String>? {
        val s = prefs.getString(key, null) ?: return default
        return try {
            val a = JSONArray(s)
            List(a.length()) { a.getString(it) }
        } catch (t: Throwable) {
            default
        }
    }

    override fun putStringList(key: String, value: List<String>) {
        prefs.edit().putString(key, JSONArray(value).toString()).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun getAllKeys(): Collection<String> = prefs.all.keys

    override fun clearAll() {
        prefs.edit().clear().apply()
    }
}
