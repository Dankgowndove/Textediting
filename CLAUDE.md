# CLAUDE.md

本文件为 Claude Code (claude.ai/code) 在此仓库中工作时提供指导。

## 构建命令

```bash
./gradlew :app:assembleDebug       # 调试 APK → app/build/outputs/apk/debug/
./gradlew :app:assembleRelease     # 发布 APK（需要 app/keystore.properties）
./gradlew :app:test                # 运行单元测试（当前无测试）
```

未配置 lint 或格式化工具。CI 工作流（`.github/workflows/build.yml`）在推送到 `main` 时执行 `./gradlew :app:assembleDebug --stacktrace`。

## 架构

单 Activity MVVM Android 应用（`com.dlam.textediting`），minSdk 24，targetSdk 34，compileSdk 36。Kotlin 2.1 + Jetpack Compose + Material 3。

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
| `MainViewModel.kt` | 所有应用状态。文件 I/O、标签页管理、搜索（本地 + 全局）、文件树、全局替换。 |
| `MainScreen.kt` | 顶层 Compose UI。工具栏、标签栏、搜索栏、包装 `LinedEditText` 的 `AndroidView`、所有对话框。 |
| `editor/LinedEditText.kt` | 自定义 `AppCompatEditText`，带 Canvas 绘制的行号栏。**硬件加速图层**使滚动时不会重复触发 `onDraw`。仅绘制可见行 + 2 行缓冲区。使用可复用 `CharArray` 实现零分配行号格式化。 |
| `UndoManager.kt` | 全文本快照撤销/重做，最多 200 步。`record()` 跳过重复内容（先快速比较长度）。`prepareUndo()`/`prepareRedo()` 必须始终在 finally 块中与 `finishUndoRedo()` 成对调用。 |
| `SettingsManager.kt` | SharedPreferences 包装器，以 `StateFlow` 形式暴露设置供响应式 UI 使用。设置项：字体大小（10-24sp）、最大标签数（5-20）、行号开关、自动换行开关、自动保存间隔（0/30/60/120/300 秒）。 |
| `FileTreeState.kt` | 数据类：`FileTreeState`、`FileNode`、`OpenTab`、`GlobalSearchResult`。 |
| `editor/EditorColors.kt` | 编辑器控件和行号栏的硬编码亮色/暗色配色方案（非 Material 主题色）。 |
| `ui/theme/` | Material 3 主题，支持动态取色（Android 12+）。 |
| `util/StatsComputer.kt` | 文本统计计算（字符/单词/行数统计、中日韩字符检测、阅读时间估算）。 |

### 依赖（版本目录：`gradle/libs.versions.toml`）

- `androidx.documentfile` — SAF 文件操作
- `androidx.appcompat` — `AppCompatEditText` 基类
- `androidx.activity:activity-compose` — Compose Activity 集成
- Material 3 + Material Icons Extended
- 无网络、数据库或 DI 库。无 Kotlin 序列化。

### 文件系统访问

所有文件 I/O 均通过 `DocumentFile` 使用 Android 的**存储访问框架（SAF）**。目录 URI 会调用 `takePersistableUriPermission()` 获取读写权限。文件树采用延迟加载，配合按目录缓存（ViewModel 中的 `dirCache`）—— 展开目录时仅加载其直接子节点。缓存在全量刷新或根目录变更时被清除。文件类型过滤（全局搜索/替换）使用硬编码的文本/代码文件扩展名集合。

### 标签页管理

标签页存储内容 + savedText + isModified 标志的快照。切换标签页时会将当前编辑器状态序列化到旧标签页的 `OpenTab` 记录中，并从新标签页的记录中恢复。标签页数量有上限（用户可配置）；达到上限时，最旧的未修改标签页会被淘汰。若所有标签页均已修改，则拒绝打开并显示 snackbar 提示。
