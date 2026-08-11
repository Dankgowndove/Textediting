# Textediting

Textediting 是一款轻量级、高性能的 Android 文本编辑器，基于 **Jetpack Compose** 和 **Material 3** 构建，专注于文本编辑与搜索。

## ✨ 功能

### 编辑与文件
- 📂 **SAF 文件访问** — 通过 Android 存储访问框架打开 / 另存任意位置的文件
- 📝 **新建文件** — 快速创建空白文本文件
- 💾 **保存 / 另存为** — 写回原文件或另存为新文件（UTF-8）
- 📨 **意图支持** — 响应 `VIEW` / `EDIT` 意图，可从其他应用直接打开文件

### 编辑体验
- 🔎 **文本搜索** — 上一个 / 下一个导航，滚动定位到匹配位置
- 🔢 **行号显示** — 自定义 Canvas 绘制，可开关，支持暗色 / 亮色主题，滚动实时对齐
- 🎯 **跳转到行** — 快速定位到指定行号

### 界面与体验
- 🌓 **暗色模式** — 跟随系统或手动指定，编辑器与行号同步适配
- 🔤 **等宽字体** — 默认 Monospace，适合代码和文本
- 📏 **可调字体** — 10–24sp 多档字体大小可选
- ↔️ **自动换行** — 可开关，适配不同编辑场景
- ⌨️ **键盘适配** — `adjustResize` 软键盘模式，返回键先收起键盘再关闭搜索

## 🛠 技术栈

| 组件 | 技术 |
|------|------|
| UI 框架 | Jetpack Compose + Material 3 |
| 架构 | MVVM（Activity + ViewModel） |
| 编辑器 | 自定义 AppCompatEditText + Canvas 行号绘制 |
| 编辑器优化 | 单向数据流，消除 Compose ↔ EditText 双向绑定 |
| 状态管理 | Kotlin StateFlow + SharedFlow |
| 持久化 | SharedPreferences（设置） |
| 最低 SDK | Android 7.0 (API 24) |
| 目标 SDK | Android 14 (API 34) |
| 编译 SDK | Android 16 (API 36) |

## 🚀 构建

### 调试构建
```bash
./gradlew :app:assembleDebug
```

### 发布构建
```bash
./gradlew :app:assembleRelease
```

发布签名需要在 `app/keystore.properties` 中配置：

```
storeFile=/path/to/keystore
storePassword=...
keyAlias=...
keyPassword=...
```

## 🏗 架构说明

### 编辑器性能优化

本项目编辑器采用**自定义 `LinedEditText`**（继承 `AppCompatEditText`），行号通过 `onDraw()` 的 Canvas 直接绘制，而非 Compose LazyColumn。针对大文件编辑做了以下关键优化：

- **行号绘制**：仅绘制可见区域 ±3 行缓冲，使用可复用 `CharArray` 避免 String 分配；行号栏宽度在布局阶段预计算，滚动路径零测量零分配
- **单向数据流**：EditText 为文本唯一真实源。用户输入时标记 `ignoreTextChange` 阻断回写；仅在打开文件时从 ViewModel 单向写入
- **消除重组开销**：`AndroidView.update` 仅更新非文本属性（字体大小、换行、行号开关），不做全文本比较
- **拼写检查关闭**：默认禁用，节省大文件 CPU 开销
- **滚动对齐**：`onScrollChanged` 使用 `postInvalidateOnAnimation` 保证行号随文本同步刷新

```
用户输入 → TextWatcher → ViewModel → 状态更新
                 ↑（ignoreTextChange 标志阻断回写）
外部更改 → LaunchedEffect → EditText.setText（仅此路径写入）
```

## 📄 许可证

[MIT](LICENSE)