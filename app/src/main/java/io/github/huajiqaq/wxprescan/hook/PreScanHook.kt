package io.github.huajiqaq.wxprescan.hook

import io.github.huajiqaq.wxprescan.core.XLog
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

object PreScanHook {

    private const val CAMERA = 0
    private const val ALBUM = 1
    private const val LONG_PRESS = 4
    private const val CAMERA_SCENE = 4

    fun install(module: XposedModule, target: Method): Boolean = try {
        module.hook(target)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val args = chain.getArgs()
                    val idx = indexes(args.size) ?: return chain.proceed()
                    val (sourceIndex, sceneIndex) = idx
                    val source = args[sourceIndex] as? Int ?: return chain.proceed()
                    val scene = args[sceneIndex] as? Int ?: return chain.proceed()

                    if (source == CAMERA) return chain.proceed()
                    if (source != ALBUM && source != LONG_PRESS) return chain.proceed()

                    val newArgs = args.toMutableList()
                    newArgs[sourceIndex] = CAMERA
                    newArgs[sceneIndex] = CAMERA_SCENE
                    XLog.i("强制前置：source $source->0，a8KeyScene $scene->4")
                    return chain.proceed(newArgs.toTypedArray())
                }
            })
        XLog.i("hook ok: ${target.declaringClass.name}#${target.name}(${target.parameterCount} 参)")
        true
    } catch (t: Throwable) {
        XLog.e("hook err: ${target.declaringClass.name}#${target.name}", t)
        false
    }

    private fun indexes(paramCount: Int): Pair<Int, Int>? {
        val sourceIndex = if (paramCount == 16) 3 else 2
        val sceneIndex = sourceIndex + 1
        return if (paramCount > sceneIndex) sourceIndex to sceneIndex else null
    }
}
