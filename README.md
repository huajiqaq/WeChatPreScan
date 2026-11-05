# WeChatPreScan

微信 / 企业微信 Xposed 模块：相册选图、长按图片识别出的二维码按扫一扫（相机）来源处理。

## 属性

- 宿主：微信 `com.tencent.mm`、企业微信 `com.tencent.wework`
- 包名：`io.github.huajiqaq.wxprescan`
- 框架：LibXposed API 102
- 无界面，日志 TAG `WxPreScan`
- [下载](https://github.com/huajiqaq/WeChatPreScan/releases) · [LSPosed 模块仓库](https://modules.lsposed.org/module/io.github.huajiqaq.wxprescan)

## 使用

1. 在框架中安装并启用模块。
2. 作用域勾选微信和／或企业微信。
3. 强制停止对应宿主后重新打开。

## Hook 目标

| 宿主 | 目标 | 修改 |
| --- | --- | --- |
| 微信 | QBarStringHandler | `source` 1 / 4 → 0，`a8KeyScene` → 4 |
| 企业微信 | 来源对象的 `(int)` / `(int,int)` 构造器 | 第一参 1013 / 1012 → 1011 |
| 企业微信 | 小程序码入口 | 第六参 2 / 3 / 4 → 1 |

未知来源值与未知签名不修改，只记录日志。企业微信适配基于 5.0.13.84802。

## 构建

```bash
./gradlew testDebugUnitTest assembleRelease --offline
```

产物：`app/build/outputs/apk/release/WeChatPreScan_1.1.apk`。

使用 JDK 21、Android SDK 35。发布签名参数放在 `local.properties` 的 `signing.*` 四项，不提交到仓库。

## 1.1

- 新增企业微信作用域与来源归一化。
- 微信保持原策略。
- 移除旧的布尔参数位置兜底。
- 修复热重载状态交接。
- 增加来源归一化单元测试。

## 鸣谢

- [WAuxiliary](https://github.com/HdShare/WAuxiliary_Public) —— 微信侧参考。
