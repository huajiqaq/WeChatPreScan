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
        if (param.packageName !in HOSTS) return
        if (!param.isFirstPackage) return
        synchronized(this) {
            if (installed) return
            installed = true
        }

        instance = this
        hostPackage = param.packageName
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
                            if (c != null && c.packageName == param.packageName && appContext == null) {
                                val app = c.applicationContext ?: c
                                appContext = app
                                ScanBootstrap.init(this@MainHook, param.classLoader, app, param.packageName, processName)
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

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        // Object[] 与宿主对象可以跨模块 ClassLoader 交接，不依赖旧模块的自定义类型。
        param.setSavedInstanceState(arrayOf<Any?>(hostLoader, appContext, hostPackage))
        return true
    }

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        instance = this
        processName = param.processName
        val state = param.savedInstanceState as? Array<*>
        val loader = state?.getOrNull(0) as? ClassLoader ?: hostLoader
        val app = state?.getOrNull(1) as? Context ?: appContext
        val host = state?.getOrNull(2) as? String ?: hostPackage
        if (loader == null || app == null || host == null || host !in HOSTS) {
            XLog.w("onHotReloaded: 缺少宿主状态，保留旧 Hook，需冷启动")
            return
        }

        hostLoader = loader
        appContext = app
        hostPackage = host
        installed = true
        val olds = runCatching { param.oldHookHandles }.getOrDefault(emptyList())
        XLog.i("onHotReloaded: 清理 ${olds.size} 个旧 Hook")
        olds.forEach { runCatching { it.unhook() } }

        ScanBootstrap.reset()
        ScanBootstrap.init(this, loader, app, host, processName)
    }

    companion object {
        const val WECHAT = "com.tencent.mm"
        const val WECOM = "com.tencent.wework"

        val HOSTS = setOf(WECHAT, WECOM)

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
        var hostPackage: String? = null
            private set

        @Volatile
        var processName: String = ""
            private set
    }
}
