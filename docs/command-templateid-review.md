# CommandTemplateId 本轮复审

检查日期：2026-06-26

检查范围：
- `src/main/java/com/cup/opsagent/executor/CommandTemplateId.java`
- `src/main/java/com/cup/opsagent/executor/CommandSpec.java`
- `src/main/java/com/cup/opsagent/executor/CommandRunner.java`
- 当前所有生产 `CommandRunner.run(...)` 调用点
- `ServiceNameValidator`
- `NumericArgumentValidator`
- `CommandRunnerTest`

## 总体结论

本轮 `CommandTemplateId` 修复方向正确，当前生产代码中的所有 `CommandRunner.run(...)` 调用点都已经带上模板 ID，并且 `CommandSpec` 已要求 `templateId` 非空。

未发现 P0 绕过问题。当前主要问题是 P1/P2 级别的可维护性和测试覆盖缺口：

- `CommandRunner` 内部复制了一份 `serviceName` 校验规则，和 `ServiceNameValidator` 存在未来漂移风险。
- `CommandRunner` 内部复制了一份 port 解析和范围规则，和 `NumericArgumentValidator` 存在未来漂移风险。
- `CommandRunnerTest` 已覆盖 shell wrapper 和一个通用 mismatch，但还没有对每个模板做系统性的 mismatch 测试。
- 模板结构规则目前写在 `CommandRunner` 里，短期可接受，长期建议抽到独立 `CommandTemplateRegistry`。

## 1. 模板校验是否覆盖所有 CommandRunner 调用点

结论：覆盖了当前所有生产调用点。

当前生产调用点如下：

| 调用点 | 当前 templateId | 命令结构 |
|---|---|---|
| `SystemLoadTool.execute` | `GET_SYSTEM_LOAD` | `["uptime"]` |
| `TopProcessesTool.execute` | `GET_TOP_PROCESSES` | `["ps", "-eo", "pid,user,comm,%cpu,%mem", "--sort=-%cpu"]` |
| `OpenPortsTool.execute` | `GET_OPEN_PORTS` | `["ss", "-tulpn"]` |
| `PortUsageTool.execute` | `CHECK_PORT_USAGE` | `["ss", "-tulpn", "sport", "=", ":" + port]` |
| `ServiceStatusTool.execute` | `GET_SERVICE_STATUS` | `["systemctl", "--no-pager", "status", "--", serviceName]` |
| `ServiceRestartVerifier.verify` | `VERIFY_SERVICE_ACTIVE` | `["systemctl", "is-active", "--", serviceName]` |

`CommandRunner.matchesTemplate(...)` 已覆盖 6 个 enum 分支：

- `GET_SYSTEM_LOAD`
- `GET_TOP_PROCESSES`
- `GET_OPEN_PORTS`
- `CHECK_PORT_USAGE`
- `GET_SERVICE_STATUS`
- `VERIFY_SERVICE_ACTIVE`

静态扫描结果中，生产代码只有 `CommandRunner` 内部直接使用 `ProcessBuilder`，没有发现其他 `ProcessBuilder`、`Runtime.exec` 或绕过 `CommandRunner` 的执行入口。

风险判断：

- 当前无 P0。
- 新增工具时只要继续通过 `CommandRunner.run(String, CommandSpec)`，就会被模板校验约束。
- 但未来如果有人新增直接 `ProcessBuilder` 或新增另一个 executor，就可能绕过模板，所以建议增加架构约束测试或静态扫描测试。

最小建议：

- 增加测试：扫描 `src/main/java`，断言 `ProcessBuilder` 只出现在 `CommandRunner.java`。
- 增加测试：扫描 `src/main/java`，断言不存在 `Runtime.getRuntime().exec`。

## 2. 是否存在未带 templateId 的执行路径

结论：当前生产路径未发现未带 `templateId` 的执行路径。

原因：

- `CommandSpec` record 构造参数已经包含 `CommandTemplateId templateId`。
- compact constructor 中显式拒绝 `templateId == null`。
- 当前所有生产 `new CommandSpec(...)` 都传入了 `CommandTemplateId`。
- `CommandRunner.run(...)` 只接收 `CommandSpec`，没有保留旧的 `run(toolName, List<String>)` 或旧三参 `CommandSpec(command, timeout, outputLimit)`。

剩余风险：

- `CommandRunner.run(nullSpec)` 会 NPE，但这不是 templateId 绕过，只是健壮性问题。
- 测试中的 `StubCommandRunner extends CommandRunner` 可以覆盖 `run(...)`，这是测试替身，不构成生产绕过。

最小建议：

- 可选增加 `CommandRunner.run` 对 `spec == null` 的显式失败处理，返回 `ToolExecutionResult(success=false, stderr="command spec is required")`。
- 更重要的是增加静态测试，防止未来出现新的无模板执行 API。

## 3. serviceName 模板校验是否和 ServiceNameValidator 漂移

结论：当前行为基本一致，但存在明确漂移风险。

当前 `ServiceNameValidator` 规则：

- 必须是非空字符串。
- 拒绝 shell 注入字符：`;`、`&&`、`||`、`|`、反引号、`$(`、`>`、`<`、换行。
- 拒绝 `-` 开头。
- 拒绝 `/` 和 `..`。
- 必须匹配 `^(?!-)[a-zA-Z0-9_.@-]{1,80}$`。

当前 `CommandRunner.isSafeServiceName(...)` 规则：

- 非 null。
- 不包含 `/`。
- 不包含 `..`。
- 匹配 `^(?!-)[a-zA-Z0-9_.@-]{1,80}$`。

现状判断：

- 以当前字符集看，`CommandRunner` 的 regex 已经间接拒绝大多数注入字符、空白和换行。
- 但它没有复用 `ServiceNameValidator`，而是复制了正则。
- 如果未来 `ServiceNameValidator` 放宽或收紧 systemd unit name，例如允许更完整的 template instance 形式，或者增加额外 denylist，`CommandRunner` 很容易忘记同步。

严重等级：P1。

为什么是问题：

- Policy 层和执行层的参数边界不应各维护一份规则。
- 执行层模板校验是最后一道防线，它和 Policy 层漂移时，会出现“Policy 允许但模板拒绝”或“Policy 拒绝但底层模板曾经可接受”的维护混乱。
- 长期看，这类漂移会让安全测试矩阵变得难维护。

最小修复建议：

- 将 `CommandRunner.isSafeServiceName(String)` 改为调用 `ServiceNameValidator.validate(serviceName).isEmpty()`。
- 或者把 serviceName 校验抽成更底层的 `CommandArgumentValidator`，由 `ServiceNameValidator` 和模板校验共同复用。

建议新增测试：

- `GET_SERVICE_STATUS` 拒绝 `["systemctl", "--no-pager", "status", "--", "-Htcp"]`。
- `GET_SERVICE_STATUS` 拒绝 `["systemctl", "--no-pager", "status", "--", "nginx;whoami"]`。
- `GET_SERVICE_STATUS` 拒绝 `["systemctl", "--no-pager", "status", "--", "../nginx"]`。
- `VERIFY_SERVICE_ACTIVE` 拒绝 `["systemctl", "is-active", "--", "-Htcp"]`。
- `VERIFY_SERVICE_ACTIVE` 拒绝 `["systemctl", "is-active", "--", "nginx$(whoami)"]`。
- 增加一个参数化测试，使用同一批 serviceName 样本同时断言 `ServiceNameValidator` 和模板校验结果一致。

## 4. port 模板校验是否和 NumericArgumentValidator 漂移

结论：当前对现有工具生成的命令是安全的，但也存在漂移风险。

当前 `NumericArgumentValidator.integerInRange(...)`：

- 接受 `Byte`、`Short`、`Integer`、`Long`、`BigInteger`。
- 拒绝 `Double`、`Float`、`BigDecimal`、字符串。
- 检查范围。

当前 `CommandRunner.matchesPortUsage(...)`：

- 要求命令前 4 段固定为 `["ss", "-tulpn", "sport", "="]`。
- 要求第 5 段以 `:` 开头。
- 使用 `Integer.parseInt(portArgument.substring(1))`。
- 检查 `1 <= port <= 65535`。

现状判断：

- 对 `PortUsageTool` 当前生成的 `":" + port` 来说，模板校验是有效的。
- 但 `CommandRunner` 已经从“原始参数校验”转成了“命令字符串反解析”，所以必然和 `NumericArgumentValidator` 有两份规则。
- `Integer.parseInt("+22")` 会接受 `+22`，所以模板层可能接受 `":+22"`。这不是当前工具会生成的命令，也不是直接 shell 注入，但模板语义不够严格。

严重等级：P2，若长期保留手写命令数组则升为 P1。

为什么是问题：

- port 的业务规则应该只有一处来源。
- 一旦未来端口参数支持字符串、范围表达式或其他格式，模板层和 Policy 层可能出现不一致。
- 模板层接受 `":+22"` 这类非规范表示，会降低模板白名单的“精确形状”价值。

最小修复建议：

- 短期：将 `matchesPortUsage` 改为先用正则限制 `^:[0-9]{1,5}$`，再解析范围。
- 中期：抽出 `PortArgumentValidator`，同时服务 `NumericArgumentValidator` 和模板校验。
- 长期：不要让调用方传 `":22"` 这种命令片段，改为 `CommandTemplateRegistry.build(CHECK_PORT_USAGE, args)`，由模板工厂生成 `":22"`。

建议新增测试：

- `CHECK_PORT_USAGE` 拒绝 `["ss", "-tulpn", "sport", "=", "22"]`。
- `CHECK_PORT_USAGE` 拒绝 `["ss", "-tulpn", "sport", "=", ":0"]`。
- `CHECK_PORT_USAGE` 拒绝 `["ss", "-tulpn", "sport", "=", ":65536"]`。
- `CHECK_PORT_USAGE` 拒绝 `["ss", "-tulpn", "sport", "=", ":22;whoami"]`。
- `CHECK_PORT_USAGE` 拒绝 `["ss", "-tulpn", "sport", "=", ":+22"]`，如果决定模板必须是规范数字。
- `CHECK_PORT_USAGE` 拒绝多余参数，例如 `["ss", "-tulpn", "sport", "=", ":22", "|", "cat"]`。

## 5. CommandRunnerTest 是否需要补充更多模板 mismatch 用例

结论：需要补。

当前已覆盖：

- 直接 shell wrapper：`sh -c`。
- 绝对路径 shell wrapper：`/bin/sh -c`。
- Windows shell wrapper：`cmd.exe /c`。
- PowerShell wrapper：`powershell -Command`。
- 一个通用模板 mismatch：`GET_SYSTEM_LOAD + python -c`。
- 一个 port 模板 allow shape。
- `CommandSpec` 拒绝 null templateId。

当前缺口：

- 没有逐一验证 6 个模板的 mismatch。
- 没有验证 `templateId` 和合法命令错配，例如 `GET_SYSTEM_LOAD + ["ss", "-tulpn"]`。
- 没有验证 serviceName 注入在模板层会被拒绝。
- 没有验证 `systemctl` 模板必须带 `--`。
- 没有验证 `systemctl status` 和 `systemctl is-active` 不能互换。
- 没有验证 port 的边界和非规范 port 字符串。

建议优先补充的测试：

P1 必补：

- `shouldRejectCommandWhenTemplateIdDoesNotMatchKnownCommand`
- `shouldRejectServiceStatusWithoutDoubleDash`
- `shouldRejectServiceStatusWithRestartSubcommand`
- `shouldRejectVerifyServiceActiveWithoutDoubleDash`
- `shouldRejectServiceNameInjectionAtTemplateLayer`
- `shouldRejectServiceNameStartingWithDashAtTemplateLayer`
- `shouldRejectPortUsageWithOutOfRangePortAtTemplateLayer`
- `shouldRejectPortUsageWithInjectedPortAtTemplateLayer`

P2 建议补：

- `shouldRejectExtraArgumentsForFixedTemplates`
- `shouldRejectExtraArgumentsForSystemctlTemplates`
- `shouldRejectPlusSignedPortAtTemplateLayer`
- `shouldAllowAllKnownTemplateShapesWithoutTemplateMismatch`

测试实现建议：

- 如果继续直接调用 `CommandRunner.run(...)`，allow-shape 测试会尝试启动系统命令，可能受 Windows 或缺命令影响。
- 更稳妥的方式是将模板匹配逻辑抽为包内可测的 `CommandTemplateRegistry.matches(...)`，测试不需要真的执行 OS 命令。

## 6. 是否应该把模板结构规则从 CommandRunner 抽到独立 CommandTemplateRegistry

结论：应该，但可以分阶段做。

当前把模板规则写在 `CommandRunner` 里，MVP 可以接受，因为工具数量只有 6 个。但从安全架构看，`CommandRunner` 同时承担了太多职责：

- shell wrapper denylist；
- templateId 与 command 匹配；
- serviceName 校验；
- port 反解析；
- ProcessBuilder 执行；
- 超时处理；
- 输出截断；
- 结果封装。

这会让命令模板边界和执行引擎耦合在一起。后续模板增加时，`CommandRunner` 会越来越像一个安全策略大杂烩。

严重等级：P2 架构建议。

推荐演进路径：

### 阶段 1：最小抽取

新增 `CommandTemplateRegistry`：

- `boolean matches(CommandSpec spec)`
- 内部迁移现有 `matchesTemplate(...)`、`matchesPortUsage(...)`、`matchesServiceStatus(...)`、`matchesVerifyServiceActive(...)`
- `CommandRunner` 只调用 `commandTemplateRegistry.matches(spec)`

好处：

- 不改变工具调用方式。
- 不改变 `CommandSpec`。
- 立即让模板规则可单元测试。
- 降低 `CommandRunnerTest` 对本机命令存在性的依赖。

### 阶段 2：模板工厂化

扩展 `CommandTemplateRegistry`：

- `CommandSpec build(CommandTemplateId templateId, Map<String, Object> args, long timeoutMs, int outputLimitBytes)`
- 每个模板定义自己的参数 schema。
- 工具层不再手写 `List<String>`。

示例目标：

```java
commandRunner.run(NAME, commandTemplateRegistry.build(
    CommandTemplateId.CHECK_PORT_USAGE,
    Map.of("port", port),
    definition().timeoutMs(),
    definition().outputLimitBytes()
));
```

### 阶段 3：彻底收口自由命令数组

进一步改为：

```java
commandRunner.run(CommandTemplateId.CHECK_PORT_USAGE, args, executionOptions)
```

此时：

- `CommandRunner` 不接受外部传入的 `List<String>`。
- 命令数组只能由 registry 生成。
- 模板校验从“事后匹配”变成“源头生成”。

这是长期最理想的 Command Capability Boundary。

## 优先级清单

### P0

未发现。

### P1

1. `serviceName` 模板校验与 `ServiceNameValidator` 存在漂移风险。
   - 最小修复：`CommandRunner` 复用 `ServiceNameValidator.validate(...)`。

2. `CommandRunnerTest` 缺少系统性的模板 mismatch 测试。
   - 最小修复：为 6 个模板补充关键 mismatch 和注入样本。

### P2

1. `port` 模板校验与 `NumericArgumentValidator` 存在漂移风险。
   - 最小修复：模板层先用严格 digits regex，再解析范围。
   - 长期修复：模板工厂接收 typed port，不再反解析命令字符串。

2. 模板规则位于 `CommandRunner`，职责偏重。
   - 最小修复：抽出 `CommandTemplateRegistry.matches(spec)`。
   - 长期修复：`CommandTemplateRegistry.build(templateId, args)` 生成命令。

3. 缺少静态防回归测试。
   - 最小修复：测试 `ProcessBuilder` 只允许出现在 `CommandRunner`，禁止生产代码出现 `Runtime.exec`。

## 最小新增测试清单

建议先补这些：

1. `shouldRejectKnownCommandWithWrongTemplateId`
2. `shouldRejectExtraArgumentsForGetSystemLoad`
3. `shouldRejectTopProcessesWithExtraShellLikeArgument`
4. `shouldRejectOpenPortsWithUnexpectedFlag`
5. `shouldRejectPortUsageWithoutColon`
6. `shouldRejectPortUsageOutOfRange`
7. `shouldRejectPortUsageInjectedValue`
8. `shouldRejectServiceStatusWithoutDoubleDash`
9. `shouldRejectServiceStatusWithRestartSubcommand`
10. `shouldRejectServiceStatusInjectedServiceName`
11. `shouldRejectVerifyServiceActiveWithoutDoubleDash`
12. `shouldRejectVerifyServiceActiveInjectedServiceName`
13. `shouldKeepServiceNameValidatorAndTemplateValidatorConsistent`
14. `shouldKeepProcessBuilderInsideCommandRunnerOnly`
15. `shouldRejectRuntimeExecInProductionCode`

## 复审结论

本轮 `CommandTemplateId` 已经把“任意非 shell 命令也可能被执行”的风险明显压低了。当前没有发现未带模板 ID 的生产执行路径，也没有发现模板校验漏掉现有 `CommandRunner.run` 调用点。

下一步不建议继续在 `CommandRunner` 里堆更多模板规则。更好的方向是抽出 `CommandTemplateRegistry`，先做模板匹配集中化，再逐步演进到模板工厂化。这样可以把安全能力从“执行前校验命令数组”提升为“调用方根本不能构造自由命令数组”。
