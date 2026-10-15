package io.github.huajiqaq.wxprescan.core

import android.content.Context
import android.os.Process
import io.github.huajiqaq.wxprescan.MainHook
import io.github.huajiqaq.wxprescan.hook.PreScanHook
import io.github.libxposed.api.XposedModule
import org.luckypray.dexkit.DexKitCacheBridge
import org.luckypray.dexkit.annotations.DexKitExperimentalApi
import org.luckypray.dexkit.query.matchers.MethodMatcher
import java.io.File
import java.lang.reflect.Method

object ScanBootstrap {

    private val FEATURES = arrayOf("MicroMsg.QBarStringHandler", "key_offline_scan_show_tips")

    private const val APP_TAG = "wxprescan"

    @Volatile
    private var started = false

    @Volatile
    private var scanning = false

    @Volatile
    private var storeReady = false

    fun init(module: XposedModule, loader: ClassLoader, ctx: Context, processName: String) {
        if (processName != MainHook.HOST) {
            XLog.i("非主进程（$processName），跳过")
            return
        }
        synchronized(this) {
            if (started) return
            started = true
        }
        val appLoader = try {
            ctx.classLoader ?: loader
        } catch (t: Throwable) {
            loader
        }
        startScan(module, appLoader, ctx)
    }

    fun reset() {
        synchronized(this) {
            started = false
            scanning = false
        }
    }

    @Synchronized
    private fun initStore(ctx: Context) {
        if (storeReady) return
        storeReady = true
        try {
            DexKitCacheBridge.init(DexCacheStore(ctx))
        } catch (t: Throwable) {
            XLog.w("dexkit cache init: $t")
        }
    }

    @OptIn(DexKitExperimentalApi::class)
    private fun startScan(module: XposedModule, loader: ClassLoader, ctx: Context) {
        synchronized(this) {
            if (scanning) return
            scanning = true
        }
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            val t0 = System.currentTimeMillis()
            try {
                val vc = try {
                    ctx.packageManager.getPackageInfo(MainHook.HOST, 0).longVersionCode
                } catch (t: Throwable) {
                    0L
                }
                val apk = ctx.packageManager.getApplicationInfo(MainHook.HOST, 0).sourceDir
                if (!NativeLib.ensureDexKit()) {
                    XLog.e("libdexkit 不可用")
                    return@Thread
                }
                initStore(ctx)
                val key = "$APP_TAG:$vc:${tinkerStamp(ctx)}"
                DexKitCacheBridge.create(key, apk).use { bridge ->
                    val byTag = bridge.getMethodsOrEmpty { findMethod ->
                        findMethod.matcher(MethodMatcher.create().usingEqStrings(FEATURES[0]))
                    }
                    val byKey = bridge.getMethodsOrEmpty { findMethod ->
                        findMethod.matcher(MethodMatcher.create().usingEqStrings(FEATURES[1]))
                    }
                    val cands = (byTag + byKey).filter { it.isMethod }
                        .distinctBy { "${it.declaredClassName}#${it.name}(${it.paramTypeNames.joinToString(",")})" }
                    if (cands.isEmpty()) {
                        XLog.e("未命中候选，功能未生效")
                        return@use
                    }
                    XLog.i("候选 ${cands.size} 个：" + cands.joinToString { "${it.declaredClassName}#${it.name}(${it.paramTypeNames.size} 参)" })

                    var hooked = 0
                    for (c in cands) {
                        val target = resolve(loader, c.declaredClassName, c.name, c.paramTypeNames) ?: continue
                        if (PreScanHook.install(module, target)) hooked++
                    }
                    XLog.i("已装 Hook $hooked 个（key=$key，耗时 ${System.currentTimeMillis() - t0}ms）")
                }
            } catch (t: Throwable) {
                XLog.e("dexkit scan fail", t)
            } finally {
                synchronized(this) {
                    scanning = false
                }
            }
        }, "wx-prescan-dexscan").start()
    }

    private fun tinkerStamp(ctx: Context): String = try {
        val newest = File(ctx.dataDir, "tinker").listFiles()?.maxByOrNull { it.lastModified() }
        if (newest == null) "none" else "${newest.name}.${newest.lastModified()}"
    } catch (t: Throwable) {
        "na"
    }

    private fun resolve(loader: ClassLoader, cls: String, name: String, params: List<String>): Method? = try {
        val types = Array(params.size) { typeOf(params[it], loader) }
        var cur: Class<*>? = Class.forName(cls, false, loader)
        var depth = 0
        var found: Method? = null
        while (cur != null && found == null && depth < 8) {
            found = try {
                cur.getDeclaredMethod(name, *types)
            } catch (t: Throwable) {
                null
            }
            cur = cur.superclass
            depth++
        }
        found?.also { it.isAccessible = true }
    } catch (t: Throwable) {
        XLog.e("解析 $cls#$name 失败", t)
        null
    }

    private fun typeOf(name: String, loader: ClassLoader): Class<*> = when (name) {
        "int" -> Int::class.javaPrimitiveType!!
        "long" -> Long::class.javaPrimitiveType!!
        "boolean" -> Boolean::class.javaPrimitiveType!!
        "float" -> Float::class.javaPrimitiveType!!
        "double" -> Double::class.javaPrimitiveType!!
        "byte" -> Byte::class.javaPrimitiveType!!
        "short" -> Short::class.javaPrimitiveType!!
        "char" -> Char::class.javaPrimitiveType!!
        "void" -> Void.TYPE
        else -> try {
            Class.forName(name, false, loader)
        } catch (t: Throwable) {
            Any::class.java
        }
    }
}
