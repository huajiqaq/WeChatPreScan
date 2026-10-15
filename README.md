# WeChatPreScan

微信「前置扫描」Xposed 模块：把相册选图 / 长按图片识别出的二维码，强制改走微信扫一扫（相机）识别链路。

## 属性

- 宿主：`com.tencent.mm`
- 包名：`io.github.huajiqaq.wxprescan`
- 框架：LibXposed API 102（LSPosed 1.9.3+）
- 无界面：状态看 logcat，TAG `WxPreScan`
- 定位：DexKit 按特征串定位宿主方法，不写死混淆类名

## 构建

```bash
./gradlew assembleRelease --offline
# app/build/outputs/apk/release/WeChatPreScan_1.0_<abi>.apk（JDK 21 + Android SDK 35）
```

## 鸣谢

- [WAuxiliary](https://github.com/HdShare/WAuxiliary_Public) —— 主要参考，`MockScanHook` 的强制扫码场景逻辑与 DexKit 特征串都来自这里。
