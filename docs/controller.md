# Windows ACS 3.12 控制器开发环境

0.3.0 增加环境设置、下载编译、运行和控制器行调试；0.3.1 将控制器脚本源码固定为 ASCII；0.3.2 增加独立的保存到 Flash 按钮；0.3.3 将设置改为列表选择与表单填写；0.3.4 修正 C 库文件版本与 ADK/固件版本的混淆。这是预览功能：开发机没有安装 ACS SDK/ADK，没有连接 ACS 模拟器或实体控制器。已通过模拟导出 C 库的桥接测试和 IDE 自动测试；这不能替代真实 3.12 SDK 和固件的联调。

## 安装 SDK 与配置环境

1. 安装你取得授权的 **Windows ACS SDK/ADK 3.12**。使用模拟器时需要安装 ADK 的模拟器及相关驱动/运行组件。
2. 在 JetBrains 2026.2 IDE 安装 `build/distributions/acspl-language-0.3.4.zip`，打开本地项目。
3. 打开 **Settings → ACSPL+ Controller**，点击左侧 **添加环境**，单击列表中的环境，在右侧表单填写名称和 **x64 原生 C 库 DLL 的完整路径**，或使用 **浏览…** 选择 DLL。通常为 `ACSCL_x64.dll`，安装后可在 `C:\Windows\System32` 或 ADK 安装目录的 `Redist` 中查找。无需双击单元格。以 DLL 导出 `acsc_OpenCommEthernetTCP`、`acsc_OpenCommSimulator` 等接口为准，不要选择 `.lib` 或 .NET 托管程序集，也不要将 DLL 复制到插件中。
4. 连接选择 `SIMULATOR` 或 `ETHERNET`。以太网填写控制器地址、TCP 端口（默认 701）及超时（默认 3000 ms）。SDK 路径和连接方式可以为不同环境分别保存。
5. 源码固定为 **ASCII（U+0000–U+007F，其中 NUL 不允许）**，包括注释和字符串。不提供 GBK/UTF-8 编码选项；旧环境里的编码配置在升级后忽略。非 ASCII 字符会在查询状态、备份及下载之前被拒绝，并显示行号、列号及 Unicode 编码点；中文注释、中文字符串、全角空格、弯引号、emoji 等均不能下载。发现问题不会替换成 `?` 或自动删除。SDK DLL 路径和环境名称仍可包含中文，它们不属于脚本源码。
6. 在所选环境的文件映射区域点击 **添加映射**，单击映射列表中的项目，在旁边表单填写项目相对文件路径，例如 `examples/controller-demo.prg`，以及备用缓冲区编号。也可以使用 **选择文件…** 自动生成项目相对路径。路径用 `/` 分隔；同一环境内文件与缓冲区一对一。映射属于所选环境，改环境名称无需修改映射。新增映射预填第一个未使用的编号，使用前仍须确认该缓冲区适合当前控制器。
7. Apply 后打开 **View → Tool Windows → ACSPL+ Controller**，从列表选择环境。设置变化后需要重新连接。

编号允许 0–63，具体可用编号由控制器决定。本版不下载 D-buffer、不批量编译其他缓冲区、不配置轴或控制器启动程序。映射用于操作目标选择，尚未参与跨文件函数颜色索引的可见性判断。

环境保存在项目的 `.idea/acspl-controller.xml`。应用设置、打开项目和切换环境不会自动连接。Windows x64 的系统 PowerShell 5.1 负责隔离原生 SDK；普通插件构建不依赖 PowerShell、ACS SDK 或 C 编译器。macOS/Linux 仍可使用编辑功能，控制器连接需要 Windows x64。

设置表单支持直接输入，不需要双击。切换环境或文件映射会保留尚未应用的输入，包括未填完的数字；点击 Apply/OK 时才校验并保存。校验失败保留草稿和原有设置，Reset/取消恢复已保存设置。删除环境也会移除它所属的映射草稿；其他环境的映射保留。0.3.2 及更早版本的设置直接读取，环境 ID 和文件映射不变。

ADK、C 库与控制器固件有各自的版本号。`ACSCL_x64.dll` 的文件版本显示 `7.2.0.0`，不会因为不是 `3.12` 而被拒绝，也不据此推断安装包或固件版本。插件检查 x64 DLL 与所需导出接口，并在连接后通过 `#SI` 检查已识别的固件版本；连接日志分别显示 C 库文件版本和控制器信息。此前版本报 `This integration targets ACS SDK 3.12; DLL version is ...` 时，请升级到 0.3.4，保留原 DLL 路径。

## 在模拟器中验证

1. 打开 `examples/controller-demo.prg`，配置到一个备用缓冲区（例如 2）；示例没有轴或 I/O 操作。
2. 在控制器工具窗口点击 **连接**。插件只建立连接、设置超时并读取 `#SI` 系统信息；SDK 可以启动其模拟器。日志显示 C 库 DLL 文件版本及控制器信息，已识别的固件版本不是 3.12 时拒绝连接。
3. 点击 **下载并编译当前文件**，核对确认框中的环境、连接目标、文件和缓冲区。缓冲区处于运行或暂停状态时拒绝下载。编译会初始化变量、激活 ON 自动例程，可能产生自动例程行为。
4. 下载前先上传原脚本，并写入 `.acspl/backups/<环境 ID>/buffer-<编号>-<时间>-<唯一后缀>.prg`；备份失败则拒绝下载。备份保持控制器返回的原始字节，不自动重编码。此目录已加入本工程的 `.gitignore`。
5. 备份完成后，通过 SDK LoadBuffer 替换脚本，再执行 `#nC`。控制器编译错误显示代码、说明及行号；源文件仍与提交内容一致时，定位对应行。
6. 编译完成后重新上传源码核对。文件、映射或控制器源码变化后，运行/调试都要求重新下载编译。此版本严格比较换行规范化后的全文；若 SDK 返回的源码含额外服务行或其他改写，将拒绝运行，需要真实 SDK 联调后处理。
7. 点击 **运行当前文件**，核对执行目标后运行。缓冲区状态按钮返回控制器状态；停止按钮仅停止当前映射的缓冲区。

下载编译、执行和 Flash 保存均由用户主动触发。连接中断后不重试下载、运行、调试或 Flash 保存命令；断开连接不会发送停止命令，实体控制器中的程序可能继续运行。SDK 关闭模拟器连接时，模拟器本身的退出行为由 SDK 决定。

## 保存当前程序到 Flash

1. 连接所选环境，打开已配置缓冲区映射的 ACSPL+ 文件，先完成 **下载并编译当前文件**。下载编译只更新程序缓冲区，不会自动保存到 Flash。
2. 停止该缓冲区；有 Debug 会话时，先在 Debug 窗口停止本次调试。点击工具窗口中的 **保存到 Flash**，核对确认框里的环境、连接目标、文件及缓冲区。保存会覆盖该缓冲区先前的 Flash 程序；下次上电会加载它，包含 `AUTOEXEC` 时可能自动执行。
3. 插件再次检查 ASCII 源码、本次连接的成功编译记录、文件及映射、上传的控制器源码和缓冲区状态。文件/映射变更、外部源码修改、未编译、运行或暂停状态均拒绝保存，需要先停止并重新下载编译。
4. 检查通过后发送 `#SAVEPROG <编号>`，例如 `#SAVEPROG 2`。只保存该缓冲区的程序，不发送无编号的 `#SAVEPROG`、整体 `#SAVE`、参数保存、D-Buffer 保存、运行或重启命令。成功日志表示收到控制器确认，不表示完成了断电验证。

编译前生成的 `.acspl/backups` 是下载前的缓冲区源码备份，不是旧 Flash 内容的备份。保存期间超时或断线时，Flash 可能已经写入，插件不会自动重试；请先用厂商工具核实控制器状态。控制器的应用保护等限制返回为错误，插件不会自动解除保护或改用整体保存。

## 调试与变量查看

1. 在编辑器左侧 gutter 放置 ACSPL+ 行断点。断点行号从 IDE 的 0 起始转换为控制器的 1 起始。
2. 完成下载编译后点击 **调试当前文件**。本次会话重置当前缓冲区的断点并安装 IDE 中启用的断点，使用 `#nXD` 运行。仅调试这个缓冲区；其他文件的断点会提示未参与本次会话。
3. 标准 **Debug** 窗口显示当前缓冲区帧、暂停位置及变量。暂停发送 `#nP`；继续发送 `#nXD`；**Step Over** 发送 `#nXS`，执行一条控制器程序行。单步/继续只接受已暂停的缓冲区。静音断点会更新该缓冲区的断点，不自动继续运行。
4. Variables 列出当前文件声明的缓冲区级 GLOBAL/LOCAL 标量（最多 50 项）。Watches 可查看 `FPOS0`、`FPOS(0)`、`demoCounter`、`samples(3)`、`grid(1)(2)`、`MST(0).#MOVE` 等已识别变量及数字数组下标。
5. 缓冲区 LOCAL 变量使用 `?n:name`，GLOBAL/系统变量使用 `?name`。读取同时检查声明：函数、函数参数、函数内部变量、未知名称和对标量使用数组下标都拒绝查询。Watches 不支持赋值、函数调用、任意表达式或终端命令。
6. 调试期间修改源码后，拒绝继续和新增断点，暂停位置也不再绑定到已修改的文件行。停止本次调试，再重新编译。
7. Debug 的停止操作发送 `#nS` 并移除本次会话的断点。停止脚本不等于轴急停，也不保证所有自动例程停止；连接错误时 UI 结束会话不能证明控制器已停止。

首版没有函数调用栈、Step Into、Step Out、条件断点、日志断点、运行到光标、修改变量、函数参数/函数内部变量读取或控制器运动调试。Debug 入口位于控制器工具窗口，未添加独立 Run Configuration。日志显示连接、命令结果及错误，不捕获控制器的异步 `DISP` 消息。

## 接口依据与联调范围

脚本编译与调试指令依据仓库中的《ACSPL+ Commands & Variables Reference Guide》**3.12**：§6.2（第 812 页）、§6.3.5（第 829–833 页）、§6.3.6（第 833–836 页）、§6.4.1（第 836 页）。这些是终端指令，不是插入脚本的 ACSPL+ 指令。

桥接调用用户安装的 SDK 原生 C 库：`acsc_OpenCommSimulator`、`acsc_OpenCommEthernetTCP`、`acsc_CloseComm`、`acsc_SetTimeout`、`acsc_GetLastError`、`acsc_GetErrorString`、`acsc_Transaction`、`acsc_LoadBuffer`、`acsc_UploadBuffer`。公开 ABI 参考为 ACS 发布的 [SPiiPlus C Library Reference 3.13.01](https://manuals.plus/m/29fa774332e3d19e94cf7b93283ff1dc16183d37cd4e111ef478d67c7a2c2c17.pdf)，第 36–37 页介绍 DLL 和安装位置，第 43、45、49–51、64–65、69、75、84 页介绍桥接使用的接口；其修订记录包含 3.12。本地两份 3.12 手册并非 C 库参考，因此仍需用安装后的 **ADK 3.12 所附头文件和实际 DLL** 核对 ABI、服务行与 UploadBuffer 分块行为。DLL 有版本资源时只报告文件版本，无版本资源时明确显示元数据不可用。ACS 的 [ADK 3.12 Beta Release Notes](https://manuals.plus/m/e5f431acfe1e36a290904bccb8ef0a6218b20a137dc0c6e391ae3ce478734863) §1.2（第 4 页）将固件列为 3.12 Beta、C 库列为 7.1.13.0，说明不能将它们的版本编号直接比较；该资料不用于断言用户的 `7.2.0.0` DLL 属于某个具体 ADK 包。

Flash 保存命令的存在及上电恢复依据本地《ACSPL+ Programmer's Guide》3.12 §2.3.3.1、§2.3.7（第 37、39 页）。该版本没有详细列出单缓冲区保存语法；`#SAVEPROG <编号>` 的单缓冲区覆盖范围补充参考 [ACS 早期 Programmer's Guide，文档修订号 4.22](https://www.scribd.com/document/785569443/SPiiPlus-ACSPLplus-Programmers-Guide-V4-20) §4.3.2（第 4-39 页），不是 3.12 SDK 的实测结论。通过已有的 [acsc_Transaction](https://www.acsmotioncontrol.com/KC/Content/SW/SW_Clibrary/Document/acsc_Transaction.htm) 发送命令，桥接添加 ASCII 回车 `\r`，没有新增 SDK 导出依赖。实际 3.12 命令接受情况、保护模式和 Flash 断电保持仍待联调。

不调用已被后续 C 库移除的早期内部断点函数，也不随插件分发 SDK 或模拟器。原生库运行在独立进程中，使用 JSON 请求和 base64 源码字节传输；源代码、地址和控制器指令不会拼接成 PowerShell 命令。桥接脚本的 ExecutionPolicy Bypass 仅作用于该进程，不修改系统执行策略。

安装真实 SDK 后的待验证事项：模拟器启动/关闭、3.12 导出与调用约定、ASCII 源码的上传往返及服务行、错误回复、终端状态文本、断点行号与自动例程行为，以及具体型号支持的缓冲区编号。Flash 另需核对单缓冲区命令、保护模式、超时、其他缓冲区/参数不受影响及断电重载。先在 SDK 模拟器检查可用行为，再核对实体控制器的设备配置和目标缓冲区。

## 开发测试

普通构建：

```powershell
.\gradlew.bat test buildPlugin
```

核心控制器测试使用 Java 模拟传输。五个 Windows 原生桥接测试及一个完整 IDE 调试会话测试需要额外的测试 DLL；没有 `mockSdkDll` 参数时这六项明确跳过，不视为真实 SDK 通过。

可使用 Windows x64 MinGW GCC 和 windres 编译 `tools/mock_sdk.c` 与 `tools/mock_sdk.rc`，然后运行完整桥接测试（路径按本机调整）。版本资源将测试 DLL 的文件版本设为 `7.2.0.0`，专门检查 C 库与固件版本分离：

```powershell
New-Item -ItemType Directory -Path '.build-tools' -Force | Out-Null
& 'C:\Users\Deneb\AppData\Local\Programs\CLion\bin\mingw\bin\windres.exe' -i 'tools\mock_sdk.rc' -o '.build-tools\mock-sdk-res.o' -O coff
& 'C:\Users\Deneb\AppData\Local\Programs\CLion\bin\mingw\bin\gcc.exe' -shared -O2 -o '.build-tools\mock-acs-sdk.dll' 'tools\mock_sdk.c' '.build-tools\mock-sdk-res.o'
.\gradlew.bat test buildPlugin '-PmockSdkDll=D:\code\idea-ascpl-plugin\.build-tools\mock-acs-sdk.dll'
```

该 DLL 只模拟需要的 C 导出、程序字节上传下载、状态、Flash 程序快照及错误，不能连接或控制 ACS 设备，且不会打包。测试覆盖实际 Windows PowerShell → C# 委托 → x64 导出函数调用链、中文路径、ASCII 源码、非 ASCII 字节拒绝、引号与特殊字符、多块源码上传、编译运行调试序列、指定缓冲区的 Flash 命令、RAM 改动不改变保存快照，以及原生错误处理。`?MOCK_FLASH_*` 仅供测试 DLL 检查快照，不是厂商指令，插件不发送这些测试指令。Java 模拟传输另外验证其他缓冲区保持原样、保存前的拒绝路径、保护错误及保存成功但回复丢失时不重试。IDE 测试核对环境持久化、旧编码选项迁移、设置页、断点类型注册及真实 Debug 会话的源码行、变量读取、单步与停止；也检查点击不支持的步入后仍保持暂停状态。设置表单另有回归测试，检查连续修改检测保持文本和光标、草稿切换、校验失败保留输入、环境改名及增删映射。未进行人工 GUI 验收。
