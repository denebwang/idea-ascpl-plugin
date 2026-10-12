# ACSPL+ JetBrains 语言插件

面向 IntelliJ IDEA、PyCharm、CLion、Rider **2026.2（平台 262）** 的 ACSPL+ 编辑插件，包含 Windows 控制器开发环境预览功能。语言资料依据 ACS Motion Control **ACSPL+ 3.12 / May 2022** 两份手册；厂商 PDF 和 SDK 不随开源仓库分发，普通构建及编辑功能不需要它们。

## 功能

- 自动识别 `.prg`、`.acspl` 脚本；分别高亮关键字、命令、内置函数、用户函数、系统变量、GLOBAL/LOCAL 变量、其他自定义名称、常量、数值、字符串、字符、注释和命令开关。
- 用户函数调用通过当前文件声明及项目内 `.prg`、`.acspl` 索引识别；变量和数组声明优先，避免将 `samples(i)`、`grid(i)(j)` 误判成函数。库文件修改、创建或删除后刷新已打开脚本的高亮。
- 支持 `PTP/rw0,100` 组合开关、`FPOS0` 数组后缀索引、`IN0.4` 位访问、`MST(0).#MOVE`、十六进制、二进制及科学计数法。
- 使用 IDE 的行注释操作切换 `!` 注释；颜色可在 **Settings → Editor → Color Scheme → ACSPL+** 中调整。
- 普通补全包含内置符号，以及当前文件声明的变量、参数、标签、函数、结构体类型、轴别名。输入 `#` 补全常量；命令后输入 `/` 补全该命令的开关，保留已输入的组合开关。
- 函数补全自动添加 `()` 并将光标放入括号；后面已有括号时不会重复插入。
- **View → Quick Documentation** 或悬停显示离线英文说明、签名、参数、开关和手册章节/页码；后缀索引变量关联其基础数组文档。
- 项目设置中的 **ACSPL+ Controller** 可定义多个 Windows ACS x64 SDK 环境、模拟器/以太网连接及文件 → 缓冲区映射；连接检查接受 3.12 / 3.13 固件，工具窗口提供下载编译、运行、保存到 Flash 和状态查询。
- 控制器设置使用环境列表与独立表单，直接填写并可浏览 DLL、选择项目脚本；映射在所属环境下编辑，改环境名称时自动保留关联。修改检测不会结束输入，切换选择也保留未应用的草稿。
- **保存到 Flash** 单独保存当前映射缓冲区的程序。保存前核对已编译源码与控制器内容，并确认覆盖目标；文件改动、运行/暂停状态或未编译时拒绝保存，普通下载编译不会自动写 Flash。
- 下载编译固定使用 ASCII，包含注释与字符串；发现非 ASCII 字符时显示行号、列号和 Unicode 编码点，拒绝下载，不做字符替换。
- SDK 连接按 `ACSC_INVALID (-1)` 判断失败，接受有效的零值连接句柄；错误日志显示具体 SDK 接口和错误码，避免将成功连接误报为 `ACS SDK error 0`。
- 调试使用 IDE 行断点和 Debug 窗口，支持暂停、继续、控制器行单步、缓冲区变量及只读 Watches。下载前自动备份，运行前核对源码；需要用户另行安装 SDK，真实 SDK/控制器联调尚未完成，详见[控制器指南](docs/controller.md)。

关键字、命令和内置函数不区分大小写；系统变量、常量以及当前文件名称按原始大小写匹配。候选保留手册或声明中的名称。

补全和快速文档仍采用当前文件名称收集。颜色识别支持项目内函数和 GLOBAL 变量，以及当前文件的缓冲区变量、函数参数和函数内变量；函数局部名称可以遮蔽外部同名名称。基础 PSI 允许未完成的代码，不做本地控制器语义诊断，也不提供重命名、符号跳转、格式化或结构视图。控制器编译错误可定位到对应源文件行。终端专用命令不进入脚本补全。

项目索引用于名称着色，不判断源文件对应哪个控制器缓冲区。手册中只有 D-Buffer 定义的函数无需声明即可供其他缓冲区使用，普通缓冲区函数只在本缓冲区可见；普通缓冲区定义的 GLOBAL 变量通常也需要在使用的缓冲区重新声明。插件不会把“识别出颜色”当作控制器可见性或程序正确性的判断。结构体成员调用尚不解析接收者类型，避免误匹配其他文件中的同名普通函数。IDE 索引期间暂时保留词法颜色，索引完成后自动应用语义颜色。

### 调整变量名颜色

在 **Settings → Editor → Color Scheme → ACSPL+** 中选择所需类别，取消 **Inherit values from**，启用 **Foreground** 并选择颜色，再点击 **Apply**。

| 颜色选项 | 对应名称 |
| --- | --- |
| User-defined function | 用户函数声明、定义和已识别的带括号调用 |
| GLOBAL variable | 显式 GLOBAL 变量、数组及已识别的项目内 GLOBAL 名称 |
| LOCAL variable / parameter | 显式 LOCAL、省略作用域的变量、函数参数和函数内变量 |
| System variable | `FPOS0`、`MST` 等系统变量 |
| User variable / identifier | 其他标识符、标签、轴别名及无法解析的名称 |

LOCAL 默认继承原有 **User variable / identifier** 颜色，用户函数默认继承 **Built-in function** 颜色。可取消继承后分别设置。升级后按 IDE 提示重启，等待项目索引完成；设置页预览也包含这三个语义类别。

### 用户函数与数组调用

用户函数的实参写在括号内，用逗号分隔，例如 `result = calculate(1, 2)` 或 `showPosition(0)`。手册未提供 `calculate 1, 2` 这种省略括号的调用形式。数组索引为 `samples(i)`、`grid(i)(j)`，插件根据声明区分；数组参数 `INT data()`、`INT data()(3)` 仍归入 LOCAL 变量颜色。

依据 3.12 手册 §3.8.6，自定义函数不能在 `DISP` 参数中直接调用：先写 `result = calculate(1, 2)`，再写 `DISP result`。相关规则见当前目录的《ACSPL+ Programmer's Guide》§3.3、§3.8，第 53、66–73 页。

## 安装

1. 从 [GitHub Release v0.3.6](https://github.com/denebwang/idea-ascpl-plugin/releases/tag/v0.3.6) 下载 `acspl-language-0.3.6.zip`，或自行构建后从 `build/distributions` 取得 ZIP。
2. 在 JetBrains 2026.2 IDE 中打开 **Settings → Plugins → 齿轮 → Install Plugin from Disk**，选择 ZIP。
3. 根据 IDE 提示启用插件或重启，打开 [examples/editing-demo.prg](examples/editing-demo.prg)。检查跨文件颜色时，将整个项目目录作为 IDE 项目打开，再打开 [examples/project-colors.prg](examples/project-colors.prg) 和 [examples/function-library.acspl](examples/function-library.acspl)。

如果 `.prg` 已经关联到其他语言，可在 **Settings → Editor → File Types** 中将 `*.prg` 关联到 ACSPL+，或使用 `.acspl`。插件不会主动覆盖用户已有的文件关联。

编辑示例包含运动命令，未针对你的设备配置；控制器流程请从不含轴和 I/O 命令的 [examples/controller-demo.prg](examples/controller-demo.prg) 和[控制器指南](docs/controller.md)开始，在 SDK 模拟器中验证。

## 构建与测试

需要 **JDK 25**。项目内包含 Gradle Wrapper 9.8.1，使用 IntelliJ Platform Gradle Plugin 2.19.0。首次构建需要访问 Gradle、Maven 和 JetBrains 官方依赖仓库，并下载 IDEA 2026.2 SDK。

PowerShell 示例（本机 CLion 内置 JBR 包含 Java 编译器）：

```powershell
$env:JAVA_HOME = 'C:\Users\Deneb\AppData\Local\Programs\CLion\jbr'
.\gradlew.bat test buildPlugin
```

macOS/Linux 使用 `./gradlew test buildPlugin`，并将 `JAVA_HOME` 指向 JDK 25。

也可以显式使用本地 **2026.2** IDE SDK，减少首次下载：

```powershell
.\gradlew.bat test buildPlugin '-PlocalIdePath=C:\Users\Deneb\AppData\Local\Programs\CLion'
```

默认使用 IDEA 2026.2 SDK，兼容范围为 `since-build=262`、`until-build=262.*`。`localIdePath` 仅改变本次构建及沙箱使用的 SDK，不扩大插件兼容范围。

### 兼容检查

```powershell
.\gradlew.bat verifyPlugin
.\gradlew.bat verifyPlugin '-PverifyIdePaths=C:\Users\Deneb\AppData\Local\Programs\CLion,C:\Users\Deneb\AppData\Local\Programs\PyCharm,C:\Users\Deneb\AppData\Local\Programs\Rider'
```

最低版本 IDEA 2026.2 始终加入验证；`verifyIdePaths` 可额外提供本地 IDE 路径，以逗号分隔（也接受 `|`，Windows 命令行建议使用逗号）。报告位于 `build/reports/pluginVerifier`。

SDK 和验证器依赖已经缓存时，可在命令末尾添加 `--offline`，避免验证期间访问 Marketplace；这使用验证器的[官方离线模式](https://github.com/JetBrains/intellij-plugin-verifier#common-options)。插件依赖 IDE 内置的平台、语言和 XDebugger 模块，不依赖某个产品的专有语言插件。编辑功能不要求安装 ACS SDK。

### IDE 沙箱

```powershell
.\gradlew.bat runIde '-PlocalIdePath=C:\Users\Deneb\AppData\Local\Programs\CLion'
```

沙箱中打开示例，检查高亮、行注释、补全和快速文档。`AcsplIdeTest` 使用平台编辑器测试夹具自动检查文件注册、函数括号插入、注释切换、声明缓存更新和文档扩展；`SemanticHighlightingTest` 检查跨文件函数、数组与作用域颜色以及库文件编辑后的更新。其他测试检查声明范围、词法重启、补全上下文和资料库来源。

控制器功能及 SDK 桥接测试方法见[控制器指南](docs/controller.md)。当前版本的测试、兼容性结论和安装包校验值见 [0.3.4 验证记录](docs/verification-0.3.4.md)；[0.3.3 验证记录](docs/verification-0.3.3.md)、[0.3.2 验证记录](docs/verification-0.3.2.md)、[0.3.1 验证记录](docs/verification-0.3.1.md)、[0.3.0 验证记录](docs/verification-0.3.0.md)、[0.2.0 验证记录](docs/verification-0.2.0.md)、[0.1.1 验证记录](docs/verification-0.1.1.md) 和 [0.1.0 验证记录](docs/verification.md) 保留历史结果。

## 维护资料库

普通构建与插件运行只读取已提交的 `src/main/resources/catalog/acspl-3.12.json`，不需要 PDF 或 Python。两份 PDF 不会打包到插件中。

需要重新提取时，先自行取得这两份厂商 PDF，按下列文件名放在项目根目录（不会提交到 Git），再执行：

- `ACSPL-Programmers-Guide.pdf`
- `ACSPL-Commands-Variables-Reference-Guide.pdf`

```powershell
python -m pip install -r tools/requirements.txt
python tools/extract_catalog.py
```

可用 `--pdf-dir` 指定其他目录，但输入仍须是这两份同名的 3.12 手册。提取器利用书签和章节识别符号，通过表格坐标关联开关、常量与说明；`tools/catalog_overrides.json` 保存人工核对后的修正，`tools/catalog_standard_structures.json` 补充仅在表格中列出的标准结构体成员，避免重新生成时丢失。

生成的 `docs/catalog-coverage.json` 记录已覆盖的符号章节和被排除的非符号章节。手册页码以 PDF 的 1 起始页码记录，此版本与页脚一致。该资料库用于编辑辅助，设备型号和固件限制以实际控制器及官方手册为准。

## 工程结构

- `lexer`：可增量重启的词法分析器及 token 类型。
- `psi`、`editor`：基础 PSI、语法高亮、颜色设置、行注释。
- `catalog`：共享的离线内置符号资源。
- `completion`：文件声明收集、缓存及补全规则。
- `documentation`：现代 Documentation Target API 和 HTML 文档生成。
- `controller`：环境设置、SDK 进程桥接、缓冲区操作、控制器工具窗口及 XDebugger 集成。
- `tools`：只在更新手册资料时使用的提取工具与修正数据。

插件 ID：`acspl.language`，当前版本：`0.3.4`。本项目尚未发布到 JetBrains Marketplace。

## 开源许可

原创插件代码、示例和项目文档使用 [MIT License](LICENSE)。第三方组件和厂商资料的说明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。源码仓库为 [denebwang/idea-ascpl-plugin](https://github.com/denebwang/idea-ascpl-plugin)，安装包在 [GitHub Releases](https://github.com/denebwang/idea-ascpl-plugin/releases) 中提供。
