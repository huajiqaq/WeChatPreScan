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

    /** 每组字符串联合匹配一个目标，不同组之间取并集。 */
    private class Profile(
        val tag: String,
        val features: Array<Array<String>>,
        val useTinker: Boolean,
    )

    private val PROFILES = mapOf(
        MainHook.WECHAT to Profile(
            tag = "wxprescan",
            features = arrayOf(
                arrayOf("MicroMsg.QBarStringHandler"),
                arrayOf("key_offline_scan_show_tips"),
            ),
            useTinker = true,
        ),
        MainHook.WECOM to Profile(
            tag = "wqprescan-v2",
            features = arrayOf(
                arrayOf("ScanHelper", "needGetUrlActionCode urlOpenType"),
                arrayOf("wx_code", "https://mp.weixin.qq.com/a/~~"),
            ),
            useTinker = false,
        ),
    )

    @Volatile
    private var started = false

    @Volatile
    private var scanning = false

    @Volatile
    private var storeReady = false

    fun init(module: XposedModule, loader: ClassLoader, ctx: Context, host: String, processName: String) {
        if (processName != host) {
            XLog.i("非主进程（$processName），跳过")
            return
        }
        if (host !in PROFILES) {
            XLog.w("未知宿主 $host，跳过")
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
        startScan(module, appLoader, ctx, host)
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
    private fun startScan(module: XposedModule, loader: ClassLoader, ctx: Context, host: String) {
        val profile = PROFILES[host] ?: return
        synchronized(this) {
            if (scanning) return
            scanning = true
        }
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            val t0 = System.currentTimeMillis()
            try {
                val vc = try {
                    ctx.packageManager.getPackageInfo(host, 0).longVersionCode
                } catch (t: Throwable) {
                    0L
                }
                val apk = ctx.packageManager.getApplicationInfo(host, 0).sourceDir
                if (!NativeLib.ensureDexKit()) {
                    XLog.e("libdexkit 不可用")
                    return@Thread
                }
                initStore(ctx)
                val key = "${profile.tag}:$host:$vc:${if (profile.useTinker) tinkerStamp(ctx) else "none"}"
                DexKitCacheBridge.create(key, apk).use { bridge ->
                    val cands = profile.features.asSequence()
                        .flatMap { features ->
                            bridge.getMethodsOrEmpty { findMethod ->
                                findMethod.matcher(MethodMatcher.create().usingEqStrings(*features))
                            }.asSequence()
                        }
                        .filter { it.isMethod }
                        .distinctBy { "${it.declaredClassName}#${it.name}(${it.paramTypeNames.joinToString(",")})" }
                        .toList()
                    if (cands.isEmpty()) {
                        XLog.e("未命中候选，功能未生效")
                        return@use
                    }
                    XLog.i("候选 ${cands.size} 个：" + cands.joinToString { "${it.declaredClassName}#${it.name}(${it.paramTypeNames.size} 参)" })

                    var hooked = 0
                    for (c in cands) {
                        val target = resolve(loader, c.declaredClassName, c.name, c.paramTypeNames) ?: continue
                        hooked += PreScanHook.install(module, target, host)
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
