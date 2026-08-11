# CLAUDE.md

本文件为 AI 编程代理在此仓库中工作时提供指导。

## 构建命令

```bash
./gradlew :app:assembleDebug       # 调试 APK → app/build/outputs/apk/debug/
./gradlew :app:assembleRelease     # 发布 APK（需要 app/keystore.properties）
./gradlew :app:test                # 运行单元测试（当前无测试）
```

未配置 lint 或格式化工具。CI 工作流（`.github/workflows/build.yml`）在推送到 `main` 时执行 `./gradlew :app:assembleDebug --stacktrace`。

## 架构

单 Activity MVVM Android 应用（`com.dlam.textediting`），minSdk 24，targetSdk 34，compileSdk 36。Kotlin 2.1 + Jetpack Compose + Material 3。**精简版仅保留单文件打开/编辑/保存、撤销/重做、行号显示与文本搜索**；不包含多标签页、文件树、语法高亮、文本统计、最近文件或自动保存。

### 数据流（编辑器正确性的关键）

编辑器采用**单向数据流**模式，避免 Compose ↔ EditText 之间的反馈循环：

```
用户输入 → TextWatcher.afterTextChanged → ViewModel.onTextChanged()
                   ↑（ignoreTextChange 标志阻断回写路径）
外部变更 → LaunchedEffect(content) → EditText.setText() ← 只有这条路径会写入编辑器控件
```

- `MainScreen` 在 `afterTextChanged` 中**先**设置 `ignoreTextChange = true`，再调用 `viewModel.onTextChanged()`。`LaunchedEffect(content)` 检测到此标志后会跳过回写，防止光标跳动和无限循环。
- `AndroidView.update` 仅调整非文本属性（字体大小、自动换行、行号可见性）—— 绝不会比较或设置文本内容。
- 撤销/重做通过工具栏按钮处理器中的 `editTextRef.value?.setText()` 直接执行（绕过 LaunchedEffect），`UndoManager.prepareUndo()`/`prepareRedo()` 必须在 finally 块中配合 `finishUndoRedo()` 使用，以确保 `isUndoingRedoing` 标志被清除。

### 核心源文件

| 文件 | 职责 |
|------|------|
| `MainActivity.kt` | 入口点。处理 `VIEW`/`EDIT` 意图。singleTask 启动模式。 |
| `MainViewModel.kt` | 应用状态。文件 I/O（打开/保存/另存/新建）、文本搜索（大小写/全字匹配）、撤销/重做。 |
| `MainScreen.kt` | 顶层 Compose UI。工具栏、搜索栏、包装 `LinedEditText` 的 `AndroidView`、跳转行/设置对话框。 |
| `editor/LinedEditText.kt` | 自定义 `AppCompatEditText`，带 Canvas 绘制的行号栏。仅绘制可见行 + 3 行缓冲，可复用 `CharArray` 零分配格式化行号。行号宽度在绘制时按需扩宽（只增不减），不再在 `onDraw` 内 `requestLayout`，避免闪帧/抖动。垂直对齐以 `extendedPaddingTop` 为基准，滚动用 `postInvalidateOnAnimation` 刷新。 |
| `UndoManager.kt` | 全文本快照撤销/重做，最多 200 步。`record()` 跳过重复内容（先快速比较长度）。`prepareUndo()`/`prepareRedo()` 必须始终在 finally 块中与 `finishUndoRedo()` 成对调用。 |
| `SettingsManager.kt` | SharedPreferences 包装器，以 `StateFlow` 形式暴露设置供响应式 UI 使用。设置项：字体大小（10-24sp）、行号开关、自动换行开关、主题模式（跟随系统/浅色/深色）。 |
| `dialogs/SearchBar.kt`（`EditorComponents.kt`） | 文本搜索栏：输入、匹配计数、上/下导航、大小写/全字过滤。 |
| `dialogs/GoToLineDialog.kt` | 跳转到行对话框（1-based 行号）。 |
| `dialogs/SettingsDialog.kt` | 设置对话框（字体大小、行号、换行、主题）。 |
| `editor/EditorColors.kt` | 编辑器控件和行号栏的硬编码亮色/暗色配色方案（非 Material 主题色）。 |
| `ui/theme/` | Material 3 主题，支持动态取色（Android 12+）。 |

### 依赖（版本目录：`gradle/libs.versions.toml`）

- `androidx.appcompat` — `AppCompatEditText` 基类
- `androidx.activity:activity-compose` — Compose Activity 集成
- Material 3 + Material Icons Extended
- 无网络、数据库、SAF DocumentFile 或 DI 库。无 Kotlin 序列化。

### 文件系统访问

所有文件 I/O 均通过 Android 的**存储访问框架（SAF）**与 `ContentResolver` 完成（`openInputStream`/`openOutputStream`），针对单个文件 URI 操作，UTF-8 编码。不维护目录树或目录级权限，不依赖 `androidx.documentfile`。

## 移除项记录

以下功能/文件曾在早期版本中存在，当前精简版已移除：
`FileTreePanel.kt`、`FileTreeState.kt`、`RecentFilesManager.kt`、`dialogs/StatsDialog.kt`、`editor/SyntaxHighlighter.kt`、`util/StatsComputer.kt`，以及多标签页、文件树、全局搜索/替换、语法高亮、文本统计、最近文件、自动保存及其设置项。