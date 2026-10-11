# 0.3.0 Windows 控制器开发环境预览

本记录保留 0.3.0 的历史实现。0.3.1 已将脚本源码固定为 ASCII，并取消可选编码；当前规则见[控制器指南](controller.md)。

验证日期：2026-10-10。使用 Java 25、Gradle Wrapper 9.8.1、IntelliJ Platform Gradle Plugin 2.19.0 和 IDEA 2026.2 SDK。ACS SDK/ADK 未安装，未连接 ACS 模拟器或实体控制器。

## 实现

- 项目环境设置：多个 SDK DLL/连接配置，SIMULATOR 或 ETHERNET，端口、超时、源码编码、文件到缓冲区映射。
- 控制器工具窗口：连接、断开、下载编译、运行、调试、状态及停止当前缓冲区。
- 独立 Windows PowerShell/C# SDK 桥接进程，只加载用户提供的 x64 原生 DLL；JSON 请求和 base64 源码字节不会作为 shell 代码执行。
- 下载前备份原脚本；状态不可识别、目标缓冲区活跃或备份失败时拒绝下载。运行/调试前核对文件、映射和控制器全文；不自动重试有副作用的指令。
- IDE XDebugger 行断点、暂停、继续、控制器行 Step Over、当前缓冲区帧及只读变量/Watches。声明解析区分数组与函数，拒绝函数调用、参数、函数内部变量、未知名称及输入命令。暂不提供调用栈、Step Into、Step Out 或运行到光标。
- 停止后清理会话断点和轮询；项目/服务释放时取消轮询并在后台关闭 SDK，避免 UI 被原生请求阻塞。

编译和调试终端指令依据本地 3.12《Commands & Variables Reference》§6.2、§6.3.5、§6.3.6、§6.4.1（第 812、829–836 页）。C API 的公开参考为 ACS 的 [C Library Reference 3.13.01](https://manuals.plus/m/29fa774332e3d19e94cf7b93283ff1dc16183d37cd4e111ef478d67c7a2c2c17.pdf)；尚未用实际 3.12 头文件/DLL 核对 ABI。完整使用说明与待联调项见[控制器指南](controller.md)。

## 自动测试

最终全部 52 项测试通过，失败、错误、跳过均为 0：

| 测试类别 | 数量 |
| --- | ---: |
| 原有语言、词法、补全、文档、声明与语义颜色测试 | 33 |
| 控制器引擎、协议、备份、状态、源码与变量检查 | 13 |
| IDE 环境设置、断点注册、完整 Debug 会话 | 3 |
| Windows 原生模拟 SDK 桥接 | 3 |

原生模拟库由 `tools/mock_sdk.c` 使用本机 CLion 的 Windows x64 MinGW GCC 编译，仅导出测试需要的 C 接口，不会连接 ACS 设备。测试通过 `-PmockSdkDll` 传入；普通构建不需要 C 编译器，缺少该参数时明确跳过 4 项涉及模拟 DLL 的测试。

核心测试覆盖：连接不自动执行、备份先于下载、GBK 中文字节往返、未知/活跃缓冲区拒绝、备份失败、编译错误及行号、编辑和外部源码变更、断点编号/去重/静音、单步/继续的状态限制、变量和数组/函数识别、指令注入拒绝、连接丢失不重试、不自动停止及固件不匹配。

桥接测试覆盖：真实 Windows 子进程、C# 原生委托、中文 DLL 路径、UTF-8 源码及特殊字符、多块上传、编译/运行/暂停/单步/继续/停止、SDK 错误。完整 IDE 会话使用该模拟 SDK 验证实际 XDebugger 注册、断点挂起、源文件和行号、变量求值、单步位置、不支持的步入状态恢复及停止清理。

测试报告：`build/reports/tests/test/index.html`。测试采用 headless IDE 夹具，未进行人工 GUI 验收。模拟导出库测试不能证明厂商 SDK ABI、驱动、服务行、编码、终端回复及实体设备行为已经通过。

## 安装包与兼容性

`buildPlugin`、`verifyPluginStructure` 通过。最终 ZIP 为 `build/distributions/acspl-language-0.3.0.zip`，231801 字节，兼容范围 `262` 至 `262.*`。ZIP/JAR 完整性、插件版本和控制器扩展注册已核对；SDK 桥接文本资源已打包，不包含 SDK DLL、测试 DLL、可执行文件或 PDF。

SHA-256：

```text
7f74955f1c16afe048c0fd1ac1e0a27bcfda8de38fed8469d195bedc46114b33
```

最终 ZIP 使用 Plugin Verifier 1.410 离线检查，四个 IDE 均为 **Compatible**，无弃用 API 使用报告。验证器提示部分宿主 SDK 布局项缺失，这些为宿主布局提示，未产生插件兼容性问题。

| IDE | 构建 | 结论 |
| --- | --- | --- |
| IntelliJ IDEA 2026.2 | IU-262.8665.258 | Compatible |
| CLion 2026.2.3.1 | CL-262.10968.176 | Compatible |
| PyCharm 2026.2.3 | PY-262.10968.92 | Compatible |
| Rider 2026.2.3.1 | RD-262.10968.170 | Compatible |

验证命令（依赖缓存后可离线执行）：

```powershell
.\gradlew.bat test buildPlugin verifyPluginStructure verifyPlugin '-PmockSdkDll=D:\code\idea-ascpl-plugin\.build-tools\mock-acs-sdk.dll' '-PverifyIdePaths=C:\Users\Deneb\AppData\Local\Programs\CLion,C:\Users\Deneb\AppData\Local\Programs\PyCharm,C:\Users\Deneb\AppData\Local\Programs\Rider' --offline --console=plain --no-configuration-cache
```

报告目录为 `build/reports/pluginVerifier`。控制器功能仍需安装真实 Windows SDK/ADK 3.12 后先完成模拟器联调，再验证具体控制器型号；本次未自动安装 SDK，也未向任何实体设备发送操作。
