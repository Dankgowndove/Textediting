# ══════════════════════════════════════════════════════════
# ProGuard / R8 代码混淆规则
#
# 当前项目为纯文本编辑器，无需特殊混淆规则。
# 以下为常见配置模板，可按需取消注释使用。
# ══════════════════════════════════════════════════════════

# 如果在项目中使用 WebView + JavaScript，取消注释以下规则
# 并指定 JavaScript 接口类的全限定名：
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# 保留调试堆栈跟踪中的行号信息：
#-keepattributes SourceFile,LineNumberTable

# 如果保留了行号信息，可以混淆源文件名：
#-renamesourcefileattribute SourceFile
