# Textediting

Textediting 是一款轻量级、高性能的 Android 文本编辑器，基于 **Jetpack Compose** 和 **Material 3** 构建，专为代码与文本编辑场景优化。

## ✨ 功能

### 编辑与文件管理
- 📂 **SAF 文件访问** — 通过 Android 存储访问框架打开 / 保存任意位置的文件
- 📑 **多标签页** — 支持同时打开多个文件，拖拽排序，可配置标签上限
- 📝 **新建文件** — 快速创建空白文本文件
- 🔍 **文件浏览器** — 内置侧边栏文件树，支持展开 / 折叠目录
- ✂️ **文件操作** — 支持复制、粘贴、重命名、删除文件和文件夹
- 📋 **意图支持** — 响应 `VIEW` / `EDIT` 意图，可从其他应用直接打开文件

### 编辑体验
- ↩️ **撤销 / 重做** — 最多 200 步历史记录，支持键盘快捷键
- 🔎 **文本搜索** — 支持大小写敏感、全字匹配、上一个 / 下一个导航
- 🔢 **行号显示** — 自定义 Canvas 绘制，可开关，支持暗色 / 亮色主题
- 🎯 **跳转到行** — 快速定位到指定行号
- 📊 **文本统计** — 字符数、行数、词数、段落数、中英文分项统计、阅读时间估算
- 📋 **复制行号** — 一键复制所有行号到剪贴板

### 界面与体验
- 🌓 **暗色模式** — 自动跟随系统主题，编辑器与行号同步适配
- 🔤 **等宽字体** — 默认使用 Monospace，适合代码编辑
- 📏 **可调字体** — 10–24sp 多档字体大小可选
- ↔️ **自动换行** — 可开关，适配不同编辑场景
- 💾 **自动保存** — 可配置间隔（30s / 1min / 2min / 5min），也可关闭
- ⌨️ **键盘适配** — IME 操作处理，返回键收起键盘

### 全局操作
- 🔎 **全局搜索替换** — 在整个目录中搜索文本，支持单文件或全部替换
- 💼 **文件过滤** — 自动识别常见文本 / 代码文件类型

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

- **行号绘制**：仅绘制可见区域 ±2 行缓冲，使用可复用 `CharArray` 避免 String 分配
- **单向数据流**：EditText 为文本唯一真实源。用户输入时标记 `ignoreTextChange` 阻断回写；仅在打开文件、撤销 / 重做、切换标签页时从 ViewModel 单向写入
- **消除重组开销**：`AndroidView.update` 仅更新非文本属性（字体大小、换行、行号开关），不做全文本比较
- **拼写检查关闭**：默认禁用，节省大文件 CPU 开销

```
用户输入 → TextWatcher → ViewModel → 状态更新
                 ↑（ignoreTextChange 标志阻断回写）
外部更改 → LaunchedEffect → EditText.setText（仅此路径写入）
```

## 📄 许可证

[MIT](LICENSE)
