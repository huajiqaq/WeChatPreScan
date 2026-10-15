package io.github.huajiqaq.wxprescan

import android.app.Application
import android.content.Context
import io.github.huajiqaq.wxprescan.core.ScanBootstrap
import io.github.huajiqaq.wxprescan.core.XLog
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class MainHook : XposedModule() {

    @Volatile
    private var installed = false

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        instance = this
        processName = param.processName
        XLog.i("onModuleLoaded: ${param.processName} sys=${param.isSystemServer}")
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.packageName != HOST) return
        if (!param.isFirstPackage) return
        synchronized(this) {
            if (installed) return
            installed = true
        }

        instance = this
        hostLoader = param.classLoader
        XLog.i("onPackageReady: ${param.packageName} proc=$processName")

        try {
            val attach = Application::class.java.getDeclaredMethod("attach", Context::class.java)
            hook(attach)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val r = chain.proceed()
                        try {
                            val c = chain.getArg(0) as? Context
                            if (c != null && c.packageName == HOST && appContext == null) {
                                val app = c.applicationContext ?: c
                                appContext = app
                                ScanBootstrap.init(this@MainHook, param.classLoader, app, processName)
                            }
                        } catch (t: Throwable) {
                            XLog.e("post-attach init fail", t)
                        }
                        return r
                    }
                })
        } catch (t: Throwable) {
            XLog.e("hook Application.attach failed", t)
        }
    }

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean = true

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        instance = this
        val olds = runCatching { param.oldHookHandles }.getOrDefault(emptyList())
        XLog.i("onHotReloaded: 清理 ${olds.size} 个旧 Hook")
        olds.forEach { runCatching { it.unhook() } }

        installed = false
        ScanBootstrap.reset()

        val loader = hostLoader
        val app = appContext
        if (loader == null || app == null) {
            XLog.w("onHotReloaded: 缺少 classLoader/appContext，等待下次冷启动")
            return
        }
        ScanBootstrap.init(this, loader, app, processName)
    }

    companion object {
        const val HOST = "com.tencent.mm"

        @Volatile
        var instance: MainHook? = null
            private set

        @Volatile
        var hostLoader: ClassLoader? = null
            private set

        @Volatile
        var appContext: Context? = null
            private set

        @Volatile
        var processName: String = HOST
            private set
    }
}
