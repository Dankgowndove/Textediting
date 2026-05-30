# Textediting

Textediting 是一款轻量级的 Android 文本编辑器，基于 Jetpack Compose 和 Material 3 构建。

## 功能

- 打开、编辑和保存文本文件
- 支持通过 Android 存储访问框架（SAF）打开任意位置的文件
- 撤销/重做（最多 100 步历史）
- 文本搜索（区分大小写）
- 支持从其他应用通过 VIEW/EDIT 意图打开文件
- 自动保存状态
- Material 3 动态主题（Android 12+）

## 技术栈

| 组件 | 技术 |
|------|------|
| UI | Jetpack Compose + Material 3 |
| 架构 | 单 Activity + ViewModel |
| 语言 | Kotlin |
| 最低 SDK | Android 7.0 (API 24) |
| 目标 SDK | Android 14 (API 34) |
| 编译 SDK | Android 16 (API 36) |

## 构建

```bash
./gradlew :app:assembleDebug
```

发布构建：

```bash
./gradlew :app:assembleRelease
```

发布签名需要 `app/keystore.properties` 文件，格式如下：

```
storeFile=/path/to/keystore
storePassword=...
keyAlias=...
keyPassword=...
```

## 许可证

[MIT](LICENSE)
