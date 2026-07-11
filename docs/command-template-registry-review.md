# CommandTemplateRegistry 安全复审与测试缺口分析

检查日期：2026-06-26

检查范围：
- `src/main/java/com/cup/opsagent/executor/CommandTemplateId.java`
- `src/main/java/com/cup/opsagent/executor/CommandSpec.java`
- `src/main/java/com/cup/opsagent/executor/CommandRunner.java`
- `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java`
- `src/main/java/com/cup/opsagent/tool/builtin/*.java`
- `src/main/java/com/cup/opsagent/verifier/ServiceRestartVerifier.java`
- `src/main/java/com/cup/opsagent/safety/validation/ServiceNameValidator.java`
- `src/main/java/com/cup/opsagent/safety/validation/NumericArgumentValidator.java`
- `src/test/java/com/cup/opsagent/executor/CommandRunnerTest.java`
- `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java`

本轮只做静态复审和测试缺口分析，未运行测试，未修改业务代码。

## 1. 总体结论

### 是否发现 P0

未发现 P0。

当前生产代码中：

- 未发现绕过 `CommandRunner` 的生产命令执行路径。
- 未发现生产代码直接使用 `ProcessBuilder`、`Runtime.getRuntime().exec` 或其他命令执行 API 绕过 `CommandRunner`。
- 当前唯一 `ProcessBuilder` 使用点仍在 `CommandRunner` 内部。
- 所有生产 `CommandRunner.run(...)` 调用点都使用了 `CommandSpec(CommandTemplateId, command, timeout, limit)`。
- 6 个 `CommandTemplateId` 都已被 `CommandTemplateRegistry.matches(...)` 覆盖。

### 当前 CommandTemplateId 边界是否足够支撑 MVP

足够支撑第一阶段 MVP。

本轮改造已经把命令边界从“禁 shell wrapper + 参数数组执行”推进到“禁 shell wrapper + 命令模板白名单匹配”。对当前 6 个 MVP 工具来说，命令结构已经足够收敛：

- 固定命令必须完全相等。
- 端口命令必须匹配固定 `ss -tulpn sport = :<port>` 结构。
- `systemctl status` 和 `systemctl is-active` 都强制使用 `--`。
- `serviceName` 模板校验已复用 `ServiceNameValidator`。
- `CHECK_PORT_USAGE` 已拒绝 `:+22`、`:22;whoami`、越界端口和额外参数。

### 是否建议进入模板工厂化

建议进入，但分阶段推进。

当前仍由工具层手写 `List<String> command`，然后交给 `CommandTemplateRegistry` 做事后匹配。这个方案能支撑 MVP，但长期安全边界更理想的形态是：

```text
工具层只传 templateId + typed args
CommandTemplateRegistry 负责 build CommandSpec
CommandRunner 不再接收自由 List<String>
```

下一阶段建议先新增 `CommandTemplateRegistry.build(templateId, args, timeout, limit)`，保留现有 `matches(spec)` 做兼容和防御。

## 2. P0/P1/P2 问题列表

### P1：CommandSpec 未防御性复制 command，存在校验后列表被修改的 TOCTOU 风险

文件/类/方法：

- `src/main/java/com/cup/opsagent/executor/CommandSpec.java`
- `src/main/java/com/cup/opsagent/executor/CommandRunner.java#run`

问题描述：

`CommandSpec` 接收 `List<String> command` 后只检查非空，没有做 `List.copyOf(command)`。如果外部传入的是可变 List，理论上可以在 `CommandRunner` 完成 shell wrapper 检查和 `CommandTemplateRegistry.matches(...)` 后、`new ProcessBuilder(spec.command())` 前修改 command 内容。

为什么是问题：

- `CommandTemplateRegistry` 是命令执行前的安全边界。
- 安全边界校验的是 `spec.command()` 当前内容。
- `ProcessBuilder` 执行的也是 `spec.command()` 当前内容。
- 如果两次读取之间 command 被并发修改，就存在校验对象和执行对象不一致的 TOCTOU 风险。

当前实际风险：

- 当前生产工具都使用 `List.of(...)`，不可变，所以现有 MVP 调用点不受影响。
- 但 `CommandSpec` 是公共 record，未来新增工具或测试代码可能传入可变 List。

最小修复建议：

- 在 `CommandSpec` compact constructor 中增加防御性复制：
  - 校验 command 非空；
  - 校验每个元素非 null、非 blank；
  - `command = List.copyOf(command)`。
- 或在 `CommandRunner.run(...)` 开头复制一份本地 command，并将后续 denylist、template match、ProcessBuilder 都基于同一份不可变快照。

建议新增测试：

- `CommandSpec` 构造后修改原始 List，不应影响 `spec.command()`。
- `CommandSpec` 应拒绝 command 中的 null 元素。
- `CommandSpec` 应拒绝 command 中的空字符串或 blank 字符串。

### P2：CHECK_PORT_USAGE 模板仍接受非规范端口表示 `:00022`

文件/类/方法：

- `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java#matchesPortUsage`

问题描述：

当前端口参数正则是 `^:[0-9]{1,5}$`，随后用 `Integer.parseInt(...)` 检查范围。因此 `:00022` 会被接受。

为什么是问题：

- 这不是 shell 注入，也不会改变端口语义。
- 但它不是 `PortUsageTool` 当前实际生成的规范格式，因为工具层由 `Integer port` 生成 `":" + port`，不会产生前导零。
- 模板白名单的目标是尽量精确匹配“允许的命令形状”，接受 `:00022` 会让模板比工具真实输出更宽。

当前实际风险：

- 低。`PortUsageTool` 不会生成 `:00022`。
- 只有内部代码手写 `CommandSpec(CHECK_PORT_USAGE, List.of(..., ":00022"))` 时才会出现。

最小修复建议：

- 如果希望模板严格等于工具输出，将正则改为 `^:([1-9][0-9]{0,4})$`，再做 `1-65535` 范围检查。
- 如果接受前导零作为等价端口，也建议在注释或测试中明确这是有意允许。

建议新增测试：

- `shouldRejectPortUsageWithLeadingZeroIfCanonicalShapeRequired`
- 或 `shouldAllowPortUsageWithLeadingZeroWhenExplicitlyAccepted`，用测试锁定设计意图。

### P2：CommandRunner 有无参构造，生产环境可能没有使用 Spring 注入的 CommandTemplateRegistry bean

文件/类/方法：

- `src/main/java/com/cup/opsagent/executor/CommandRunner.java#CommandRunner()`
- `src/main/java/com/cup/opsagent/executor/CommandRunner.java#CommandRunner(CommandTemplateRegistry)`

问题描述：

`CommandRunner` 是 `@Component`，但同时存在无参构造和带 `CommandTemplateRegistry` 的构造。无参构造内部 `new CommandTemplateRegistry()`。

为什么是问题：

- 当前 `CommandTemplateRegistry` 无状态，所以不会造成实际安全绕过。
- 但如果未来 registry 增加配置、审计、环境模板、禁用模板、目标 OS 适配或外部规则，`CommandRunner` 可能绕过 Spring 容器里的 registry bean，使用自己 new 出来的默认实例。
- 这会削弱模板注册表作为中心安全组件的可替换性和可测试性。

最小修复建议：

- 删除生产无参构造，只保留 `CommandRunner(CommandTemplateRegistry commandTemplateRegistry)`。
- 测试里显式使用 `new CommandRunner(new CommandTemplateRegistry())`。
- 如果必须保留无参构造，至少将其限制为测试用途或增加注释说明。

建议新增测试：

- Spring 上下文测试：断言 `CommandRunner` 使用容器注入的 `CommandTemplateRegistry` bean。

### P2：CommandRunner.run 对 null spec 和异常模板输入的健壮性不足

文件/类/方法：

- `src/main/java/com/cup/opsagent/executor/CommandRunner.java#run`
- `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java#matchesPortUsage`

问题描述：

`CommandRunner.run(...)` 开始即调用 `spec.command()`，如果 `spec == null` 会 NPE。`CommandTemplateRegistry.matchesPortUsage(...)` 对 portArgument 为 null 的情况也会 NPE。

为什么是问题：

- 当前生产调用点都不会传 null spec 或 null command element。
- 但命令执行入口属于安全边界，异常输入最好返回失败结果，而不是抛出运行时异常。
- 对安全系统来说，失败应可审计、可解释、可测试。

最小修复建议：

- `CommandRunner.run(...)` 开头显式处理 `spec == null`，返回失败的 `ToolExecutionResult`。
- `CommandSpec` 拒绝 command 中的 null/blank 元素。
- `CommandTemplateRegistry.matchesPortUsage(...)` 对 `command.get(4)` 为 null 时返回 false。

建议新增测试：

- `CommandRunner` 对 null spec 返回失败结果。
- `CommandSpec` 拒绝 null command element。
- `CommandSpec` 拒绝 blank command element。
- `CommandTemplateRegistry` 对 null port argument 返回 false。

### P2：模板测试已明显增强，但仍缺少“所有合法模板正例”和“工具命令与模板一致性”测试

文件/类/方法：

- `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java`
- `src/test/java/com/cup/opsagent/executor/CommandRunnerTest.java`
- `src/main/java/com/cup/opsagent/tool/builtin/*.java`
- `src/main/java/com/cup/opsagent/verifier/ServiceRestartVerifier.java`

问题描述：

`CommandTemplateRegistryTest` 已覆盖很多 mismatch 和注入样本，但还缺少几个正向和集成一致性测试：

- 6 个模板的标准合法命令全部应 matches true。
- 每个工具实际构造的 command 应与对应 templateId 匹配。
- `ServiceRestartVerifier` 构造的 verify command 应与 `VERIFY_SERVICE_ACTIVE` 匹配。
- `CommandRunnerTest` 中 `shouldAllowKnownPortUsageTemplateShape` 会真实调用 `ss`，它只检查 stderr 不包含 template mismatch，并不适合作为模板正例测试。

为什么是问题：

- mismatch 测试能证明危险命令被拒绝。
- 正例测试能证明模板和工具不会因为未来调整而悄悄漂移。
- 工具层仍手写 `List<String>`，所以“工具命令与模板一致性”尤其需要测试锁住。

最小修复建议：

- 在 `CommandTemplateRegistryTest` 增加标准模板正例参数化测试。
- 为工具层新增轻量 stub `CommandRunner`，捕获 `CommandSpec`，断言 templateId 和 command。
- 或进入模板工厂化，让工具层不再手写命令数组，从根上消除这类漂移。

建议新增测试：

- `shouldAllowAllCanonicalTemplateCommands`
- `SystemLoadTool_shouldUseGetSystemLoadTemplate`
- `TopProcessesTool_shouldUseGetTopProcessesTemplate`
- `OpenPortsTool_shouldUseGetOpenPortsTemplate`
- `PortUsageTool_shouldUseCheckPortUsageTemplate`
- `ServiceStatusTool_shouldUseGetServiceStatusTemplate`
- `ServiceRestartVerifier_shouldUseVerifyServiceActiveTemplateId`

## 3. 已覆盖测试清单

当前测试已经覆盖的安全边界：

### CommandRunnerTest 已覆盖

- 直接 shell wrapper 被阻断：`sh -c`。
- 绝对路径 shell wrapper 被阻断：`/bin/sh -c`。
- Windows shell wrapper 被阻断：`cmd.exe /c`。
- PowerShell wrapper 被阻断：`powershell -Command`。
- 非模板命令被阻断：`GET_SYSTEM_LOAD + python -c`。
- `CommandSpec` 拒绝 null `templateId`。
- `CHECK_PORT_USAGE` 的一个合法 shape 不会触发 template mismatch。

### CommandTemplateRegistryTest 已覆盖

- 已知命令与错误 templateId 错配会被拒绝。
- `GET_SYSTEM_LOAD` 拒绝额外参数。
- `GET_TOP_PROCESSES` 拒绝额外 shell-like 参数。
- `GET_OPEN_PORTS` 拒绝意外 flag。
- `CHECK_PORT_USAGE` 拒绝没有冒号的端口。
- `CHECK_PORT_USAGE` 拒绝 `:0` 和 `:65536`。
- `CHECK_PORT_USAGE` 拒绝 `:22;whoami`。
- `CHECK_PORT_USAGE` 拒绝 `:+22`。
- `GET_SERVICE_STATUS` 拒绝缺少 `--`。
- `GET_SERVICE_STATUS` 拒绝替换为 restart 子命令。
- `GET_SERVICE_STATUS` 拒绝注入式 serviceName。
- `VERIFY_SERVICE_ACTIVE` 拒绝缺少 `--`。
- `VERIFY_SERVICE_ACTIVE` 拒绝注入式 serviceName。
- serviceName 模板校验和 `ServiceNameValidator` 在一组样本上保持一致。
- 静态架构测试：`ProcessBuilder` 只允许出现在 `CommandRunner`。
- 静态架构测试：生产代码中禁止 `Runtime.getRuntime().exec` / `.exec(`。

### 代码层已覆盖的安全点

- `CommandTemplateRegistry` 复用 `ServiceNameValidator.validate(...)`。
- `CHECK_PORT_USAGE` 使用严格 `^:[0-9]{1,5}$`，拒绝 `:+22` 和注入字符。
- `systemctl status` 模板固定为 `systemctl --no-pager status -- <serviceName>`。
- `systemctl is-active` 模板固定为 `systemctl is-active -- <serviceName>`。
- `CommandRunner` 在模板校验前先阻断 shell wrapper。

## 4. 未覆盖测试清单

建议新增的最小测试：

### P1 必补

1. `CommandSpec_shouldDefensivelyCopyCommandList`
2. `CommandSpec_shouldRejectNullCommandElement`
3. `CommandSpec_shouldRejectBlankCommandElement`

### P2 建议补

4. `CommandRunner_shouldReturnFailureForNullCommandSpec`
5. `CommandTemplateRegistry_shouldReturnFalseForNullPortArgument`
6. `CommandTemplateRegistry_shouldAllowAllCanonicalTemplateCommands`
7. `CommandTemplateRegistry_shouldRejectPortUsageWithLeadingZeroIfCanonicalShapeRequired`
8. `SystemLoadTool_shouldUseGetSystemLoadTemplate`
9. `TopProcessesTool_shouldUseGetTopProcessesTemplate`
10. `OpenPortsTool_shouldUseGetOpenPortsTemplate`
11. `PortUsageTool_shouldUseCheckPortUsageTemplate`
12. `ServiceStatusTool_shouldUseGetServiceStatusTemplate`
13. `ServiceRestartVerifier_shouldUseVerifyServiceActiveTemplateId`
14. `SpringContext_shouldInjectCommandTemplateRegistryIntoCommandRunner`
15. `StaticArchitecture_shouldForbidNewCommandRunnerInMainCodeOutsideConfiguration`

## 5. 架构建议

### 是否应该抽 CommandTemplateRegistry.build(templateId, args, timeout, limit)

应该。

当前 `CommandTemplateRegistry.matches(spec)` 是“事后校验”。它能挡住错误命令，但工具层仍然可以手写 `List<String>`。下一阶段建议新增“模板工厂”能力：

```java
CommandSpec build(
    CommandTemplateId templateId,
    Map<String, Object> args,
    long timeoutMs,
    int outputLimitBytes
)
```

短期可以保留 `matches(spec)`：

- `build(...)` 用于新代码。
- `matches(...)` 用于 `CommandRunner` 执行前防御。
- 测试同时覆盖 build 的输出和 matches 的拦截。

### 是否应该让 CommandRunner 不再接受自由 List<String>

长期应该。

推荐分阶段：

1. 阶段一：保留 `CommandSpec.command`，但 `CommandSpec` 防御性复制并校验元素。
2. 阶段二：新增 `CommandTemplateRegistry.build(...)`，工具层改用 build。
3. 阶段三：`CommandRunner.run` 改为接收 `templateId + args + options`，不再公开接收自由 `List<String>` 的入口。
4. 阶段四：`CommandSpec` 变为执行层内部对象，外部工具无法直接构造命令数组。

### 是否应该增加 CommandExecutionOptions

建议增加，但不必抢在模板工厂之前。

当前 timeout 和 outputLimitBytes 放在 `CommandSpec` 中可以支撑 MVP。后续如果要生产化，建议抽：

```java
CommandExecutionOptions(
    timeoutMs,
    outputLimitBytes,
    workingDirectoryPolicy,
    environmentPolicy,
    runAsUser,
    killProcessTree,
    redactOutput,
    auditStdoutPolicy
)
```

这样可以把“命令结构”和“执行策略”分开：

- `CommandTemplateRegistry` 管命令结构和参数。
- `CommandExecutionOptions` 管执行环境和资源限制。
- `CommandRunner` 只负责执行、超时、输出截断和结果封装。

### 是否应该增加更强的静态架构测试

应该。

现有静态测试已经很好，建议继续增加：

- 生产代码中禁止 `new ProcessBuilder` 出现在 `CommandRunner` 之外。
- 生产代码中禁止 `Runtime.getRuntime().exec`。
- 生产代码中禁止新增 `CommandRunner.run(String, List<String>)` 之类无模板 API。
- 生产代码中禁止 `new CommandRunner()` 出现在非测试代码或非配置代码中。
- 生产工具类中禁止直接拼接 shell 连接符相关字符串，例如 `sh -c`、`bash -c`、`cmd /c`、`powershell -Command`。
- 如果进入模板工厂阶段，禁止工具层直接 `new CommandSpec(...)`。

## 最小分阶段路径

### 阶段 1：稳住当前 MVP 边界

- `CommandSpec` 防御性复制 command。
- `CommandSpec` 拒绝 null/blank command element。
- 明确 `:00022` 是否允许；如不允许，加 canonical port regex。
- 删除或限制 `CommandRunner` 无参构造，确保 Spring 注入 registry bean。
- 补齐上述 P1/P2 测试。

### 阶段 2：模板工厂化

- 新增 `CommandTemplateRegistry.build(...)`。
- 先改 6 个 MVP 工具和 `ServiceRestartVerifier` 使用 build。
- `CommandRunner` 继续执行前调用 `matches(spec)`，作为二次防线。
- 测试覆盖每个 templateId 的 build 输出。

### 阶段 3：关闭自由命令入口

- 将 `CommandSpec.command` 构造能力收口到 executor 包或 registry 内部。
- `CommandRunner` 对外只暴露 `run(toolName, templateId, args, options)`。
- 静态测试禁止工具层直接构造 `List<String> command` 或 `new CommandSpec(...)`。

## 复审结论

当前 `CommandTemplateRegistry` 改造是一次实质性安全增强，已经足够支撑第一阶段 MVP。它解决了上一轮最大的两个问题：模板逻辑集中化，以及 serviceName 模板校验和 `ServiceNameValidator` 漂移。

当前没有 P0。最值得优先修的是 `CommandSpec` command 列表防御性复制，这属于安全边界的基础硬化。随后建议进入模板工厂化，让工具层逐步停止手写 `List<String> command`，把命令能力边界从“执行前匹配”推进到“源头生成”。
