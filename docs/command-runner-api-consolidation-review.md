# CommandTemplateRegistry Factory 边界收紧复审与 CommandRunner API 收口方案

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
- `src/test/java/com/cup/opsagent/OpsAgentApplicationContextTest.java`

本轮只做静态复审和方案设计，未运行测试，未修改业务代码。用户提供的当前测试结果为：`BUILD SUCCESS`，`Tests run: 72`，`Failures: 0`，`Errors: 0`。

## 1. 总体结论

### 是否发现 P0/P1

未发现 P0，也未发现 P1。

当前 factory 边界已经足够支撑 MVP：

- `CommandTemplateRegistry.build(...)` 已经按 templateId 做 fail-closed 参数 schema。
- 无参模板已经拒绝额外参数。
- `CHECK_PORT_USAGE` 只允许 `port`，并继续生成 canonical `:22` 形式。
- `GET_SERVICE_STATUS` 和 `VERIFY_SERVICE_ACTIVE` 只允许 `serviceName`，并复用 `ServiceNameValidator`。
- 全生产 `new CommandSpec(` 静态约束已经扩大到整个 `src/main/java`，只允许 `executor/CommandTemplateRegistry.java` 出现。
- `CommandRunner` 和 `AbstractBuiltinTool` 都已经对关键依赖做 null fail-fast。
- Spring context 测试已经覆盖 `CommandTemplateRegistry`、`CommandRunner`、6 个内置工具、`ServiceRestartVerifier` 的 bean 创建。

建议进入下一阶段 `CommandRunner` API 收口。

当前剩余问题不是“模板边界不安全”，而是 API 暴露面仍偏宽：

```text
工具/verifier -> CommandTemplateRegistry factory -> CommandSpec -> CommandRunner.run(toolName, spec)
```

下一阶段建议收口为：

```text
工具/verifier -> CommandRunner.run(toolName, templateId, args, options)
```

由 `CommandRunner` 内部调用 `CommandTemplateRegistry.build(...)`，并继续保留 `matches(spec)` 二次防线。

## 2. 问题列表

### P0

无。

### P1

无。

### P2-1：旧 `run(toolName, CommandSpec)` 仍是 public，API 暴露面还没有最终收口

文件/类/方法：

- `CommandRunner.run(String toolName, CommandSpec spec)`

问题描述：

当前工具和 `ServiceRestartVerifier` 已经不再直接 `new CommandSpec`，但仍可以拿到 `CommandSpec` 后调用 public `CommandRunner.run(toolName, spec)`。现有静态测试禁止了生产代码在 `CommandTemplateRegistry` 之外构造 `CommandSpec`，所以当前安全边界是稳的；但从 API 设计看，`CommandSpec` 仍是公开执行输入。

为什么是问题：

- 只要 `CommandSpec` 仍是 public 执行输入，未来新增生产代码就可能尝试绕过新 API。
- 当前靠静态测试防回归，长期更理想的是类型层面收口。

最小修复建议：

- 阶段 1：新增 `CommandRunner.run(toolName, templateId, args, options)`，保留旧 API。
- 阶段 2：工具和 verifier 全部迁移到新 API。
- 阶段 3：静态测试禁止 executor 包外调用 `run(..., CommandSpec)`。
- 阶段 4：将旧 API 降级为 package-private，最终让 `CommandSpec` 成为 executor 内部对象。

建议新增测试：

- `shouldForbidCommandRunnerRunWithCommandSpecOutsideExecutorPackageInProductionCode`

### P2-2：工具和 verifier 仍直接依赖 `CommandTemplateRegistry`

文件/类/方法：

- `AbstractBuiltinTool`
- 6 个内置工具构造器
- `ServiceRestartVerifier`

问题描述：

当前工具和 verifier 不再手写命令数组，这是本轮的核心改进。但它们仍直接注入并调用 `CommandTemplateRegistry` typed factory。

为什么是问题：

- 当前设计安全，因为 factory 会生成 canonical `CommandSpec`。
- 但下一阶段如果目标是“执行入口统一在 CommandRunner”，那么工具层不应该再知道 `CommandTemplateRegistry`。
- `CommandTemplateRegistry` 应逐步成为 `CommandRunner` 的内部依赖，而不是工具层依赖。

最小修复建议：

- 新增 `CommandRunner.run(toolName, templateId, args, options)` 后，工具和 verifier 只注入 `CommandRunner`。
- `AbstractBuiltinTool` 移除 `CommandTemplateRegistry` 字段。
- 静态测试禁止 `tool` 和 `verifier` 包 import / 使用 `CommandTemplateRegistry`。

建议新增测试：

- `shouldForbidCommandTemplateRegistryUsageInToolAndVerifierProductionCode`

### P2-3：`build(...)` fail-closed 参数 schema 已正确，但测试建议补齐所有 templateId 的额外参数拒绝

文件/类/方法：

- `CommandTemplateRegistry.build(...)`
- `CommandTemplateRegistryTest.shouldRejectExtraArgsWhenBuildingTemplates`

问题描述：

代码中 `requireOnlyArgs(...)` 已覆盖所有 templateId。当前测试覆盖了：

- `GET_SYSTEM_LOAD` 额外参数拒绝
- `CHECK_PORT_USAGE` 额外参数拒绝
- `GET_SERVICE_STATUS` 额外参数拒绝

但没有逐一覆盖：

- `GET_TOP_PROCESSES`
- `GET_OPEN_PORTS`
- `VERIFY_SERVICE_ACTIVE`

为什么是问题：

- 不是实现问题，是测试完整性问题。
- 参数 schema 是安全边界，建议用参数化测试覆盖全部 templateId。

最小修复建议：

- 将额外参数拒绝测试改为参数化，覆盖 6 个 templateId。

建议新增测试：

- `buildShouldRejectUnexpectedArgsForEveryTemplateId`

### P2-4：Spring context 测试覆盖 bean 存在，但未验证工具内部使用的是容器注入实例

文件/类/方法：

- `OpsAgentApplicationContextTest`

问题描述：

当前 `@SpringBootTest` 已确认关键 bean 存在，足以捕获构造器签名变化导致的启动失败。它没有进一步验证工具内部持有的 `CommandTemplateRegistry` 与容器里的 registry 是同一个实例。

为什么是问题：

- 当前生产代码通过构造器注入，且没有无参构造，风险很低。
- 如果后续有人在工具内部 `new CommandTemplateRegistry()`，bean 存在测试不一定能发现。
- 静态测试禁止 `new CommandSpec`，但没有禁止工具层 `new CommandTemplateRegistry`。

最小修复建议：

- 下一阶段 API 收口后，用静态测试直接禁止工具/verifier 使用 `CommandTemplateRegistry`，比反射检查字段更干净。

建议新增测试：

- `shouldForbidNewCommandTemplateRegistryInProductionCode`
- API 收口后：`shouldForbidCommandTemplateRegistryUsageInToolAndVerifierProductionCode`

### P2-5：非法参数目前在 factory 层抛异常，迁移到新 run API 后需要转成 `ToolExecutionResult`

文件/类/方法：

- `CommandTemplateRegistry.build(...)`
- 待新增 `CommandRunner.run(toolName, templateId, args, options)`

问题描述：

当前工具层在调用 factory 前大多已经做参数提取和校验，因此正常链路不会让 factory 异常冒泡。下一阶段如果工具直接调用 `CommandRunner.run(toolName, templateId, args, options)`，非法 args 会在 runner 内部调用 `build(...)` 时抛 `IllegalArgumentException`。

为什么是问题：

- `OpsTool.execute(...)` 不应因为参数非法而抛出未捕获异常。
- 命令执行入口更适合返回 `ToolExecutionResult(success=false, stderr=...)`，保持可审计、可验证、可响应。

最小修复建议：

- 新 run API 内部 catch `IllegalArgumentException`，返回失败 `ToolExecutionResult`。
- stderr 使用稳定前缀，例如 `invalid command template args: ...`。
- 仍不要执行 `ProcessBuilder`。

建议新增测试：

- `runWithTemplateShouldReturnFailureForInvalidArgs`
- `runWithTemplateShouldNotExecuteWhenBuildFails`

## 3. CommandRunner API 收口方案

### 3.1 `CommandExecutionOptions` 最小设计

建议新增最小 record：

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

可选便利方法：

```java
public static CommandExecutionOptions of(long timeoutMs, int outputLimitBytes)
```

不建议第一版就耦合 `ToolDefinition`，保持 executor 包独立。工具层可这样创建：

```java
new CommandExecutionOptions(definition().timeoutMs(), definition().outputLimitBytes())
```

后续可扩展字段：

- `killProcessTree`
- `environmentPolicy`
- `workingDirectoryPolicy`
- `redactionPolicy`
- `auditOutputMode`

### 3.2 新 run API 方法签名

最小侵入版本：

```java
public ToolExecutionResult run(
        String toolName,
        CommandTemplateId templateId,
        Map<String, Object> args,
        CommandExecutionOptions options
)
```

可选 typed overload 后续再加，不必第一阶段引入：

```java
public ToolExecutionResult run(
        String toolName,
        CommandTemplateId templateId,
        CommandTemplateArgs args,
        CommandExecutionOptions options
)
```

### 3.3 内部执行流程

建议流程：

```text
CommandRunner.run(toolName, templateId, args, options)
  1. startedAt = now
  2. 校验 templateId != null
  3. 校验 options != null
  4. try registry.build(templateId, args, options.timeoutMs, options.outputLimitBytes)
     catch IllegalArgumentException -> ToolExecutionResult(success=false, stderr="invalid command template args: ...")
  5. 调用现有 run(toolName, spec) 或内部 executeSpec(toolName, spec, startedAt)
```

为了避免 startedAt 重复计算，可以抽私有方法：

```java
private ToolExecutionResult runBuiltSpec(String toolName, CommandSpec spec, Instant startedAt)
```

### 3.4 如何保留 matches 二次防线

必须保留。

即使 `registry.build(...)` 内部已经 `checked(spec)`，`CommandRunner` 仍应保留：

```java
if (!commandTemplateRegistry.matches(spec)) {
    return result(toolName, false, "", "command does not match template: " + spec.templateId(), null, startedAt);
}
```

原因：

- 防御手工构造 `CommandSpec` 的旧 API。
- 防御 future bug。
- 防御测试替身或内部调用绕过 build。

### 3.5 如何迁移 6 个工具和 ServiceRestartVerifier

#### SystemLoadTool

从：

```java
commandRunner.run(NAME, commandTemplateRegistry.getSystemLoad(...))
```

到：

```java
commandRunner.run(NAME, CommandTemplateId.GET_SYSTEM_LOAD, Map.of(), options())
```

#### TopProcessesTool

```java
commandRunner.run(NAME, CommandTemplateId.GET_TOP_PROCESSES, Map.of(), options())
```

说明：`limit` 当前不进入命令模板，继续由业务层/输出处理层另行处理。

#### OpenPortsTool

```java
commandRunner.run(NAME, CommandTemplateId.GET_OPEN_PORTS, Map.of(), options())
```

#### PortUsageTool

保持现有工具内参数提取作为第一道校验：

```java
commandRunner.run(NAME, CommandTemplateId.CHECK_PORT_USAGE, Map.of("port", port), options())
```

#### ServiceStatusTool

保持 `ServiceNameValidator.extract(...)` 作为工具层第一道校验：

```java
commandRunner.run(NAME, CommandTemplateId.GET_SERVICE_STATUS, Map.of("serviceName", serviceName), options())
```

#### RestartServiceTool

当前仍不执行，无需迁移执行命令。

#### ServiceRestartVerifier

```java
commandRunner.run(
    name(),
    CommandTemplateId.VERIFY_SERVICE_ACTIVE,
    Map.of("serviceName", serviceName),
    new CommandExecutionOptions(VERIFY_TIMEOUT_MS, VERIFY_OUTPUT_LIMIT_BYTES)
)
```

如果需要 evidence 中记录 command，建议有两种选择：

1. `ToolExecutionResult` 增加 `templateId` / `command` evidence 字段，不建议现在做。
2. 在 verifier 中仍可调用 registry build 仅用于 evidence，不推荐，因为会重复 build。
3. 更好的折中：新 run API 返回的 `ToolExecutionResult` 暂不包含 command，`ServiceRestartVerifier` evidence 记录 `templateId` 和 `serviceName`，不再记录完整 `systemctlCommand`。如果课程演示需要完整命令，可暂时保留旧 spec API 到 verifier 迁移的下一步。

当前最小迁移可先让 `ServiceRestartVerifier` 保留旧 spec API，等 `ToolExecutionResult` 或 execution audit 能表达 command/template 后再迁。

### 3.6 如何处理非法参数的 `ToolExecutionResult`

建议新 run API 遇到非法参数时返回：

```text
success=false
stdout=""
stderr="invalid command template args: <reason>"
exitCode=null
durationMs=...
```

这比抛异常更符合现有 `CommandRunner.run(null spec)` 的失败返回语义。

后续如果增加 `failureCode`，可用：

```text
INVALID_COMMAND_TEMPLATE_ARGS
```

### 3.7 旧 `run(toolName, CommandSpec)` 的阶段性处理

推荐分阶段：

#### 阶段 1：保留 public

- 新增新 API。
- 工具逐步迁移。
- 旧 API 继续服务测试、verifier evidence 或内部兼容。

#### 阶段 2：静态限制生产使用

- 静态测试禁止 executor 包外生产代码调用 `run(..., CommandSpec)`。
- 静态测试禁止 tool/verifier 包 import `CommandSpec`。

#### 阶段 3：降级 package-private

- 当生产代码完全迁移后，将旧 API 改为 package-private。
- 测试如需构造异常 spec，可以放在 executor 包下继续测。

#### 阶段 4：CommandSpec 内部化

- 最终让 `CommandSpec` 变为 executor 内部对象，外部无法构造自由命令。

## 4. 测试方案

### 4.1 `CommandExecutionOptions` 校验测试

新增：

- `shouldCreateCommandExecutionOptions`
- `shouldRejectNonPositiveTimeout`
- `shouldRejectNonPositiveOutputLimit`

### 4.2 新 run API 正常路径测试

建议避免依赖本机 Linux 命令是否存在。可采用两层测试：

1. 对 `CommandTemplateRegistry.build(...)` 做 canonical 输出测试，已有。
2. 对 `CommandRunner.run(templateId,args,options)` 测试“不返回 template mismatch / invalid args”。

如果要测真实执行，建议只在 Linux/Kylin profile 下跑，不作为跨平台单元测试强依赖。

新增：

- `runWithTemplateShouldBuildAndPassTemplateValidation`

### 4.3 新 run API 非法 args 测试

新增：

- `runWithTemplateShouldReturnFailureWhenPortMissing`
- `runWithTemplateShouldReturnFailureWhenPortOutOfRange`
- `runWithTemplateShouldReturnFailureWhenServiceNameInjected`
- `runWithTemplateShouldReturnFailureWhenUnexpectedArgsPresent`
- `runWithTemplateShouldReturnFailureWhenTemplateIdNull`
- `runWithTemplateShouldReturnFailureWhenOptionsNull`

断言：

- `success=false`
- `exitCode=null`
- `stderr` 包含稳定前缀 `invalid command template args` 或 `command execution options are required`

### 4.4 template mismatch 二次防线测试

旧 API 仍保留时继续覆盖：

- `runWithSpecShouldRejectTemplateMismatch`
- `runWithSpecShouldRejectShellWrapperBeforeTemplateExecution`
- `runWithSpecShouldRejectManualCommandSpecOutsideCanonicalShape`

这些测试证明二次防线没有因为新 API 被删除。

### 4.5 静态禁止工具/verifier 直接调用 registry factory 或 CommandSpec

API 收口后新增：

- `shouldForbidCommandTemplateRegistryUsageInToolAndVerifierProductionCode`
- `shouldForbidCommandSpecImportOutsideExecutorProductionCode`
- `shouldForbidCommandRunnerRunWithCommandSpecOutsideExecutorProductionCode`
- `shouldForbidNewCommandSpecOutsideCommandTemplateRegistryInProductionCode`，保留当前测试

### 4.6 Spring context 是否需要调整

需要小幅调整。

如果 `AbstractBuiltinTool` 不再注入 `CommandTemplateRegistry`，context 测试仍应保留：

- `CommandTemplateRegistry` bean 存在
- `CommandRunner` bean 存在
- 6 个内置工具 bean 存在
- `ServiceRestartVerifier` bean 存在

如果 `ServiceRestartVerifier` 也不再注入 registry，则 context 测试会自然覆盖构造器变化。

## 5. 最小分阶段路径

### 阶段 1：新增 API，不迁移

- 新增 `CommandExecutionOptions`。
- 新增 `CommandRunner.run(toolName, templateId, args, options)`。
- 新 API 内部调用 `CommandTemplateRegistry.build(...)`。
- catch build 参数异常并转为 `ToolExecutionResult`。
- 保留旧 API 和所有现有调用点。

### 阶段 2：迁移工具

- 6 个内置工具改用新 API。
- `AbstractBuiltinTool` 移除 `CommandTemplateRegistry` 字段。
- 静态测试禁止 tool 包使用 `CommandTemplateRegistry` 和 `CommandSpec`。

### 阶段 3：迁移 verifier

- `ServiceRestartVerifier` 改用新 API。
- 若仍需要 evidence 中的完整 command，短期可通过 registry build 保留；长期建议 evidence 记录 `templateId + args`，由审计层关联模板。
- 静态测试禁止 verifier 包使用 `CommandTemplateRegistry` 和 `CommandSpec`。

### 阶段 4：旧 API 降级

- 静态测试禁止 executor 包外调用 `run(toolName, CommandSpec)`。
- 将旧 API 改为 package-private。
- 最终考虑让 `CommandSpec` package-private。

## 6. 复审结论

本轮 factory 边界收紧是正确的，`build(...)` 已经从“忽略额外参数”变为 fail-closed 参数 schema，静态测试也已经覆盖全生产 `new CommandSpec` 约束。当前无 P0/P1，factory 边界足够。

建议进入 `CommandRunner` API 收口：先加 `CommandExecutionOptions` 和新 run API，再迁移 6 个工具，最后迁移 `ServiceRestartVerifier` 并逐步降级旧 `run(toolName, CommandSpec)`。整个过程保留 `matches(spec)` 二次防线，这条安全带不要拆。
