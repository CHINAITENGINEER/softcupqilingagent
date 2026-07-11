# CommandTemplateRegistry 模板工厂化实现复审与测试缺口分析

检查日期：2026-06-28

检查范围：
- `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java`
- `src/main/java/com/cup/opsagent/executor/CommandRunner.java`
- `src/main/java/com/cup/opsagent/executor/CommandSpec.java`
- `src/main/java/com/cup/opsagent/tool/builtin/AbstractBuiltinTool.java`
- `src/main/java/com/cup/opsagent/tool/builtin/*.java`
- `src/main/java/com/cup/opsagent/verifier/ServiceRestartVerifier.java`
- `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java`
- `src/test/java/com/cup/opsagent/executor/CommandRunnerTest.java`
- `src/test/java/com/cup/opsagent/verifier/ServiceRestartVerifierTest.java`

本轮只做静态复审和测试缺口分析，未运行测试，未修改业务代码。用户提供的当前测试结果为：`BUILD SUCCESS`，`Tests run: 69`，`Failures: 0`，`Errors: 0`。

## 1. 总体结论

### 是否发现 P0/P1

未发现 P0，也未发现 P1。

当前模板工厂化实现已经达到 MVP 安全边界：

- 生产代码中 `new CommandSpec(...)` 只出现在 `executor/CommandTemplateRegistry.java`。
- 6 个内置工具已经迁移到 typed factory，不再手写命令数组。
- `ServiceRestartVerifier` 已迁移到 `verifyServiceActive(...)` factory，不再直接构造 `CommandSpec`。
- `CommandTemplateRegistry` 的 factory 会先构造 `CommandSpec`，再通过 `checked(spec)` 调用 `matches(spec)` 自检。
- `CommandRunner` 执行前仍调用 `commandTemplateRegistry.matches(spec)`，保留 execution-time 二次防线。
- `checkPortUsage(int port, ...)` 复用 `NumericArgumentValidator`，并生成 canonical `":" + safePort`，不会生成 `:00022`、`:+22` 这类非规范端口。
- `getServiceStatus(...)` 和 `verifyServiceActive(...)` 复用 `ServiceNameValidator.extract(...)`，与 serviceName 校验规则保持一致。
- `ServiceRestartVerifier` 的 evidence 使用 `spec.command()` 记录 `systemctlCommand`，仍能准确记录实际验证命令结构。

### 是否建议继续 API 收口

建议继续，但可以分阶段推进。当前边界已经足够支撑 MVP，下一步重点不是再修明显漏洞，而是继续把命令执行 API 从：

```text
CommandRunner.run(toolName, CommandSpec)
```

逐步收口为：

```text
CommandRunner.run(toolName, templateId, args, options)
```

最终让 `CommandSpec` 成为 executor 内部对象，而不是工具层或 verifier 可直接依赖的公共执行输入。

## 2. 问题列表

### P0

无。

### P1

无。

### P2-1：通用 `build(...)` 对无参模板和参数模板的额外 args 采取忽略策略

文件/类/方法：

- `CommandTemplateRegistry.build(...)`

问题描述：

`build(...)` 将 `args == null` 归一化为 `Map.of()`，然后按 `templateId` 构造命令。对于无参模板：

- `GET_SYSTEM_LOAD`
- `GET_TOP_PROCESSES`
- `GET_OPEN_PORTS`

即使传入额外 args，也会被忽略。对于参数模板，除必需字段外的额外字段也会被忽略。

为什么是问题：

- 这不构成命令执行绕过，因为最终 command 仍由 factory 固定生成，并经过 `checked(spec)` 和 `CommandRunner.matches(spec)`。
- 但它会让 action schema 偏宽：Planner/LLM 可以传入未使用字段，审计和调试时容易造成“看起来有参数生效，实际被忽略”的语义混淆。
- 对安全系统来说，参数 schema 最好是 fail-closed：未知参数拒绝，而不是静默忽略。

最小修复建议：

- 为每个 templateId 定义允许的 args key 集合。
- `GET_SYSTEM_LOAD`、`GET_TOP_PROCESSES`、`GET_OPEN_PORTS` 要求 args 为空。
- `CHECK_PORT_USAGE` 只允许 `port`。
- `GET_SERVICE_STATUS`、`VERIFY_SERVICE_ACTIVE` 只允许 `serviceName`。
- 如果短期担心影响兼容，可先在测试里锁定当前行为，然后下一轮改为严格拒绝。

建议新增测试：

- `buildShouldRejectExtraArgsForNoArgTemplates`
- `buildShouldRejectUnexpectedArgsForCheckPortUsage`
- `buildShouldRejectUnexpectedArgsForServiceTemplates`

### P2-2：静态测试只禁止 tool/verifier 包 `new CommandSpec`，未覆盖全部生产代码

文件/类/方法：

- `CommandTemplateRegistryTest.shouldForbidNewCommandSpecInToolAndVerifierProductionCode`

问题描述：

当前静态测试扫描：

- `src/main/java/com/cup/opsagent/tool`
- `src/main/java/com/cup/opsagent/verifier`

并禁止其中出现 `new CommandSpec(`。但用户期望是“当前生产代码中 `new CommandSpec` 只应出现在 `executor/CommandTemplateRegistry.java`”。现有测试没有覆盖 `agent`、`planner`、`safety`、`api`、`audit` 等生产包。

为什么是问题：

- 当前静态扫描显示生产代码确实只有 `CommandTemplateRegistry.java` 有 `new CommandSpec(`。
- 但现有防回归测试没有完全锁住这个架构约束。
- 未来如果有人在 `agent` 或 `safety` 包中手写 `new CommandSpec(...)`，现有静态测试可能捕获不到。

最小修复建议：

- 新增或增强静态测试：扫描整个 `src/main/java`。
- 允许列表只包含：
  - `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java`
- 其他任何生产文件出现 `new CommandSpec(` 都失败。

建议新增测试：

- `shouldForbidNewCommandSpecOutsideCommandTemplateRegistryInProductionCode`

### P2-3：缺少 Spring context 装配测试

文件/类/方法：

- `AbstractBuiltinTool`
- 6 个内置工具构造器
- `ServiceRestartVerifier` 构造器
- `CommandRunner(CommandTemplateRegistry)`

问题描述：

`AbstractBuiltinTool` 现在持有 `CommandTemplateRegistry`，6 个工具构造器都新增了 `CommandTemplateRegistry` 参数，`ServiceRestartVerifier` 也新增了 registry 注入。静态看构造器注入是合理的，但当前未发现 `@SpringBootTest` 或 context loads 测试。

为什么是问题：

- 当前类都是 Spring bean，构造器注入如果漏 bean、循环依赖、测试 profile 缺少组件扫描，会在启动时失败。
- 本轮改造改变了多个 bean 的构造器签名，适合用一个轻量 context 测试锁住装配。
- 这不是运行时安全漏洞，但会影响交付稳定性。

最小修复建议：

- 增加一个最小 Spring context 测试，验证应用上下文能启动。
- 断言关键 bean 存在：
  - `CommandTemplateRegistry`
  - `CommandRunner`
  - 6 个内置工具
  - `ServiceRestartVerifier`

建议新增测试：

- `OpsAgentApplicationContextTest.contextLoads`
- `CommandTemplateBeans_shouldBeInjectable`

### P2-4：`CommandRunner` 构造器未显式拒绝 null registry

文件/类/方法：

- `CommandRunner.CommandRunner(CommandTemplateRegistry commandTemplateRegistry)`

问题描述：

`CommandRunner` 已移除无参构造，只保留注入式构造，这是正确方向。但构造器没有显式 `Objects.requireNonNull(commandTemplateRegistry)`。

为什么是问题：

- Spring 正常不会注入 null。
- 但手动构造或测试误用时，会在 `run(...)` 中出现 NPE。
- 对安全边界类来说，构造期 fail-fast 更清晰。

最小修复建议：

- 在构造器中增加 null 检查。

建议新增测试：

- `shouldRejectNullCommandTemplateRegistryInCommandRunnerConstructor`

### P2-5：`AbstractBuiltinTool` 未显式拒绝 null `CommandRunner` / `CommandTemplateRegistry`

文件/类/方法：

- `AbstractBuiltinTool.AbstractBuiltinTool(...)`

问题描述：

`AbstractBuiltinTool` 将 `commandRunner` 和 `commandTemplateRegistry` 保存为 protected final 字段，但没有显式 null 检查。

为什么是问题：

- Spring 正常注入不会传 null。
- 但该类是所有内置工具的基类，构造期 fail-fast 能减少后续 NPE。
- 这是健壮性建议，不是当前 P1 安全问题。

最小修复建议：

- 使用 `Objects.requireNonNull(...)`。

建议新增测试：

- 如果不想为抽象基类单独写测试，可在 Spring context 测试里覆盖正常装配即可。

### P2-6：`ToolExecutionResult` 仍依赖 stderr 文本表达失败原因

文件/类/方法：

- `CommandRunner.run(...)`
- `ToolExecutionResult`

问题描述：

当前失败类型包括：

- null spec
- shell wrapper denied
- template mismatch
- timeout
- IOException
- interrupted

这些都通过 `stderr` 字符串表达，没有结构化 failure code。

为什么是问题：

- MVP 可接受。
- 但后续 UI、审计、告警、统计会需要稳定的机器可读错误码。
- 单靠 stderr 文本容易被文案变更影响测试和审计规则。

最小修复建议：

- 暂不急于做。
- 当进入审批流、执行策略、告警统计阶段时，再给 `ToolExecutionResult` 增加 `failureCode` 或 `executionStatus`。

建议新增测试：

- 如果新增 failureCode，分别覆盖 `COMMAND_SPEC_MISSING`、`SHELL_WRAPPER_DENIED`、`TEMPLATE_MISMATCH`、`TIMEOUT`。

## 3. 测试覆盖评价

### 已覆盖的安全边界

当前测试已经覆盖较充分：

- typed factory 能构造 6 个 canonical command。
- 通用 `build(...)` 能构造 `CHECK_PORT_USAGE` 和 `GET_SERVICE_STATUS`。
- invalid build args 被拒绝：
  - missing port
  - out-of-range port
  - injected serviceName
- `matches(...)` 接受 6 个 canonical template command。
- `matches(...)` 拒绝 templateId 与命令错配。
- `matches(...)` 拒绝固定模板额外参数。
- `CHECK_PORT_USAGE` 拒绝：
  - 无冒号端口
  - `:0`
  - `:65536`
  - `:22;whoami`
  - `:+22`
  - `:00022`
- `GET_SERVICE_STATUS` 拒绝：
  - 缺少 `--`
  - 子命令变成 restart
  - 注入式 serviceName
- `VERIFY_SERVICE_ACTIVE` 拒绝：
  - 缺少 `--`
  - 注入式 serviceName
- serviceName 模板校验和 `ServiceNameValidator` 在样本集上一致。
- tool/verifier 包禁止 `new CommandSpec(`。
- `ProcessBuilder` 只允许在 `CommandRunner` 中出现。
- 生产代码禁止 `Runtime.getRuntime().exec` / `.exec(`。
- `CommandRunner` 继续阻断 shell wrapper。
- `CommandSpec` 防御性复制、拒绝 null/blank element、拒绝 null templateId。
- `CommandRunner.run(null)` 返回失败结果。
- `ServiceRestartVerifier` 对注入 serviceName、dash serviceName、缺少参数、restart execution failed 都不会执行 status check。
- `ServiceRestartVerifier` evidence 不暴露原始 stdout/stderr，并记录 `systemctlCommand`。

### 还缺的最小测试

建议新增：

- `buildShouldRejectExtraArgsForNoArgTemplates`
- `buildShouldRejectUnexpectedArgsForCheckPortUsage`
- `buildShouldRejectUnexpectedArgsForServiceTemplates`
- `shouldForbidNewCommandSpecOutsideCommandTemplateRegistryInProductionCode`
- `contextLoads`
- `shouldInjectCommandTemplateRegistryIntoAllBuiltinTools`
- `shouldInjectCommandTemplateRegistryIntoServiceRestartVerifier`
- `shouldRejectNullCommandTemplateRegistryInCommandRunnerConstructor`

### 静态测试是否需要增强

需要小幅增强。

当前静态测试已经很有价值，但建议扩大三类约束：

1. `new CommandSpec(` 只能出现在 `CommandTemplateRegistry.java`。
2. 生产代码中 `commandRunner.run(` 的第二个参数应来自 `commandTemplateRegistry.*` 或后续的新 `CommandRunner.run(templateId,args,options)` API。
3. 如果继续 API 收口，禁止工具和 verifier 层出现 `List.of("systemctl"...`、`List.of("ss"...`、`List.of("ps"...`、`List.of("uptime"...` 这类命令数组常量。

## 4. 架构建议

### 下一步是否做 `CommandRunner.run(templateId,args,options)`

建议做，但分两步。

当前已经完成：

```text
工具层 -> CommandTemplateRegistry factory -> CommandSpec -> CommandRunner.run(toolName, spec)
```

下一步可以新增：

```java
ToolExecutionResult run(
        String toolName,
        CommandTemplateId templateId,
        Map<String, Object> args,
        CommandExecutionOptions options
)
```

内部流程：

```text
CommandRunner.run(...)
-> registry.build(templateId, args, options.timeoutMs, options.outputLimitBytes)
-> registry.matches(spec)
-> ProcessBuilder
```

好处：

- 工具层不再接触 `CommandSpec`。
- `CommandRunner` 成为唯一执行入口和唯一 build 调用入口。
- 继续保留 `matches(spec)` 二次防线。

### `CommandSpec` 是否应变成 executor 内部对象

长期应该。

建议路径：

1. 保留 public `CommandSpec`，但静态测试禁止 executor 包外生产代码 `new CommandSpec(`。
2. 新增 `CommandRunner.run(toolName, templateId, args, options)`。
3. 工具和 verifier 迁移到新 API。
4. 将 `CommandRunner.run(toolName, CommandSpec)` 降级为 package-private，或仅保留给 executor 包内测试。
5. 最后考虑让 `CommandSpec` package-private。

这样不会一次性大改，也能逐步减少自由命令数组暴露面。

### `CommandExecutionOptions` 的最小设计

建议最小版本：

```java
public record CommandExecutionOptions(
        long timeoutMs,
        int outputLimitBytes
) {
    public CommandExecutionOptions {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("timeoutMs must be positive");
        }
        if (outputLimitBytes <= 0) {
            throw new IllegalArgumentException("outputLimitBytes must be positive");
        }
    }
}
```

短期只承载现有两个参数，避免过度设计。

后续可以扩展：

- `workingDirectoryPolicy`
- `environmentPolicy`
- `redactionPolicy`
- `killProcessTree`
- `runAsUser`
- `auditOutputMode`

### `ToolExecutionResult.failureCode` 是否值得现在做

不建议马上做，除非下一阶段要做 UI 告警或审计统计。

当前 MVP 的 `success=false + stderr reason` 还能支撑测试和演示。`failureCode` 更适合和下面几项一起做：

- `CommandExecutionOptions`
- 审计事件结构化 payload
- 前端错误展示
- 告警和指标统计

建议枚举草案：

```text
COMMAND_SPEC_MISSING
SHELL_WRAPPER_DENIED
TEMPLATE_MISMATCH
PROCESS_START_FAILED
COMMAND_TIMEOUT
COMMAND_INTERRUPTED
NON_ZERO_EXIT
```

## 5. 最小分阶段路径

### 阶段 1：补齐当前 factory 边界

- `build(...)` 严格拒绝未知 args。
- 静态测试扩大到整个 `src/main/java`，只允许 `CommandTemplateRegistry.java` 中 `new CommandSpec(`。
- 增加 Spring context 测试。
- 构造器增加 null fail-fast。

### 阶段 2：CommandRunner API 收口

- 新增 `CommandExecutionOptions`。
- 新增 `CommandRunner.run(toolName, templateId, args, options)`。
- 6 个工具和 `ServiceRestartVerifier` 改用新 API。
- 保留旧 `run(toolName, CommandSpec)` 作为二次兼容入口。

### 阶段 3：CommandSpec 内部化

- 静态测试禁止 executor 包外使用 `CommandSpec`。
- `CommandRunner.run(toolName, CommandSpec)` 改为 package-private。
- `CommandSpec` 最终改成 executor 包内部对象。

## 6. 复审结论

本轮模板工厂化实现已经把命令能力边界推进到一个很稳的 MVP 状态。工具和 verifier 不再手写命令数组，factory 自检和 CommandRunner 二次匹配形成了合理的双层防线。

当前没有 P0/P1。下一步最值得做的是小幅收紧通用 `build(...)` 的 args schema，增强静态架构测试覆盖全生产代码，然后再推进 `CommandRunner.run(templateId,args,options)` 和 `CommandExecutionOptions`。`ToolExecutionResult.failureCode` 可以稍后和审计/前端错误语义一起做，不必抢在当前模板边界收口之前。
