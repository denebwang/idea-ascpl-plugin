# 0.3.4 C 库版本检查修正

记录整理日期：2026-10-12。测试和构建日志记录于 2026-10-11，使用 Java 25、Gradle Wrapper 9.8.1、IntelliJ Platform Gradle Plugin 2.19.0 和 IDEA 2026.2 SDK。

## 变更

此前插件把 C 库 DLL 文件版本与 ADK/固件的 `3.12` 直接比较，导致用户的 `7.2.0.0` 库被拒绝加载。C 库有独立的版本编号，不能通过其文件版本判断 ADK 或控制器固件版本。

- 移除 DLL 文件版本必须为 `3.12` 的条件；版本信息只用于连接日志，明确显示为 `ACS C Library`。
- 保留 Windows x64 DLL 检查和全部所需导出接口检查；连接后的控制器固件检查仍依据 `#SI` 返回信息。
- 设置页明确使用 `ACSCL_x64.dll`，指南补充 DLL 常见位置、版本区分和升级步骤。
- 测试 DLL 增加 `7.2.0.0` 文件版本资源，原生桥接回归测试确认 C 库版本与模拟返回的 `3.12.01.00` 固件信息分别显示，连接不会因两者编号不同而失败。

安装后保留原 DLL 路径、环境和文件映射，按 IDE 提示重启后重新连接即可。本次没有连接用户的 DLL、ACS 模拟器或实体控制器。

## 验证

全部 **69 项测试通过**，失败、错误、跳过均为 0：语言编辑测试 33 项，控制器引擎/协议测试 23 项，IDE 集成测试 8 项，Windows 原生桥接测试 5 项。测试 DLL 的实际 Windows 文件版本资源为 `7.2.0.0`；完整运行传入 `mockSdkDll`。普通构建未传入该参数时，5 项原生桥接和 1 项完整 IDE 调试测试明确跳过。

`test`、`buildPlugin`、`verifyPluginStructure` 和 `verifyPlugin` 均通过。日志为 `.build-tools/c-library-version-validation.log`，测试报告为 `build/reports/tests/test/index.html`。最终 ZIP/JAR CRC、插件版本和兼容范围、打包桥接资源中的修复及保留的 x64/接口检查均通过核对；DLL、PDF 和测试资源未打包。桥接脚本、新增版本资源及全部五个演示脚本通过 ASCII 检查。

最终 ZIP 通过 Plugin Verifier 1.410 的四款 IDE 检查，均为 Compatible，未报告内部或弃用 API。

| IDE | 构建 | 结果 |
| --- | --- | --- |
| IntelliJ IDEA 2026.2 | IU-262.8665.258 | Compatible |
| CLion 2026.2.3.1 | CL-262.10968.176 | Compatible |
| PyCharm 2026.2.3 | PY-262.10968.92 | Compatible |
| Rider 2026.2.3.1 | RD-262.10968.170 | Compatible |

## 安装包

文件：`build/distributions/acspl-language-0.3.4.zip`，243369 字节。兼容范围为 `262` 至 `262.*`。

SHA-256：

```text
82c33cb695b35842ef5c30b5f2332e18818d83df035894f660eeebaac4bc5f97
```

## 尚未验证

测试使用模拟导出的 C 库，不能替代用户实际 `7.2.0.0` DLL 的 ABI、依赖、真实控制器通信及 Flash 断电保持验证。本次修复仅取消错误的文件版本比较，未据此宣称所有 C 库版本均兼容。控制器集成继续作为预览功能，接口依据和联调范围见[控制器指南](controller.md)。
