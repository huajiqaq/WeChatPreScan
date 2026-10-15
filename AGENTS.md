# AGENTS.md

微信前置扫描模块（`io.github.huajiqaq.wxprescan`）。

## 构建

```bash
./gradlew assembleRelease --offline   # JDK 21 + SDK 35，依赖全在本机 Gradle 缓存
```

## 结构

`MainHook`（入口 / 热重载）· `core/ScanBootstrap`（DexKitCacheBridge 定位）· `core/DexCacheStore`（查询缓存后端）·
`core/NativeLib` · `core/XLog` · `hook/PreScanHook`（核心 Hook）
