package io.github.huajiqaq.wxprescan.hook

import android.app.Activity
import android.content.Context
import io.github.huajiqaq.wxprescan.MainHook
import io.github.huajiqaq.wxprescan.core.XLog
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Modifier

object PreScanHook {

    private const val CAMERA = 0
    private const val ALBUM = 1
    private const val LONG_PRESS = 4
    private const val CAMERA_SCENE = 4

    fun install(module: XposedModule, target: Method, host: String): Int = when (host) {
        MainHook.WECHAT -> if (installWeChat(module, target)) 1 else 0
        MainHook.WECOM -> installWeCom(module, target)
        else -> 0
    }

    /**
     * 微信：`MicroMsg.QBarStringHandler` 里 source/a8KeyScene 两个 int 参数，
     * 相册(1)/长按(4) 改写为相机(0)/相机场景(4)。
     */
    private fun installWeChat(module: XposedModule, target: Method): Boolean = try {
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

    /**
     * 企微按签名分派：URL 分类入口只用于取得来源对象类型，不 Hook 该方法本身；
     * 小程序入口单独归一化 source。未知签名直接跳过，不猜参数位置。
     */
    private fun installWeCom(module: XposedModule, target: Method): Int {
        val types = target.parameterTypes
        if (!Modifier.isStatic(target.modifiers) ||
            target.returnType != Boolean::class.javaPrimitiveType
        ) {
            XLog.w("跳过企微未知签名：$target")
            return 0
        }

        val wxCodeTypes = arrayOf(
            Activity::class.java, String::class.java, String::class.java,
            Boolean::class.javaPrimitiveType!!, Runnable::class.java,
            Int::class.javaPrimitiveType!!,
        )
        if (types.contentEquals(wxCodeTypes)) {
            return installWeComArgument(module, target, 5, WeComSourcePolicy::wxCodeSource)
        }

        if (types.size == 6 && types[0] == Context::class.java &&
            types[1] == String::class.java && types[2] == String::class.java &&
            types[3].isInterface && types[4].isInterface
        ) {
            val sourceType = types[5]
            val constructors = try {
                val intType = Int::class.javaPrimitiveType!!
                listOf(
                    sourceType.getDeclaredConstructor(intType),
                    sourceType.getDeclaredConstructor(intType, intType),
                )
            } catch (t: Throwable) {
                XLog.e("企微来源对象构造器不匹配：${sourceType.name}", t)
                return 0
            }
            return constructors.sumOf {
                installWeComArgument(module, it, 0, WeComSourcePolicy::openType)
            }
        }

        XLog.w("跳过企微未知签名：$target")
        return 0
    }

    /** 只改来源参数，保留构造器第二参、二维码内容和附加上下文。 */
    private fun installWeComArgument(
        module: XposedModule,
        target: Executable,
        sourceIndex: Int,
        normalize: (Int) -> Int,
    ): Int = try {
        module.hook(target)
            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
            .intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val args = chain.getArgs()
                    val source = args.getOrNull(sourceIndex) as? Int ?: return chain.proceed()
                    val cameraSource = normalize(source)
                    if (cameraSource == source) return chain.proceed()

                    val newArgs = args.toMutableList()
                    newArgs[sourceIndex] = cameraSource
                    XLog.i("企微来源：${target.declaringClass.name}#${target.name} $source->$cameraSource")
                    return chain.proceed(newArgs.toTypedArray())
                }
            })
        XLog.i("hook ok: ${target.declaringClass.name}#${target.name}(${target.parameterCount} 参，source#$sourceIndex)")
        1
    } catch (t: Throwable) {
        XLog.e("hook err: ${target.declaringClass.name}#${target.name}", t)
        0
    }

    private fun indexes(paramCount: Int): Pair<Int, Int>? {
        val sourceIndex = if (paramCount == 16) 3 else 2
        val sceneIndex = sourceIndex + 1
        return if (paramCount > sceneIndex) sourceIndex to sceneIndex else null
    }
}
