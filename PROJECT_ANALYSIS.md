# Textediting 项目分析文档

> **版本**: 1.0 | **分析日期**: 2026-06-12 | **许可证**: MIT

---

## 目录

1. [项目概述](#1-项目概述)
2. [目录结构](#2-目录结构)
3. [架构设计](#3-架构设计)
4. [模块详细分析](#4-模块详细分析)
   - [4.1 入口层](#41-入口层)
   - [4.2 UI 层](#42-ui-层)
   - [4.3 ViewModel 层](#43-viewmodel-层)
   - [4.4 编辑器核心](#44-编辑器核心)
   - [4.5 语法高亮引擎](#45-语法高亮引擎)
   - [4.6 对话框组件](#46-对话框组件)
   - [4.7 工具模块](#47-工具模块)
   - [4.8 主题系统](#48-主题系统)
5. [数据流分析](#5-数据流分析)
6. [关键算法详解](#6-关键算法详解)
7. [技术栈总览](#7-技术栈总览)
8. [性能优化策略](#8-性能优化策略)
9. [构建配置分析](#9-构建配置分析)
10. [类关系图](#10-类关系图)

---

## 1. 项目概述

**Textediting** 是一款基于 **Jetpack Compose** 和 **Material 3** 的轻量级 Android 文本编辑器，专为代码与文本编辑场景优化。采用 **MVVM** 架构，通过自定义 `AppCompatEditText` + Canvas 行号绘制实现高性能编辑体验。

### 核心特性

| 分类 | 特性 |
|------|------|
| 文件管理 | SAF 文件访问、多标签页（可配置上限）、文件浏览器侧边栏、新建/复制/粘贴/重命名/删除 |
| 编辑体验 | 200 步撤销/重做、搜索（大小写/全字匹配）、行号显示、跳转到行、文本统计 |
| 界面 | 暗色模式、等宽字体、可调字体(10-24sp)、自动换行、自动保存 |
| 高级功能 | 全局搜索替换、语法高亮(15+语言)、括号匹配、当前行高亮、空白字符显示 |

---

## 2. 目录结构

```
Textediting/
├── .github/workflows/
│   └── build.yml                    # CI/CD 工作流配置
├── app/
│   ├── build.gradle.kts             # 应用模块构建配置
│   ├── proguard-rules.pro           # 代码混淆规则
│   ├── keystore.properties          # 签名密钥配置（gitignore）
│   └── src/main/
│       ├── AndroidManifest.xml      # 应用清单
│       ├── res/                     # 资源文件（图标、字符串、主题）
│       │   ├── drawable/            # 自适应图标背景
│       │   ├── drawable-v24/        # 自适应图标前景
│       │   ├── mipmap-*/            # 多密度启动图标
│       │   ├── values/              # 字符串、主题定义
│       │   └── xml/                 # 备份规则
│       └── kotlin/com/dlam/textediting/
│           ├── MainActivity.kt      # 入口 Activity
│           ├── MainScreen.kt        # 主界面 Composable
│           ├── MainViewModel.kt     # 核心 ViewModel
│           ├── FileTreePanel.kt     # 文件树侧边栏
│           ├── FileTreeState.kt     # 数据模型定义
│           ├── UndoManager.kt       # 撤销/重做管理器
│           ├── SettingsManager.kt   # 设置管理器
│           ├── RecentFilesManager.kt # 最近文件管理
│           ├── editor/
│           │   ├── LinedEditText.kt # 自定义编辑器控件
│           │   ├── SyntaxHighlighter.kt # 语法高亮引擎
│           │   └── EditorColors.kt  # 编辑器配色方案
│           ├── dialogs/
│           │   ├── EditorComponents.kt # 搜索栏/标签栏/全局替换
│           │   ├── GoToLineDialog.kt   # 跳转到行对话框
│           │   ├── SettingsDialog.kt   # 设置对话框
│           │   └── StatsDialog.kt      # 文本统计对话框
│           ├── ui/theme/
│           │   ├── Color.kt         # 主题色定义
│           │   ├── Theme.kt         # Material 3 主题
│           │   └── Type.kt          # 排版定义
│           └── util/
│               └── StatsComputer.kt # 文本统计计算
├── gradle/
│   ├── libs.versions.toml          # 版本目录（依赖管理）
│   └── wrapper/                    # Gradle Wrapper
├── build.gradle.kts                # 根构建脚本
├── settings.gradle.kts             # 项目设置
├── gradle.properties               # Gradle 属性
├── gradlew / gradlew.bat           # Gradle Wrapper 脚本
├── CLAUDE.md                       # AI 辅助开发文档
├── LICENSE                         # MIT 许可证
└── README.md                       # 项目说明
```

---

## 3. 架构设计

### 3.1 整体架构：MVVM 模式

```
┌─────────────────────────────────────────────────────────┐
│                      MainActivity                        │
│  ┌───────────────────────────────────────────────────┐  │
│  │                   MainScreen (Compose)             │  │
│  │  ┌──────────┐  ┌──────────┐  ┌─────────────────┐ │  │
│  │  │ TopAppBar│  │  TabBar  │  │  SearchBar      │ │  │
│  │  └──────────┘  └──────────┘  └─────────────────┘ │  │
│  │  ┌──────────────────────────────────────────────┐ │  │
│  │  │         LinedEditText (AndroidView)          │ │  │
│  │  │   ┌──────┐  ┌────────────────────────────┐  │ │  │
│  │  │   │Gutter│  │    EditText Content Area   │  │ │  │
│  │  │   │(Canvas)│  │  (AppCompatEditText)     │  │ │  │
│  │  │   └──────┘  └────────────────────────────┘  │ │  │
│  │  └──────────────────────────────────────────────┘ │  │
│  │  ┌──────────────────────────────────────────────┐ │  │
│  │  │  FileTreeSidebar (ModalNavigationDrawer)     │ │  │
│  │  └──────────────────────────────────────────────┘ │  │
│  └───────────────────────────────────────────────────┘  │
│                          ↕ StateFlow                     │
│  ┌───────────────────────────────────────────────────┐  │
│  │                  MainViewModel                     │  │
│  │  ┌────────────┐ ┌──────────┐ ┌────────────────┐  │  │
│  │  │UndoManager │ │Settings  │ │RecentFiles     │  │  │
│  │  └────────────┘ │Manager   │ │Manager         │  │  │
│  │                  └──────────┘ └────────────────┘  │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 3.2 设计模式

| 模式 | 应用场景 |
|------|---------|
| **MVVM** | Activity → ViewModel → StateFlow → Compose UI 单向数据流 |
| **观察者模式** | StateFlow/SharedFlow 驱动 UI 响应式更新 |
| **策略模式** | SyntaxHighlighter 根据文件扩展名选择不同语言规则 |
| **命令模式** | SyntaxHighlighter.SpanCommand 延迟应用到 Spannable |
| **单例对象** | SyntaxHighlighter 使用 Kotlin `object` 声明 |
| **工厂模式** | `detectLanguage()` 根据扩展名返回对应 LanguageRules |
| **快照模式** | UndoManager 全文本快照实现撤销/重做 |

---

## 4. 模块详细分析

### 4.1 入口层

#### MainActivity.kt

```
职责：应用入口，处理 Intent，连接 Compose UI
```

- **启动模式**：`singleTask` — 确保只有一个实例，新 Intent 通过 `onNewIntent` 接收
- **Intent 处理**：支持 `ACTION_VIEW` 和 `ACTION_EDIT`，可从外部应用打开文本文件
- **View Model 初始化**：通过 `by viewModels()` 委托创建，自动管理生命周期
- **Edge-to-Edge**：启用全面屏显示

**关键代码流**：
```
用户从文件管理器打开 .txt → Intent(ACTION_VIEW, uri)
→ onNewIntent() → handleIntent() → viewModel.openFile(uri)
```

---

### 4.2 UI 层

#### MainScreen.kt（544 行）

```
职责：顶层 Compose UI，组合所有子组件
```

**核心组件树**：
```
MainScreen
├── ModalNavigationDrawer
│   ├── ModalDrawerSheet
│   │   └── FileTreeSidebar          # 文件浏览器侧边栏
│   └── Scaffold
│       ├── TopAppBar                 # 顶部工具栏
│       │   ├── 导航按钮（打开抽屉）
│       │   ├── 标题（文件名 + 修改标记 ●）
│       │   └── 操作按钮
│       │       ├── 搜索切换
│       │       ├── 撤销
│       │       ├── 重做
│       │       ├── 保存
│       │       └── 更多菜单（复制行号/跳转/统计/设置）
│       └── Content
│           ├── TabBar                # 标签栏（LazyRow）
│           ├── SearchBar             # 搜索栏（AnimatedVisibility）
│           └── 编辑区域
│               ├── 空状态页面（打开/新建按钮 + 最近文件列表）
│               └── AndroidView(LinedEditText)  # 编辑器
```

**关键副作用处理（LaunchedEffect）**：

| LaunchedEffect | 触发条件 | 作用 |
|---------------|---------|------|
| `snackbarEvent` | ViewModel 发送 Snackbar 消息 | 显示 Snackbar |
| `pendingScrollToLine` | 全局搜索跳转 | 滚动编辑器到指定行 |
| `isModified + autoSaveInterval + currentUri` | 自动保存计时器 | 定时触发保存 |
| `content + fileName + syntaxHighlight` | 文本/文件变化 | 触发语法高亮 |
| `highlightsReady` | 高亮计算完成 | 应用高亮 Span 到编辑器 |
| `content` | 外部内容变更（ignoreTextChange 保护） | 同步编辑器文本 |

**单向数据流机制**（编辑器正确性的关键）：

```
用户输入 → TextWatcher.afterTextChanged
    ├── ignoreTextChange = true（先设标志）
    └── viewModel.onTextChanged(text)
          └── 更新 _textContent StateFlow
                └── LaunchedEffect(content) 触发
                      └── if (ignoreTextChange) → 跳过，清除标志
                      └── else → editText.setText(content)  ← 只有非用户输入走这里

外部变更路径（打开文件/撤销/重做/切换标签）:
    ViewModel 更新 _textContent → LaunchedEffect → EditText.setText()
```

**返回键处理**：
- 键盘可见时：先隐藏键盘
- 搜索可见时：关闭搜索
- 否则：默认行为

#### FileTreePanel.kt（615 行）

```
职责：侧边栏文件浏览器，支持目录导航、文件操作、全局搜索
```

**核心组件结构**：

| 组件 | 功能 |
|------|------|
| `FileTreeSidebar` | 侧边栏主容器，包含工具栏、搜索面板、文件树列表 |
| `FileTreeItem` | 单个文件树节点，支持点击展开/打开、长按上下文菜单 |
| `ContextMenuDialog` | 右键菜单：新建文件/文件夹、重命名、复制、粘贴、删除 |
| `GlobalSearchPanel` | 全局搜索面板：搜索输入、结果列表、替换入口 |
| `CreateItemDialog` | 新建文件/文件夹对话框 |
| `RenameDialog` | 重命名对话框 |

**文件树状态管理**：
- `FileTreeState` 数据类持有所有状态
- `dirCache`（ViewModel 中的 `MutableMap<Uri, List<...>>`）缓存目录列表
- 展开/折叠采用增量更新，避免全量重建

---

### 4.3 ViewModel 层

#### MainViewModel.kt（966 行）

```
职责：所有应用状态的中心枢纽
```

**状态属性表**：

| 属性 | 类型 | 说明 |
|------|------|------|
| `textContent` | `StateFlow<String>` | 当前编辑器文本内容 |
| `isModified` | `StateFlow<Boolean>` | 是否有未保存修改 |
| `currentUri` | `StateFlow<Uri?>` | 当前文件 URI |
| `fileName` | `StateFlow<String>` | 当前文件名 |
| `isLoading` | `StateFlow<Boolean>` | 加载状态 |
| `searchQuery / isSearchVisible / searchMatchCount / currentSearchIndex` | 搜索相关 | 搜索状态 |
| `openTabs` | `StateFlow<List<OpenTab>>` | 打开的标签页列表 |
| `activeTabIndex` | `StateFlow<Int>` | 当前活跃标签索引 |
| `fileTree` | `StateFlow<FileTreeState>` | 文件树状态 |
| `isCaseSensitive / isWholeWord` | `StateFlow<Boolean>` | 搜索选项 |
| `syntaxHighlightingActive` | `StateFlow<Boolean>` | 语法高亮是否激活 |

**核心功能模块**：

| 功能 | 方法 | 说明 |
|------|------|------|
| **文件 I/O** | `openFile()` | 通过 SAF 读取文件内容，支持 UTF-8 |
| | `saveFile()` | 保存到当前 URI |
| | `saveAs()` | 另存为新 URI |
| | `createNewFile()` | 创建无标题新文件 |
| **搜索** | `performSearch()` | 在文本中查找匹配位置 |
| | `searchNext()/searchPrevious()` | 导航搜索结果 |
| | `toggleCaseSensitive()/toggleWholeWord()` | 搜索选项切换 |
| **标签页** | `addOrSwitchTab()` | 添加或切换到标签 |
| | `switchToTab()/closeTab()/moveTab()` | 标签页操作 |
| **文件树** | `selectRootDir()` | 选择工作区根目录 |
| | `refreshFileTree()` | 刷新文件树 |
| | `toggleExpandDir()` | 展开/折叠目录 |
| | `createFile()/createFolder()` | 新建文件/文件夹 |
| | `deleteFile()/renameFile()` | 删除/重命名 |
| | `copyFileToClipboard()/pasteFile()` | 复制/粘贴 |
| **全局操作** | `startGlobalSearch()` | 在目录中搜索文本 |
| | `performGlobalReplace()` | 全局或单文件替换 |
| | `openFileFromGlobalSearch()` | 从搜索结果打开文件 |
| **语法高亮** | `triggerSyntaxHighlight()` | 触发高亮分析（后台线程） |
| | `applyHighlightIfReady()` | 应用高亮结果到 UI |
| **自动保存** | `scheduleAutoSave()` | 启动自动保存计时器 |

**文件树缓存机制**：
```
getOrLoadDirListing(uri):
    if dirCache[uri] exists → return cached
    else → load from SAF → cache → return

clearFileTreeCache(): 在手动刷新或根目录变更时调用
```

**文本文件扩展名过滤**：
```kotlin
val textExtensions = setOf(
    "txt", "md", "json", "xml", "csv", "ini", "cfg", "log",
    "yml", "yaml", "java", "kt", "html", "htm", "css", "js",
    "ts", "py", "sh", "bat", "properties", "gradle", "kts",
    "c", "cpp", "h", "hpp", "go", "rs", "rb", "php", "sql"
)
```

#### UndoManager.kt（78 行）

```
职责：全文本快照式撤销/重做管理
```

**数据结构**：
```
history: MutableList<String>    # 全文快照列表
index: Int                       # 当前指针位置
isUndoingRedoing: Boolean        # 防止递归记录
maxHistory: Int = 200            # 最大历史步数
```

**算法**：
```
record(text):
    1. 如果 isUndoingRedoing → 跳过（防止递归）
    2. 快速比较：长度不同 → 已变更；长度相同 → 全文比较
    3. 截断 index 之后的 redo 历史
    4. 添加新快照，超出 maxHistory 则移除最旧的
    5. 更新 index 指向最新

prepareUndo():
    1. index <= 0 → 无可撤销 → 返回 null
    2. 设置 isUndoingRedoing = true
    3. index-- → 返回 history[index]

prepareRedo():
    1. index >= history.size - 1 → 无可重做 → 返回 null
    2. 设置 isUndoingRedoing = true
    3. index++ → 返回 history[index]

finishUndoRedo():
    设置 isUndoingRedoing = false（必须在 finally 块中调用）
```

**性能考量**：
- 对超大文件（>500KB）全量快照可能导致内存压力
- `maxHistory=200` 上限限制了最坏情况的内存使用

#### SettingsManager.kt（121 行）

```
职责：基于 SharedPreferences 的设置持久化，暴露为 StateFlow
```

**设置项清单**：

| 设置 | Key | 默认值 | 可选值 |
|------|-----|-------|--------|
| 字体大小 | `font_size` | 14 sp | 10-24 |
| 最大标签数 | `max_tabs` | 10 | 5/8/10/15/20 |
| 显示行号 | `show_line_numbers` | true | true/false |
| 自动换行 | `word_wrap` | true | true/false |
| 自动保存间隔 | `auto_save_interval` | 0（关闭） | 0/30/60/120/300 秒 |
| 语法高亮 | `syntax_highlight` | true | true/false |
| 括号匹配 | `bracket_matching` | true | true/false |
| 当前行高亮 | `highlight_current_line` | true | true/false |
| 显示空白字符 | `show_whitespace` | false | true/false |

**设计特点**：
- 每个 setter 同时更新 `MutableStateFlow` 和 `SharedPreferences`
- StateFlow 初始值从 SharedPreferences 读取
- companion object 提供可选值列表

#### RecentFilesManager.kt（82 行）

```
职责：最近打开文件的持久化记录
```

**存储格式**：
```
sharedPreferences: "textediting_recent"
key: "recent_files"
value: "encodedUri1|displayName1||encodedUri2|displayName2||..."
```

**操作**：
- `recordFile()`: 将文件移到列表最前面，最多保留 20 条
- `remove()`: 删除单条记录
- `clear()`: 清空所有记录

#### FileTreeState.kt（34 行）

```
职责：文件树相关的数据模型定义
```

**数据类**：

| 类 | 字段 | 说明 |
|---|------|------|
| `FileTreeState` | rootUri, nodes, expandedUris, isLoading, error | 文件树 UI 完整状态 |
| `FileNode` | uri, name, isDirectory, depth | 单个文件树节点 |
| `OpenTab` | uri, fileName, isModified, content, savedText | 标签页快照 |
| `GlobalSearchResult` | fileUri, fileName, lineNumber, lineContent, matchStart | 全局搜索结果 |

---

### 4.4 编辑器核心

#### LinedEditText.kt（469 行）

```
职责：自定义 EditText，集成行号栏、括号匹配、当前行高亮、空白字符显示
```

**继承关系**：`AppCompatEditText`

**可配置属性**：

| 属性 | 类型 | 说明 |
|------|------|------|
| `showLineNumbers` | Boolean | 是否显示行号栏 |
| `darkMode` | Boolean | 暗色/亮色配色 |
| `highlightCurrentLine` | Boolean | 当前行高亮 |
| `bracketMatching` | Boolean | 括号匹配高亮 |
| `showWhitespace` | Boolean | 显示空格/制表符 |

**绘制层次（onDraw）**：
```
1. drawRect(currentLineHighlight)  ← 当前行高亮（在文本下方）
2. super.onDraw(canvas)           ← 文本、光标、选区
3. drawGutter()                   ← 行号栏（在文本上方）
4. drawWhitespace()               ← 空白字符可视化
```

**行号绘制优化**：
- 仅绘制可见行 ± 3 行缓冲区（`firstLine..lastLine`）
- 使用可复用 `CharArray(10)` 实现零分配行号格式化
- 动态调整栏宽（当行数超过当前位数容量时自动扩宽）
- 通过 `onScrollChanged` 触发 `invalidate()` 保证滚动时同步更新

**括号匹配算法**：
```
updateBracketHighlight(cursor):
    1. 检查 cursor-1 位置的字符是否为括号 → 是，记为 idx
    2. 否则检查 cursor 位置的字符 → 是，记为 idx
    3. 查找匹配括号：
       - 开括号：正向遍历，按深度匹配
       - 闭括号：反向遍历，按深度匹配
    4. 在 idx 和 match 位置设置 BackgroundColorSpan（金色高亮）
    5. 光标移动时清除旧 Span
```

**Scroll-to-line 功能**：
```
scrollToLine(line):
    target = (line - 1).coerceIn(0, layout.lineCount - 1)
    scrollTo(scrollX, layout.getLineTop(target))
```

**键盘处理**：
- `onTouchEvent`: 确保点击时获得焦点并弹出键盘
- `onKeyPreIme`: 处理返回键收起键盘
- `setTextSize` 重写：同步更新行号 Paint 的字体大小

#### EditorColors.kt（50 行）

```
职责：编辑器组件（文本/背景/行号栏）的独立配色方案
```

**设计原因**：编辑器需要独立于 Material 主题的配色，确保代码编辑区域的对比度和可读性。

| 模式 | 文本色 | 背景色 | 强调色 | 行号背景 | 分割线 |
|------|--------|--------|--------|----------|--------|
| 亮色 | `#1A1A1A` | `#FFFFFF` | `#6650A4` | `#F0F0F0` | `#D0D0D0` |
| 暗色 | `#EEEEEE` | `#121212` | `#BB86FC` | `#1A1A1A` | `#3A3A3A` |

---

### 4.5 语法高亮引擎

#### SyntaxHighlighter.kt（467 行）

```
职责：基于正则表达式的代码语法高亮
```

**架构**：
```
SyntaxHighlighter (object)
├── Colors                            # 亮色/暗色调色板
├── SpanType 枚举                     # KEYWORD, STRING, COMMENT, NUMBER, ANNOTATION
├── SpanCommand 数据类                # 高亮指令（位置 + 类型）
├── LanguageRules 数据类              # 语言规则定义
├── detectLanguage(fileName)          # 扩展名 → LanguageRules
├── analyse(text, rules)              # 文本分析 → List<SpanCommand>
├── applyTo(spannable, commands)      # 应用 Span 到编辑器
└── clearSpans(spannable)             # 清除所有高亮 Span
```

**支持的语言**：

| 语言 | 扩展名 | 关键字数 | 特殊规则 |
|------|--------|---------|---------|
| Kotlin | `.kt`, `.kts` | ~55 | 三引号字符串 `"""` |
| Java | `.java` | ~40 | 标准 C 风格 |
| Python | `.py` | ~30 | `#` 注释, `"""` `'''` 字符串 |
| JavaScript/TypeScript | `.js`, `.ts`, `.jsx`, `.tsx` | ~30 | 模板字符串 `` ` `` |
| C/C++/Go/Rust | `.c`, `.cpp`, `.h`, `.hpp`, `.go`, `.rs` | ~40 | 十六进制数字 |
| XML/HTML | `.xml`, `.html`, `.htm` | ~10 | `<!-- -->` 注释 |
| JSON | `.json` | 3 | 数字支持科学计数法 |
| CSS | `.css` | 0 | 带单位数字匹配 |
| Shell | `.sh`, `.bat` | ~15 | `#` 注释 |
| Ruby | `.rb` | ~25 | `=begin`/`=end` 注释 |
| PHP | `.php` | ~40 | 标准 C 风格 |
| SQL | `.sql` | ~70 | `--` 注释, 大小写双份关键字 |
| Markdown | `.md` | 0 | `<!-- -->` 注释 |

**高亮分析算法**：
```
analyse(text, rules):
    遍历文本字符 i = 0 → len:
        1. 安全检查：指令数 > 2000 → 终止
        2. 块注释匹配：检测 /* 或 <!-- 或 """ → 跳到注释结束
        3. 行注释匹配：检测 // 或 # 或 -- → 跳到行尾
        4. 字符串匹配：检测 " 或 ' 或 ` 或 """ 或 ''' → 跳到字符串结束（处理转义）
        5. 注解匹配：检测 @ 开头 → 匹配标识符
        6. 数字匹配：正则匹配数字模式
        7. 关键字匹配：正则匹配 \b(key1|key2|...)\b
        8. 无匹配 → i++
```

**性能设计**：
- 在 `Dispatchers.Default` 后台线程执行分析
- 300ms 防抖（debounce）避免频繁输入时重复计算
- 2000 条指令的安全上限防止无限循环
- 结果以 `SpanCommand` 列表形式传递，在主线程批量应用

---

### 4.6 对话框组件

#### EditorComponents.kt（259 行）

| 组件 | 功能 |
|------|------|
| `SearchBar` | 搜索栏：查询输入、匹配计数、上/下一个导航、大小写/全字过滤 Chip |
| `TabBar` | 标签栏（LazyRow）：显示修改指示点、点击切换、关闭按钮、上限提示 |
| `GlobalReplaceDialog` | 全局替换对话框：查找/替换输入、仅当前文件选项、二次确认 |

#### GoToLineDialog.kt（76 行）

- 数字键盘输入，仅允许数字字符
- 实时验证行号范围 `1..totalLines`
- 支持 IME Go 操作
- 错误提示：`"行号超出范围 (1-N)"`

#### SettingsDialog.kt（261 行）

- 9 个设置项的完整 UI
- 下拉菜单选择器（字体大小、最大标签数、自动保存间隔）
- Switch 开关（行号、换行、高亮、括号匹配、当前行高亮、空白字符）
- 声明 "设置实时生效，无需重启应用"

#### StatsDialog.kt（89 行）

- 异步计算（`Dispatchers.Default`）
- 加载指示器
- 11 项统计数据展示
- 超大文档提示（>100,000 行）

---

### 4.7 工具模块

#### StatsComputer.kt（64 行）

```
职责：文本统计分析
```

**统计指标**：

| 指标 | 计算方法 |
|------|---------|
| 总字符数（含空格） | `text.length` |
| 总字符数（不含空格） | `text.count { !it.isWhitespace() }` |
| 总行数 | `text.lines().size` |
| 非空行数 | `lines.count { it.isNotBlank() }` |
| 总词数 | `text.split(Regex("\\s+")).count { it.isNotEmpty() }` |
| 段落数 | `text.split(Regex("\\n\\s*\\n")).count { it.isNotBlank() }` |
| 中文字符 | Unicode 范围: `一`..`鿿` + `㐀`..`䶿` |
| 英文字符 | `a`..`z` + `A`..`Z` |
| 数字 | `0`..`9` |
| 标点符号 | 中英文标点共 20 种 |
| 估算阅读时间 | `总词数 / 200` 词/分钟 |

---

### 4.8 主题系统

#### Color.kt / Theme.kt / Type.kt

**配色方案**：

| 用途 | 亮色 | 暗色 |
|------|------|------|
| Primary | `#6650A4` | `#D0BCFF` |
| Secondary | `#625B71` | `#CCC2DC` |
| Tertiary | `#7D5260` | `#EFB8C8` |

**动态取色**：Android 12+ 使用 `dynamicColorScheme`，跟随系统壁纸自动生成配色。

---

## 5. 数据流分析

### 5.1 总体数据流

```
                    ┌──────────────┐
                    │ SharedPrefs  │  ← 设置持久化
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              ▼            ▼            ▼
    ┌───────────┐  ┌────────────┐  ┌──────────────┐
    │ Settings  │  │  Recent    │  │  UndoManager │
    │  Manager  │  │  Files Mgr │  │  (快照历史)   │
    └─────┬─────┘  └─────┬──────┘  └──────┬───────┘
          │              │                │
          └──────────────┼────────────────┘
                         ▼
              ┌─────────────────────┐
              │   MainViewModel    │  ← 中心状态管理
              │   (20+ StateFlow)  │
              └──────────┬──────────┘
                         │ collectAsState()
                         ▼
              ┌─────────────────────┐
              │   MainScreen        │  ← Compose UI
              │   + dialogs         │
              └──────────┬──────────┘
                         │
              ┌──────────▼──────────┐
              │  LinedEditText      │  ← 自定义编辑器
              │  (AppCompatEditText)│
              └─────────────────────┘
```

### 5.2 编辑器数据流（核心）

这是整个应用最关键的机制，用于避免 Compose ↔ EditText 之间的反馈循环：

```
┌──────────────────────────────────────────────────────────────┐
│                     编辑器数据流                              │
│                                                              │
│  ┌─────────────── 用户输入路径 ───────────────┐              │
│  │                                            │              │
│  │  LinedEditText                             │              │
│  │    │ (用户打字)                             │              │
│  │    ▼                                       │              │
│  │  TextWatcher.afterTextChanged               │              │
│  │    │                                       │              │
│  │    ├── ignoreTextChange = true  ──────────┐│              │
│  │    │                                      ││              │
│  │    └── viewModel.onTextChanged(text)      ││              │
│  │           │                               ││              │
│  │           ▼                               ││              │
│  │    _textContent.value = text              ││              │
│  │           │                               ││              │
│  │           ▼                               ││              │
│  │    LaunchedEffect(content) 触发            ││              │
│  │           │                               ││              │
│  │           ├── ignoreTextChange == true? ──┘│              │
│  │           │   跳过 setText(),清除标志       │              │
│  │           │                                │              │
│  │           └── ignoreTextChange == false?   │              │
│  │               执行 setText(content) ← 只有外部变更才走这里│
│  │                                                          │
│  └──────────────────────────────────────────────────────────┘
│
│  ┌─────────────── 外部变更路径 ───────────────┐
│  │                                            │
│  │  打开文件 / 撤销 / 重做 / 切换标签页          │
│  │    │                                       │
│  │    └── viewModel 更新 _textContent          │
│  │           │                                │
│  │           └── LaunchedEffect → setText()   │
│  │                                                          │
│  └──────────────────────────────────────────────────────────┘
└──────────────────────────────────────────────────────────────┘
```

### 5.3 语法高亮数据流

```
用户输入 / 文件切换
    │
    ▼
LaunchedEffect(content, fileName, syntaxHighlight)
    │
    ▼
viewModel.triggerSyntaxHighlight(text, fileName, darkMode)
    │
    ├── detectLanguage(fileName) → LanguageRules?
    │
    └── launch(Dispatchers.Default)
        │
        ├── delay(300ms) ← 防抖
        │
        ├── SyntaxHighlighter.analyse(text, rules) → List<SpanCommand>
        │
        └── withContext(Dispatchers.Main)
            │
            ├── _lastHighlightCommands = commands
            └── _highlightsReady.emit(Unit)
                    │
                    ▼
            LaunchedEffect(highlightsReady)
                    │
                    ▼
            viewModel.applyHighlightIfReady(spannable, darkMode)
                    │
                    ├── clearSpans(spannable)
                    └── applyTo(spannable, commands, darkMode)
```

---

## 6. 关键算法详解

### 6.1 搜索算法（findAllPositions）

```kotlin
// 输入：text（全文），query（搜索词）
// 输出：所有匹配位置的列表（Int）
//
// 算法：滑动窗口 + 条件过滤
// 时间复杂度：O(n * m) 最坏情况（n=文本长度, m=查询长度）
// 空间复杂度：O(k)（k=匹配数）

fun findAllPositions(text: String, query: String): List<Int> {
    val positions = mutableListOf<Int>()
    val searchText = if (caseSensitive) text else text.lowercase()
    val searchQuery = if (caseSensitive) query else query.lowercase()

    var index = searchText.indexOf(searchQuery, 0)
    while (index >= 0) {
        // 全字匹配时检查前后字符是否为字母/数字
        if (wholeWord) {
            val before = if (index > 0) searchText[index - 1] else ' '
            val after = if (index + queryLen < n) searchText[index + queryLen] else ' '
            if (before.isLetterOrDigit() || after.isLetterOrDigit()) {
                index = searchText.indexOf(searchQuery, index + 1)
                continue
            }
        }
        positions.add(index)
        index = searchText.indexOf(searchQuery, index + 1)
    }
    return positions
}
```

### 6.2 全局搜索算法（searchInDir）

```
searchInDir(dirUri, query, results, depth):
    if depth > 10 or results.size >= 500: return  # 安全限制
    
    children = dir.listFiles()
    for child in children:
        if results.size >= 500: break
        if child.isDirectory:
            searchInDir(child.uri, query, results, depth + 1)  # 递归
        else:
            if extension not in textExtensions: continue
            read file line by line
            for each line:
                if line contains query (ignoreCase):
                    results.add(GlobalSearchResult(...))
```

### 6.3 标签页切换状态保存

```
switchToTab(index):
    1. 保存当前标签页状态:
       tabs[activeIndex] = tabs[activeIndex].copy(
           content = _textContent.value,
           isModified = _isModified.value,
           savedText = savedText
       )
    2. 从目标标签页恢复:
       _fileName.value = target.fileName
       _currentUri.value = target.uri
       _textContent.value = target.content
       savedText = target.savedText
       _isModified.value = target.isModified
    3. 重置 Undo 管理器
```

---

## 7. 技术栈总览

| 类别 | 技术 | 版本 |
|------|------|------|
| **语言** | Kotlin | 2.1.0 |
| **UI 框架** | Jetpack Compose + Material 3 | BOM 2025.10.01 |
| **构建工具** | Gradle (Kotlin DSL) | 9.0.0 |
| **Android Gradle Plugin** | AGP | 8.13.0 |
| **最低 SDK** | Android 7.0 | API 24 |
| **目标 SDK** | Android 14 | API 34 |
| **编译 SDK** | Android 16 (Baklava) | API 36 |
| **架构组件** | Activity Compose | 1.11.0 |
| | Lifecycle Runtime KTX | 2.9.2 |
| | AppCompat | 1.7.0 |
| | AndroidX Core KTX | 1.17.0 |
| **文件访问** | SAF (DocumentFile) | 1.0.1 |
| **异步** | Kotlin Coroutines + Flow | - |
| **CI/CD** | GitHub Actions | - |
| **Java 兼容** | JVM 17 | - |

**明确不使用的**：
- 无网络库（离线优先）
- 无数据库（SharedPreferences 即够用）
- 无 DI 框架（手动依赖管理）
- 无 Kotlin Serialization

---

## 8. 性能优化策略

### 8.1 编辑器渲染优化

| 优化项 | 策略 | 效果 |
|--------|------|------|
| **行号绘制** | 仅绘制可见行 + 3 行缓冲 | O(visible) 而非 O(total lines) |
| **CharArray 复用** | 可复用 `CharArray(10)` 格式化数字 | 滚动时零分配 |
| **硬件加速** | `LAYER_TYPE_HARDWARE` | 避免滚动时重复 `onDraw` |
| **拼写检查关闭** | `TYPE_TEXT_FLAG_NO_SUGGESTIONS` | 节省大文件 CPU 开销 |
| **状态更新阻隔** | `ignoreTextChange` 标志 | 防止反馈循环 |
| **Compose 重组避免** | `AndroidView.update` 仅更新非文本属性 | 不会触发 Compose 重组 |

### 8.2 语法高亮优化

| 优化项 | 策略 |
|--------|------|
| **后台计算** | `Dispatchers.Default` 协程 |
| **防抖** | 300ms delay，频繁输入不重复计算 |
| **安全上限** | 2000 条 Span 指令后终止 |
| **分离设计与应用** | 后台分析 → 主线程批量应用 Span |

### 8.3 文件树优化

| 优化项 | 策略 |
|--------|------|
| **按目录缓存** | `dirCache: Map<Uri, List<...>>` |
| **增量展开** | 仅加载展开目录的直接子节点 |
| **排序策略** | 目录优先 + 名称不区分大小写 |
| **深度限制** | 最大递归深度 10 |
| **缓存清理** | 手动刷新或根目录变更时清理 |

### 8.4 内存优化

| 优化项 | 策略 |
|--------|------|
| **撤销历史** | maxHistory=200，超出移除最旧 |
| **全局搜索** | 结果上限 500，目录深度 10 |
| **最近文件** | 上限 20 条 |
| **标签页限制** | 可配置 5-20，超出淘汰未修改标签 |

---

## 9. 构建配置分析

### 9.1 Gradle 配置

**版本目录（libs.versions.toml）**：
- 集中管理所有依赖版本
- 使用 `[versions]`、`[libraries]`、`[plugins]` 三部分

**构建特性**：
```kotlin
// app/build.gradle.kts
compileSdk = 36           # 最新的 Android Baklava
minSdk = 24               # 覆盖 98%+ 设备
targetSdk = 34            # Android 14
jvmTarget = JVM 17        # 最新 LTS
compose = true            # 启用 Compose
```

**签名配置**：
- Release 构建从 `keystore.properties` 读取签名信息
- 文件在 `.gitignore` 中排除，保证安全

### 9.2 CI/CD

`.github/workflows/build.yml`：
- 触发器：push/PR 到 main 分支 + 手动触发
- 并发控制：同一分支同时只运行一个构建
- 步骤：Checkout → JDK 17 → Setup Gradle → Build Debug APK → Upload Artifacts
- 超时：30 分钟

---

## 10. 类关系图

```
┌─────────────────────────────────────────────────────────────┐
│                      数据模型层                              │
│                                                             │
│  FileTreeState ◄── FileNode                                 │
│  OpenTab                                               │
│  GlobalSearchResult                                    │
│  RecentFile                                           │
│  StatsResult                                          │
│  EditorColors / GutterColors                           │
│  SpanCommand / LanguageRules / SpanType                │
│                                                             │
└──────────────────────────┬──────────────────────────────────┘
                           │ 被使用
┌──────────────────────────▼──────────────────────────────────┐
│                     状态管理层                               │
│                                                             │
│  MainViewModel ──┬── SettingsManager                        │
│                  ├── RecentFilesManager                     │
│                  ├── UndoManager                            │
│                  └── SyntaxHighlighter (object)             │
│                                                             │
└──────────────────────────┬──────────────────────────────────┘
                           │ StateFlow / SharedFlow
┌──────────────────────────▼──────────────────────────────────┐
│                       UI 层                                  │
│                                                             │
│  MainActivity ── MainScreen                                 │
│                      ├── FileTreeSidebar                    │
│                      │   ├── FileTreeItem                   │
│                      │   ├── ContextMenuDialog              │
│                      │   ├── GlobalSearchPanel              │
│                      │   └── CreateItemDialog               │
│                      ├── TabBar                             │
│                      ├── SearchBar                          │
│                      ├── LinedEditText (AndroidView)        │
│                      ├── GoToLineDialog                     │
│                      ├── StatsDialog                        │
│                      ├── SettingsDialog                     │
│                      └── GlobalReplaceDialog                │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

---

> **文档生成信息**：本文档基于源代码逐文件分析生成，涵盖 20 个 Kotlin 源文件、5 个配置文件、3 个资源文件。项目采用纯 Kotlin + Compose + MVVM 架构，无外部网络依赖，代码行数约 4500 行。
