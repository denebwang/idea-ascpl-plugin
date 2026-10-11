# 0.1.1 变量颜色修复

验证日期：2026-10-10。构建使用 Java 25、Gradle Wrapper 9.8.1、IntelliJ Platform Gradle Plugin 2.19.0 和 IDEA 2026.2 SDK。

## 修复内容

0.1.0 只为系统变量提供颜色键，自定义变量的 `IDENTIFIER` token 没有对应的可调颜色。0.1.1 新增 **User variable / identifier** 设置，使用独立的 `ACSPL_IDENTIFIER` 颜色键；系统变量继续使用 `ACSPL_SYSTEM_VARIABLE`。

设置路径：**Settings → Editor → Color Scheme → ACSPL+ → User variable / identifier**。取消 **Inherit values from**，启用 **Foreground** 后选择颜色，点击 **Apply**。

设置页预览和脚本编辑器采用相同映射。该项覆盖自定义变量、参数及其他普通标识符，包括标签和自定义函数名。

## 回归测试

22 项测试全部通过，失败、错误、跳过均为 0：8 项 IDE 编辑器测试、7 项词法测试、4 项补全测试、3 项资料库与文档测试。

新增 IDE 编辑器测试验证：

- 设置页提供自定义变量颜色选项。
- `REAL Alpha` 声明处及 `Alpha = FPOS0` 使用处均读取设置的自定义颜色。
- 再次调整自定义变量颜色后，两个位置的高亮属性同步更新。
- `FPOS0` 使用独立的系统变量颜色，调整自定义变量颜色不会覆盖它。

测试采用 headless IDE 编辑器夹具，未进行人工界面验收。完整测试报告位于 `build/reports/tests/test/index.html`。

## 安装包

`buildPlugin` 和 `verifyPluginStructure` 已通过。安装包为 `build/distributions/acspl-language-0.1.1.zip`，144575 字节，兼容范围为 `262` 至 `262.*`。已核对插件版本、ZIP 完整性及 869 个离线资料条目。

SHA-256：

```text
c0c3841ee6b1ae758d6610a80cb167983a47695e80a49986e188f14babcfbb4c
```

## 兼容验证

Plugin Verifier 1.410 使用已缓存的 SDK 和离线模式完成验证，四个 IDE 的最终结论均为 **Compatible**：

| IDE | 平台构建号 | 结论 |
| --- | --- | --- |
| IntelliJ IDEA 2026.2 | 262.8665.258 | Compatible |
| CLion 2026.2.3.1 | 262.10968.176 | Compatible |
| PyCharm 2026.2.3 | 262.10968.92 | Compatible |
| Rider 2026.2.3.1 | 262.10968.170 | Compatible |

报告位于 `build/reports/pluginVerifier/<IDE构建号>/plugins/acspl.language/0.1.1/`。验证器仍输出 IDE 产品清单中部分非本插件模块的 classpath 警告；四个目标均通过检查，未配置兼容性问题忽略规则。
