# 0.2.0 函数与变量作用域颜色

验证日期：2026-10-10。构建使用 Java 25、Gradle Wrapper 9.8.1、IntelliJ Platform Gradle Plugin 2.19.0 和 IDEA 2026.2 SDK。

## 实现与范围

- 增加项目文件索引，记录 `.prg`、`.acspl` 顶层用户函数及 GLOBAL 变量名称，按大小写匹配。
- 通过语义高亮层为用户函数、GLOBAL 变量、LOCAL 变量/参数提供独立颜色选项。颜色预览包含三个新增类别，LOCAL 仍可继承 0.1.1 的自定义标识符颜色。
- 依据声明区分函数和数组。变量、数组和数组参数优先于同名项目函数；未知名称不会仅因后面有括号就被当作函数。二维数组 `data(i)(j)` 与参数 `INT data()(3)` 均按变量处理。
- 函数参数及函数内变量只在对应函数中参与名称匹配，可以遮蔽文件外层的 GLOBAL/LOCAL 名称。函数前置声明的参数不会泄漏到后续程序或其他函数中。
- 注释与字符串不参与声明收集。用户结构体方法不会被当作项目普通函数，成员调用不匹配其他文件中的同名普通函数。
- 脚本修改、创建、删除和重命名时刷新已打开的 ACSPL+ 脚本；库文件未保存的编辑也能更新索引查询与颜色缓存。

此索引用于编辑高亮，不映射控制器缓冲区，不检验 D-Buffer 的可见性；补全和快速文档仍采用当前文件名称收集。全局变量来源有冲突时优先保留变量分类，避免将数组着色为函数。新增 API 使用参考 JetBrains 的[文件索引文档](https://plugins.jetbrains.com/docs/intellij/file-based-indexes.html)及[高亮文档](https://plugins.jetbrains.com/docs/intellij/syntax-highlighting-and-error-highlighting.html)。

## 手册核对

核对《ACSPL+ Programmer's Guide》3.12 的相关完整页面：

- §3.3，第 53 页：省略作用域意味着 LOCAL，省略类型意味着 INT。
- §3.4.3，第 55 页：一维和二维数组用 `(expression)` 与 `(expression)(expression)` 索引。
- §3.8.2，第 66–68 页：函数签名、参数列表、普通缓冲区和 D-Buffer 函数的可见性。
- §3.8.3，第 69–71 页：按引用及数组参数，包含 `INT arr()`、`INT arr()(3)`。
- §3.8.4，第 71–72 页：函数内变量生命期及同名变量遮蔽。
- §3.8.6，第 72–73 页：不能在 DISP 的参数中调用用户函数。函数调用示例使用 `myFunc(arguments)`；手册没有省略括号的命令式调用规则。

## 自动测试

33 项测试通过，失败、错误、跳过均为 0：8 项原有 IDE 测试、6 项语义高亮 IDE 测试、5 项声明/作用域测试、7 项词法测试、4 项补全测试、3 项资料库与文档测试。

新增 IDE 测试读取平台实际文档高亮层的颜色键和设置后的前景色，验证：跨文件函数调用、严格大小写、GLOBAL 数组、当前文件变量及参数对函数名称的遮蔽、注释和字符串过滤、三个颜色选项、文件未保存的函数改名、文件删除、新增库文件及当前文件数组声明修改。

测试采用 headless IDE 编辑器夹具，未进行人工界面验收。测试报告位于 `build/reports/tests/test/index.html`。演示脚本为 `examples/project-colors.prg` 和 `examples/function-library.acspl`。

## 安装包

`buildPlugin` 和 `verifyPluginStructure` 已通过。安装包为 `build/distributions/acspl-language-0.2.0.zip`，163402 字节，兼容范围为 `262` 至 `262.*`。已核对 ZIP/JAR 完整性、插件版本、索引和高亮扩展注册，以及 869 个离线资料条目。

SHA-256：

```text
663dc3c816e157ddbb73da12cb94943783d6a9e29236bf74f428b54ab7945cbd
```

## 兼容验证

Plugin Verifier 1.410 使用离线模式完成最终 ZIP 的验证，四个 IDE 均为 **Compatible**，没有报告弃用 API 使用：

| IDE | 平台构建号 | 结论 |
| --- | --- | --- |
| IntelliJ IDEA 2026.2 | 262.8665.258 | Compatible |
| CLion 2026.2.3.1 | 262.10968.176 | Compatible |
| PyCharm 2026.2.3 | 262.10968.92 | Compatible |
| Rider 2026.2.3.1 | 262.10968.170 | Compatible |

文件刷新使用 `DaemonCodeAnalyzer.restart(PsiFile, Object)`，记录刷新原因。测试、结构检查及兼容检查的最终执行均为成功；未配置兼容性问题忽略规则。验证器仍输出 IDE 产品清单中部分非本插件模块的 classpath 警告，四个目标的最终结论均通过。

报告位于 `build/reports/pluginVerifier/<IDE构建号>/plugins/acspl.language/0.2.0/`。最终构建日志位于 `.build-tools/semantic-validation-final.log`。
