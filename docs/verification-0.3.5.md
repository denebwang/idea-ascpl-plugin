# 0.3.5 SDK 连接句柄修正

验证日期：2026-10-12。构建使用 Java 25、Gradle Wrapper 9.8.1、IntelliJ Platform Gradle Plugin 2.19.0 和 IDEA 2026.2 SDK。

## 问题与修复

旧桥接把 ACS 连接句柄的数值 `0` 也判为失败，随后读取本来没有设置的 SDK 错误码，产生 `ACS SDK error 0: `。用户报告发生于 ETHERNET。ACS 的 C Library Reference §4.1.2、§4.1.4（第 43、45 页）将连接失败值定义为 `ACSC_INVALID (-1)`；ACS 的通信句柄不采用 Win32 空指针规则。参考资料为 ACS 发布的 [C Library Reference 3.13.01](https://manuals.plus/m/29fa774332e3d19e94cf7b93283ff1dc16183d37cd4e111ef478d67c7a2c2c17.pdf)，其修订记录包含 3.12。

- 连接判断、后续操作的连接状态检查、`CloseComm` 清理统一使用 `ACSC_INVALID (-1)`，允许数值为 0 的有效通信句柄。
- 超时设置、命令通信及程序传输的布尔结果仍必须成功；没有通过忽略错误码 0 来放行失败。
- 错误包含 SDK 接口名称；ETHERNET 打开失败另外包含目标地址和端口。原始错误码在读取错误说明前保存，缺少说明或错误码时明确提示。
- PowerShell JSON 错误回复使用底层异常消息，避免整段托管调用堆栈影响用户定位问题。
- 测试 DLL 返回零值模拟器句柄，另提供零值、非零值及超过 32 位范围的 ETHERNET 句柄；检查实际 `CloseComm` 收到的句柄。

未修复的桥接配合更新后的测试 DLL，10 项原生测试有 9 项失败，复现了用户报告的同一条 `MethodInvocationException` / `ACS SDK error 0: `。日志保存在 `.build-tools/zero-handle-before-fix.log`，原始 XML 保存在 `.build-tools/zero-handle-before-fix.xml`。

## 验证

修复后的全部 **74 项测试通过**，失败、错误、跳过均为 0：语言编辑测试 33 项，控制器引擎/协议测试 23 项，IDE 集成测试 8 项，Windows 原生桥接测试 10 项。

新增原生回归测试覆盖模拟器及 ETHERNET 的零值句柄、超时设置、通信、源码加载/上传和关闭，以及非零/64 位句柄、`-1` 打开失败、设置超时失败、失败但未提供错误码时的拒绝。普通构建没有 `mockSdkDll` 参数时，10 项原生桥接及 1 项完整 IDE 调试测试明确跳过。

`test`、`buildPlugin`、`verifyPluginStructure`、`verifyPlugin` 全部通过。日志为 `.build-tools/zero-handle-validation.log`，测试报告为 `build/reports/tests/test/index.html`。最终 ZIP/JAR CRC、版本和兼容范围、打包桥接资源与源码一致性、桥接/C 测试源码及全部五个演示脚本的 ASCII 检查通过；厂商/测试 DLL、可执行文件、PDF 及测试标记没有打包。

Plugin Verifier 1.410 的四款 IDE 检查均为 Compatible。

| IDE | 构建 | 结果 |
| --- | --- | --- |
| IntelliJ IDEA 2026.2 | IU-262.8665.258 | Compatible |
| CLion 2026.2.3.1 | CL-262.10968.176 | Compatible |
| PyCharm 2026.2.3 | PY-262.10968.92 | Compatible |
| Rider 2026.2.3.1 | RD-262.10968.170 | Compatible |

## 安装包

`build/distributions/acspl-language-0.3.5.zip`，243612 字节，兼容范围为 `262` 至 `262.*`。

SHA-256：

```text
d56fb65079e291fc96348c7d9e21294162b85af57cc33207b78f64a36202e3f6
```

通过“从磁盘安装插件”升级并按 IDE 提示重启，原 DLL 路径、ETHERNET 配置及文件映射可继续使用。

## 验证边界

已通过真实 Windows PowerShell → C# → x64 测试 DLL 调用链复现和修复该错误。用户仅补充了 ETHERNET 连接方式，没有提供可读取的实际 DLL、ADK 头文件或控制器地址；本次未连接真实 SDK 或实体控制器，不能据此断言实际设备通信、SDK 完整 ABI 和 Flash 断电保持已通过验证。仍然失败时应以 0.3.5 返回的接口名称和原始错误码继续定位。
