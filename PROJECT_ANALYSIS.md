# Textediting 项目分析文档

> **版本**: 2.0 | **分析日期**: 2026-08-11 | **许可证**: MIT
>
> 本版为**精简版**：只保留单文件打开/编辑/保存、行号显示与文本搜索（含跳转行）。已移除多标签页、文件树、全局搜索/替换、语法高亮、文本统计、最近文件、自动保存、撤销/重做及配套设置项。

---

## 目录

1. [项目概述](#1-项目概述)
2. [目录结构](#2-目录结构)
3. [架构设计](#3-架构设计)
4. [模块详细分析](#4-模块详细分析)
5. [编辑器数据流（核心）](#5-编辑器数据流核心)
6. [行号绘制与修复](#6-行号绘制与修复)
7. [技术栈总览](#7-技术栈总览)
8. [已移除功能记录](#8-已移除功能记录)

---

## 1. 项目概述

基于 **Jetpack Compose** 与 **Material 3** 的轻量级 Android 文本编辑器，采用 **MVVM** 架构，通过自定义 `AppCompatEditText` + Canvas 行号绘制实现高性能编辑体验。

### 核心特性

| 分类 | 特性 |
|------|------|
| 文件 | SAF 打开/另存、新建空白文档、`VIEW`/`EDIT` 意图 |
| 编辑 | 文本输入、行号显示、自动换行、字体、暗色模式 |
| 搜索 | 当前文件查找、上一个/下一个、跳转到行 |
| 编辑控件 | 行号栏（Canvas）、当前行高亮、括号匹配、空白字符可视化（默认关闭） |

---

## 2. 目录结构

```
Textediting/
├── .github/workflows/build.yml        # CI/CD（assembleDebug）
├── app/
│   ├── build.gradle.kts               # 应用模块构建配置
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/                       # 图标、字符串、主题
│       └── kotlin/com/dlam/textediting/
│           ├── MainActivity.kt        # 入口 Activity（VIEW/EDIT 意图）
│           ├── MainScreen.kt          # 主界面 Compose（工具栏/搜索/编辑器）
│           ├── MainViewModel.kt       # 核心 ViewModel（状态与文件/搜索逻辑）
│           ├── SettingsManager.kt     # 设置管理器
│           ├── editor/
│           │   ├── LinedEditText.kt   # 自定义编辑器控件（行号绘制）
│           │   └── EditorColors.kt    # 编辑器配色方案
│           ├── dialogs/
│           │   ├── EditorComponents.kt # 搜索栏
│           │   ├── GoToLineDialog.kt   # 跳转到行对话框
│           │   └── SettingsDialog.kt   # 设置对话框
│           └── ui/theme/               # 主题（Color/Theme/Type）
├── gradle/libs.versions.toml          # 版本目录
└── build.gradle.kts / settings.gradle.kts / gradlew
```

---

## 3. 架构设计

### 3.1 整体架构：MVVM 模式

```
MainActivity
  └── MainScreen (Compose)
        ├── TopAppBar（打开/新建/保存/搜索/更多菜单，可横向滚动）
        ├── SearchBar（AnimatedVisibility）
        └── LinedEditText（AndroidView）
                ├── Gutter（Canvas 行号）
                └── AppCompatEditText 内容区
                ↕ StateFlow / SharedFlow
  MainViewModel（编辑状态、搜索状态、SettingsManager）
```

### 3.2 设计模式

| 模式 | 应用场景 |
|------|---------|
| MVVM | Activity → ViewModel → StateFlow → Compose UI 单向数据流 |
| 观察者模式 | StateFlow / SharedFlow 驱动 UI 响应式更新 |

---

## 4. 模块详细分析

### 4.1 入口层 —— MainActivity.kt

- **启动模式**：`singleTask`，新 Intent 通过 `onNewIntent` 接收
- **Intent 处理**：支持 `ACTION_VIEW` / `ACTION_EDIT`
- **Edge-to-Edge**：启用全面屏，主题由 `SettingsManager.darkThemeMode` 与系统决定

### 4.2 UI 层 —— MainScreen.kt

顶层 Compose UI，组合工具栏、搜索栏、编辑器与对话框。

- 工具栏动作（文件打开时）：搜索、保存、更多（跳转行 / 设置），支持左右滑动访问
- 空状态页：打开文件 / 新建文件 两个入口
- 编辑器通过 `AndroidView(LinedEditText)` 集成，`update` 仅同步非文本属性

### 4.3 ViewModel 层 —— MainViewModel.kt

状态（StateFlow）：`textContent`、`isModified`、`currentUri`、`fileName`、`isLoading`、搜索相关（`searchQuery`/`isSearchVisible`/`searchMatchCount`/`currentSearchIndex`/`searchPositions`）、`settings`。

事件（SharedFlow）：`snackbarEvent`。

核心方法：

| 模块 | 方法 |
|------|------|
| 文件 I/O | `openFile()`、`createNewFile()`、`saveFile()`、`saveAs()`、`getFileName()` |
| 文本变更 | `onTextChanged()` |
| 搜索 | `toggleSearch()`、`dismissSearch()`、`onSearchQueryChanged()`、`searchNext()`、`searchPrevious()`、`getSearchPosition()`、`findAllPositions()` |

### 4.4 编辑器核心 —— LinedEditText.kt

继承 `AppCompatEditText`，配置属性：`showLineNumbers`、`darkMode`、`colorScheme`、`highlightCurrentLine`、`bracketMatching`、`showWhitespace`。

绘制层次（onDraw）：

```
1. drawRect(currentLineHighlight)  ← 当前行高亮（在文本下方）
2. super.onDraw(canvas)            ← 文本、光标、选区
3. drawGutter()                    ← 行号栏（在文本上方）
4. drawWhitespace()                ← 空白字符可视化
```

### 4.5 SettingsManager.kt

基于 SharedPreferences，暴露为 StateFlow。设置项：字体大小、显示行号、自动换行、主题模式。

### 4.6 对话框

- `SearchBar`：搜索输入、匹配计数、上/下导航
- `GoToLineDialog`：数字输入 + 行号范围校验（1..N），跳转调用 `LinedEditText.scrollToLine()`
- `SettingsDialog`：字体大小 / 主题模式下拉 + 行号 / 换行开关

---

## 5. 编辑器数据流（核心）

```
用户输入 → TextWatcher.afterTextChanged
    ├── ignoreTextChange = true（先设标志）
    └── viewModel.onTextChanged(text) → _textContent.value = text
          └── LaunchedEffect(content)
                ├── ignoreTextChange == true → 跳过回写，清除标志
                └── 否则 → EditText.setText(content)  ← 仅外部变更走这里

外部变更（打开文件）：ViewModel 更新 _textContent → LaunchedEffect → setText()
```

---

## 6. 行号绘制与修复

### 绘制策略

- 仅绘制可见行 ± 3 行缓冲，大文件保持常量开销
- 可复用 `CharArray(10)` 零分配格式化行号
- 行号右侧对齐，行号栏底色 + 分割线

### 本轮修复（Bug #8）

修复真机验证仍存在的"行号显示有问题"问题（错位、数字不对应、滚动滞后），根因与对策：

1. **行号宽度在 `onDraw`（绘制阶段）内测量并改 padding**：绘制阶段修改 padding 会触发 `requestLayout`，导致本帧文本（旧 padding）与行号（新宽度）**错位**，且行数位数变化时整视图反复重排抖动。
   → 改为：新增 `updateGutterWidth()`，在 `onLayout`（布局阶段）按总行数位数计算宽度并调整 padding（宽度只增不减，最多多一次布局后稳定）；`drawGutter()` 只读宽度，绘制路径**零测量、零分配**。
2. **滚动滞后/拖影**：滚动时每帧执行 `measureText("0".repeat(...))`（字符串分配 + 测量）。
   → 移除绘制阶段的宽度计算后，滚动路径无分配；保留 `postInvalidateOnAnimation()` 随绘制节拍刷新。
3. **行号越界防护**：行号 ≥ 10^10 时可能冲垮 `CharArray(10)` 缓冲。
   → 增加 `drawText(num.toString())` 兜底分支。
4. **垂直对齐**：行号 baseline 与当前行高亮均以 `extendedPaddingTop` 为基准，与 TextView 内部文本绘制偏移保持一致，确保滚动、软键盘弹出后仍逐行对齐。

### 验证清单

- [ ] 小文件行号从 1 起且逐行对齐
- [ ] 大文件（≥1 万行）打开无闪帧、行号栏宽度稳定
- [ ] 滚到底部行号与文本对齐、不滞后
- [ ] 自动换行开/关、字体 10–24sp、暗色/亮色、软键盘弹出均对齐

---

## 7. 技术栈总览

| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 2.1.0 |
| UI | Jetpack Compose + Material 3（BOM 2025.10.01） |
| 构建 | Gradle 9.0 / AGP 8.13.0（Kotlin DSL） |
| SDK | minSdk 24，targetSdk 34，compileSdk 36 |
| 架构 | Activity Compose + Lifecycle Runtime KTX + AppCompat |
| 文件 | SAF + ContentResolver（无 DocumentFile） |
| 异步 | Kotlin Coroutines + Flow |

明确不使用：网络库、数据库、DI 框架、SAF DocumentFile、Kotlin Serialization。

---

## 8. 已移除功能记录

| 功能 | 移除文件 |
|------|---------|
| 多标签页 | `MainViewModel`/`MainScreen` 中相关状态与方法、EditorComponents 中 `TabBar` |
| 文件树/文件操作 | `FileTreePanel.kt`、`FileTreeState.kt` 及相关方法 |
| 全局搜索/替换 | `GlobalReplaceDialog`、`startGlobalSearch`/`performGlobalReplace` 等 |
| 语法高亮 | `editor/SyntaxHighlighter.kt` 及高亮管线 |
| 文本统计 | `dialogs/StatsDialog.kt`、`util/StatsComputer.kt` |
| 最近文件 | `RecentFilesManager.kt` |
| 撤销/重做 | `UndoManager.kt`、顶栏撤销/重做按钮及 `onUndoRedoApplied()` |
| 搜索过滤选项 | `isCaseSensitive`/`isWholeWord` 状态、`toggleCaseSensitive()`/`toggleWholeWord()`、SearchBar 的 FilterChip |
| 自动保存/最近相关设置 | SettingsManager/SettingsDialog 中对应项 |
