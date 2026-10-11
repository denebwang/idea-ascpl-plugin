# 0.3.1 ASCII 源码限制

验证日期：2026-10-10。使用 Java 25、Gradle Wrapper 9.8.1、IntelliJ Platform Gradle Plugin 2.19.0 和 IDEA 2026.2 SDK。本机未安装厂商 ACS SDK/ADK，所有控制器测试使用测试导出 C 库。

## 变更

- 控制器源码固定使用 US-ASCII，包括注释与字符串。拒绝非 ASCII 字符及 NUL，不做替换或删除。提示首次错误的行号、列号、Unicode 编码点，并可在编辑器定位。
- 移除环境中的编码字段和设置页编码选项。旧项目 XML 的 GBK/UTF-8 设置在读取时忽略，环境名称、SDK 路径、连接配置和缓冲区映射保留。
- 编译、运行、调试均检查源码；编译时检查先于缓冲区状态查询、备份及下载。SDK 下载接口和原生桥接进程也拒绝非 ASCII 源码字节。
- 控制器上传源码按 ASCII 严格解码，非 ASCII 字节会阻止运行/调试。原脚本备份仍保存原始字节，避免损坏已有内容。
- 全部五个演示脚本通过 ASCII 字节检查，中文或排版标点注释已改为英文 ASCII 注释。编辑器可显示未完成的非 ASCII 内容，下载编译时拒绝；未添加新的语义诊断功能。

中文环境名称、SDK/文件路径、IDE 界面和中文指南不受脚本字符限制影响。SDK 桥接的 JSON 使用 UTF-8 传输路径等元数据，源码字节使用 base64 传输。

## 验证

全部 **56 项测试通过**，失败、错误、跳过均为 0。其中原有语言编辑测试 33 项，控制器引擎/协议测试 16 项，IDE 集成测试 4 项，Windows 模拟 SDK 桥接测试 3 项。

新增或调整的检查覆盖：

- 中文注释、中文字符串、非 ASCII 拉丁字符、U+0080、全角空格、emoji、BOM 和 NUL 被拒绝。
- 错误行列、CRLF/LF/CR 换行处理、emoji 的完整编码点及 ASCII 原文往返。
- 拒绝发生在任何状态查询、上传备份、下载和编译操作之前；已有缓冲区保持原样。
- 外部控制器源码出现非 ASCII 字节后，不发送运行或调试指令。
- 旧 GBK 配置项被忽略，环境与映射仍可读取；中文 SDK DLL 路径仍工作。
- 直接调用 Java SDK 字节下载接口时，拒绝非 ASCII/NUL 字节并保留原缓冲区。

另以 JSON 直接向 Windows 原生桥接进程提交包含 `0x80` 的源码，绕过 Java 校验，确认原生进程返回 ASCII 错误，随后上传的缓冲区仍为空。该检查仍使用 `tools/mock_sdk.c` 编译的测试 DLL，不涉及实体设备。

`buildPlugin` 和 `verifyPluginStructure` 通过。测试报告位于 `build/reports/tests/test/index.html`，构建验证日志为 `.build-tools/ascii-validation.log`。未进行人工 GUI 或真实 3.12 SDK/控制器联调。

## 安装包

文件：`build/distributions/acspl-language-0.3.1.zip`，234019 字节。

SHA-256：

```text
98fcbdd3ed7b358ffc78a93d6fe28c0e93df5baee99edbb85d2fec392e7876a9
```

兼容范围仍为 `262` 至 `262.*`。最终 ZIP 通过 Plugin Verifier 1.410 的四款 IDE 检查，均为 Compatible；没有报告弃用 API。

| IDE | 构建 | 结果 |
| --- | --- | --- |
| IntelliJ IDEA 2026.2 | IU-262.8665.258 | Compatible |
| CLion 2026.2.3.1 | CL-262.10968.176 | Compatible |
| PyCharm 2026.2.3 | PY-262.10968.92 | Compatible |
| Rider 2026.2.3.1 | RD-262.10968.170 | Compatible |

使用指南见[控制器开发环境](controller.md)，控制器功能的范围及模拟 SDK 构建方法沿用 0.3.0。
