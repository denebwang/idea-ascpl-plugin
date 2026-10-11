# 0.1.0 验证记录

验证日期：2026-10-09。构建使用项目自带 Gradle Wrapper 9.8.1、IntelliJ Platform Gradle Plugin 2.19.0、Java 25 和 IDEA 2026.2 SDK（262.8665.258）。

## 自动化测试

21 项测试全部通过，失败、错误、跳过均为 0：

| 测试组 | 数量 | 验证内容 |
| --- | ---: | --- |
| AcsplLexerTest | 7 | 大小写、字符/字符串、中文注释、数值、位访问、组合开关/除法、逐 token 重启与编辑后重启 |
| CompletionTest | 4 | 文件声明、去重、名称大小写、常量/命令开关、注释和字符串抑制 |
| CatalogDocumentationTest | 3 | 资料去重和来源、重点符号文档、HTML 转义 |
| AcsplIdeTest | 7 | 文件注册、基础 PSI、函数括号、实际补全插入、声明缓存、注释切换、增量高亮、现代文档扩展及指针 |

编辑器测试运行于 IDEA 2026.2 的 headless 测试沙箱，包含修改结构体前缀后重新高亮的检查。未逐个启动四个 IDE 界面进行人工视觉验收。

完整测试报告：`build/reports/tests/test/index.html`。

## 安装包与兼容性

`buildPlugin` 和 `verifyPluginStructure` 通过。Plugin Verifier 1.410 对最终安装包的结论如下；复核使用离线模式和已缓存的 SDK，未忽略兼容性问题：

| IDE | 平台构建号 | 结论 |
| --- | --- | --- |
| IntelliJ IDEA 2026.2 | 262.8665.258 | Compatible |
| CLion 2026.2.3.1 | 262.10968.176 | Compatible |
| PyCharm 2026.2.3 | 262.10968.92 | Compatible |
| Rider 2026.2.3.1 | 262.10968.170 | Compatible |

完整报告：`build/reports/pluginVerifier/<IDE构建号>/plugins/acspl.language/0.1.0/`。

验证器在读取 IDE 产品清单时输出部分非本插件模块的缺失 classpath 警告；四个目标最终均生成 `Compatible` 结论，没有插件兼容性问题。IDEA 沙箱功能测试也通过。

ZIP 包含一个插件 JAR；已检查压缩包完整性、插件 ID/版本、`262` 至 `262.*` 范围、现代文档及补全扩展注册，以及 869 个离线资料条目。PDF、Python 提取器和测试代码不打包。

安装包：`build/distributions/acspl-language-0.1.0.zip`，144465 字节。

SHA-256：

```text
a49045e0d8f21b3cc2a6bd1b389293fd6fd5c4ddcdeefce48f2f7583eddebd22
```

## 手册资料

资料库包含 102 个命令、206 个函数、412 个变量/字段、128 个常量、16 个关键字、4 个标准结构体类型及 1 个预定义标签。

书签覆盖情况及人工核对的 63 个结构体表格成员记录于 `docs/catalog-coverage.json`。重点核对了 PTP 的组合开关、DISP 的字符串输出、FPOS 的后缀索引、AST/MST 的 #MOVE，以及 LCI 和 DPM/MotionDuration 的成员。AINOFFS、AINSCALE 使用变量章节的分类，ENCREAD 使用其函数调用签名；重复条目不会成为重复补全候选。
