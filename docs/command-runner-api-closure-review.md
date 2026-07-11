# QilingOS SafeOps Agent CommandRunner API 收口静态复审

复审日期：2026-06-28

复审范围：

- `src/main/java/com/cup/opsagent/executor/CommandExecutionOptions.java`
- `src/main/java/com/cup/opsagent/executor/CommandRunner.java`
- `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java`
- `src/main/java/com/cup/opsagent/executor/CommandSpec.java`
- `src/main/java/com/cup/opsagent/tool/builtin/*.java`
- `src/main/java/com/cup/opsagent/verifier/ServiceRestartVerifier.java`
- `src/test/java/com/cup/opsagent/executor/CommandRunnerTest.java`
- `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java`
- `src/test/java/com/cup/opsagent/verifier/ServiceRestartVerifierTest.java`
- `src/test/java/com/cup/opsagent/OpsAgentApplicationContextTest.java`

说明：本轮只做静态复审，没有修改 Java 生产代码，没有执行 `mvn test`。以下测试结论基于当前代码和用户提供的测试结果：`BUILD SUCCESS, Tests run: 79, Failures: 0, Errors: 0`。

## 1. 总体结论

### 是否发现 P0/P1

未发现 P0 或 P1。

当前命令执行主边界已经形成三层防线：

1. 工具和 verifier 只调用 `CommandRunner.run(toolName, templateId, args, options)`。
2. `CommandRunner` 调 `CommandTemplateRegistry.build(...)` 生成模板命令。
3. 旧 spec 执行路径仍在真正 `ProcessBuilder` 前执行 shell wrapper denylist 和 `matches(spec)` 二次匹配。

从静态代码看，`templateId == null`、`options == null`、`invalid args` 都会在进入 `ProcessBuilder` 前失败返回。`ProcessBuilder` 仍只出现在 `CommandRunner` 内，`Runtime.exec` 未在生产代码中出现。

### 当前 API 收口是否达到 MVP

达到 MVP 安全边界。

6 个内置工具和 `ServiceRestartVerifier` 已不再依赖 `CommandTemplateRegistry` 或 `CommandSpec`，命令构造权基本收回到 `CommandRunner -> CommandTemplateRegistry` 这一条链路。`CommandExecutionOptions` 也已将 timeout 和 output limit 从散落参数收口为显式对象，并在构造期拒绝非正值。

### 是否建议继续降级旧 API

建议继续降级旧 API，但不是阻塞 MVP 的问题。

`public ToolExecutionResult run(String toolName, CommandSpec spec)` 仍是当前最大的剩余开放面。虽然现有生产代码没有在 executor 包外调用它，且静态测试已禁止生产代码在 `CommandTemplateRegistry.java` 之外 `new CommandSpec(...)`，但 public API 本身仍允许未来代码绕过新 API 的参数 schema 层，直接提交手写 `CommandSpec` 给 runner。

建议下一阶段将旧 API 按分阶段方式降级：

1. 先新增静态测试，禁止 `executor` 包外生产代码调用 `run(..., CommandSpec)`。
2. 将旧 API 标记为内部兼容路径，测试可继续覆盖 shell wrapper 和 template mismatch。
3. 迁移测试辅助方式后，将旧 API 改为 package-private。
4. 再考虑把 `CommandSpec` 也收为 package-private 或 executor 内部对象。

## 2. P0/P1/P2 问题列表

### P0

无。

### P1

无。

### P2-1：旧 `run(String, CommandSpec)` 仍是 public

- 文件/类/方法：`CommandRunner.run(String, CommandSpec)`
- 位置：`src/main/java/com/cup/opsagent/executor/CommandRunner.java:64`
- 问题描述：新 API 已经收口到 `templateId + args + options`，但旧 spec API 仍为 public。
- 为什么是问题：当前工具和 verifier 已迁移，但未来任何生产代码只要能拿到 `CommandSpec`，仍可绕过 `build(...)` 的 fail-closed 参数 schema，直接提交命令数组。虽然执行前还有 shell wrapper denylist 和 `matches(spec)`，但少了一层参数入口约束，也会让架构边界变模糊。
- 最小修复建议：先加静态测试禁止 `executor` 包外生产代码调用 `run(` 且传入 `CommandSpec`；随后将该方法降级为 package-private。
- 建议新增测试：
  - `shouldForbidCommandRunnerSpecRunOutsideExecutorProductionCode`
  - 扫描 `src/main/java`，排除 `executor` 包，禁止出现 `run(` 与 `CommandSpec` 的组合调用。

### P2-2：`CommandSpec` 仍是 public，且 registry typed factory 仍返回 `CommandSpec`

- 文件/类/方法：
  - `CommandSpec`
  - `CommandTemplateRegistry.getSystemLoad(...)`
  - `CommandTemplateRegistry.getTopProcesses(...)`
  - `CommandTemplateRegistry.getOpenPorts(...)`
  - `CommandTemplateRegistry.checkPortUsage(...)`
  - `CommandTemplateRegistry.getServiceStatus(...)`
  - `CommandTemplateRegistry.verifyServiceActive(...)`
- 位置：
  - `src/main/java/com/cup/opsagent/executor/CommandSpec.java`
  - `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java:54`
  - `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java:58`
  - `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java:67`
  - `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java:71`
  - `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java:82`
  - `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java:93`
- 问题描述：`CommandSpec` 作为命令数组承载对象仍对外 public，typed factory 也仍暴露它。
- 为什么是问题：这会让外部代码继续围绕 `CommandSpec` 建模，而不是围绕 `CommandTemplateId + args + options` 建模。短期有 `matches(spec)` 兜底，长期会拖慢 API 收口。
- 最小修复建议：短期保留 public 以降低改造风险，但新增全生产静态约束，禁止 executor 包外 import/use `CommandSpec` 和 `CommandTemplateRegistry`。中期将 typed factory 改为 package-private，仅供 `CommandRunner` 使用。长期将 `CommandSpec` 改为 package-private。
- 建议新增测试：
  - `shouldForbidCommandSpecUsageOutsideExecutorProductionCode`
  - `shouldForbidCommandTemplateRegistryUsageOutsideExecutorProductionCode`

### P2-3：静态架构测试覆盖 tool/verifier 很强，但全局 API 边界还可收紧

- 文件/类/方法：`CommandTemplateRegistryTest`
- 位置：
  - `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java:154`
  - `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java:167`
  - `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java:179`
  - `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java:191`
- 问题描述：当前测试已禁止 tool/verifier 包使用 `CommandTemplateRegistry` 和 `CommandSpec`，也禁止 `CommandTemplateRegistry.java` 之外 `new CommandSpec(...)`，禁止 `ProcessBuilder` 外逃和 `Runtime.exec`。但还没有全生产范围禁止 executor 包外调用旧 spec API 或使用 registry factory。
- 为什么是问题：未来新增 controller、agent、service、planner 等包时，仍可能直接注入 `CommandTemplateRegistry` 或调用旧 `run(String, CommandSpec)`，绕开新 API 收口意图。
- 最小修复建议：将架构测试从 tool/verifier 扩展为所有 `src/main/java`，允许清单只保留 `executor` 包和必要 Spring context 测试。
- 建议新增测试：
  - `shouldForbidCommandTemplateRegistryUsageOutsideExecutorProductionCode`
  - `shouldForbidCommandSpecUsageOutsideExecutorProductionCode`
  - `shouldForbidOldCommandRunnerSpecApiOutsideExecutorProductionCode`

### P2-4：`ServiceRestartVerifier` 构造器未对 `CommandRunner` 做 null fail-fast

- 文件/类/方法：`ServiceRestartVerifier.ServiceRestartVerifier(CommandRunner)`
- 位置：`src/main/java/com/cup/opsagent/verifier/ServiceRestartVerifier.java:27`
- 问题描述：构造器直接赋值 `this.commandRunner = commandRunner`，没有 `Objects.requireNonNull`。
- 为什么是问题：Spring 正常注入不会给 null，但测试替身或手动构造时可能延迟到 verify 阶段 NPE。项目当前已经在 `CommandRunner` 和 `AbstractBuiltinTool` 上采用构造期 fail-fast，这里保持一致更清晰。
- 最小修复建议：构造器中使用 `Objects.requireNonNull(commandRunner, "commandRunner must not be null")`。
- 建议新增测试：
  - `shouldRejectNullCommandRunnerInServiceRestartVerifierConstructor`

### P2-5：新 run API 的 invalid args 测试还可以覆盖“未知参数”

- 文件/类/方法：`CommandRunner.run(String, CommandTemplateId, Map<String,Object>, CommandExecutionOptions)`
- 位置：`src/main/java/com/cup/opsagent/executor/CommandRunner.java:42`
- 问题描述：`CommandRunnerTest` 已覆盖缺少 `port` 的 invalid args，但还没有直接覆盖新 API 遇到 unexpected args 时的失败返回。
- 为什么是问题：`CommandTemplateRegistryTest` 已覆盖 registry 层 unexpected args，但 runner 层需要确认异常被转换为 `ToolExecutionResult(success=false, exitCode=null)`，且不会进入执行路径。
- 最小修复建议：在 `CommandRunnerTest` 新增 unexpected args 场景，例如 `GET_SYSTEM_LOAD` 传入 `Map.of("unexpected", true)`。
- 建议新增测试：
  - `runWithTemplateShouldReturnFailureForUnexpectedArgs`
  - 断言 `success=false`、`exitCode=null`、`stderr` 包含 `invalid command template args` 和 `unexpected command template args`。

### P2-6：`ServiceRestartVerifier` evidence 的新字段需要更明确断言

- 文件/类/方法：`ServiceRestartVerifier.verify(...)`
- 位置：`src/main/java/com/cup/opsagent/verifier/ServiceRestartVerifier.java:89`
- 问题描述：evidence 已从完整命令改成 `statusTemplateId + serviceName`，这是更安全的审计表达。但测试目前主要断言不暴露 stdout/stderr 原文，以及记录 serviceName/status，缺少对 `statusTemplateId` 的直接断言。
- 为什么是问题：后续如果有人误删 `statusTemplateId`，审计仍能通过当前大多数测试，但排障时会少一个关键模板线索。
- 最小修复建议：在 active/inactive 两个测试中断言 `result.evidence()` 包含 `statusTemplateId=VERIFY_SERVICE_ACTIVE`。
- 建议新增测试：
  - `shouldRecordVerifyServiceActiveTemplateIdInEvidence`

### P2-7：失败类型仍靠 stderr 文本表达，长期不利于审计聚合

- 文件/类/方法：`ToolExecutionResult`
- 位置：`src/main/java/com/cup/opsagent/tool/core/ToolExecutionResult.java`
- 问题描述：`ToolExecutionResult` 目前只有 `success/stdout/stderr/exitCode`，runner 失败原因通过 stderr 文本表达。
- 为什么是问题：审计、前端、上层 response 如果需要区分模板非法、shell wrapper 阻断、模板 mismatch、超时、进程启动失败，只能解析字符串，稳定性差。
- 最小修复建议：本轮不必阻塞 API 收口。下一阶段可引入 nullable `failureCode` 或保留兼容构造器的静态 factory，避免一次性改动所有 `new ToolExecutionResult(...)`。
- 建议新增测试：
  - 如果引入 failureCode，新增每个 runner fail-fast 分支的 failureCode 断言。

## 3. 测试覆盖评价

### 已覆盖的安全边界

现有测试已经覆盖以下关键边界：

1. `CommandExecutionOptions` 拒绝 `timeoutMs <= 0` 和 `outputLimitBytes <= 0`。
2. 新 `CommandRunner.run(templateId,args,options)` 对 `templateId == null` 返回失败。
3. 新 `CommandRunner.run(templateId,args,options)` 对 `options == null` 返回失败。
4. 新 `CommandRunner.run(templateId,args,options)` 对缺少必需参数返回 `invalid command template args`。
5. 旧 spec 执行路径阻断 `sh`、`/bin/sh`、`cmd.exe`、`powershell` 等 shell wrapper。
6. 旧 spec 执行路径执行前仍调用 `CommandTemplateRegistry.matches(spec)`，可阻断模板 mismatch。
7. `CommandTemplateRegistry.build(...)` 采用 fail-closed 参数 schema。
8. `CHECK_PORT_USAGE` 模板拒绝 `:+22`、`:00022`、`:22;whoami`、`:0`、`:65536`。
9. `GET_SERVICE_STATUS` 和 `VERIFY_SERVICE_ACTIVE` 模板强制 `--` 并拒绝子命令互换、注入 serviceName。
10. `ServiceNameValidator` 与模板层 serviceName 校验有一致性测试。
11. tool/verifier 生产包禁止使用 `CommandTemplateRegistry` 和 `CommandSpec`。
12. 全生产代码只允许 `CommandTemplateRegistry.java` 出现 `new CommandSpec(`。
13. 全生产代码只允许 `CommandRunner` 使用 `new ProcessBuilder`。
14. 全生产代码禁止 `Runtime.getRuntime().exec` 和 `.exec(`。
15. Spring context 能创建 `CommandTemplateRegistry`、`CommandRunner`、6 个内置工具、`ServiceRestartVerifier`。
16. `ServiceRestartVerifier` 已覆盖 active、inactive、非法 serviceName、以 `-` 开头、缺失参数、restart 执行失败不触发 is-active、stdout/stderr 原文不进入 evidence。

### 还缺的最小测试

建议新增以下最小测试：

1. `CommandRunnerTest.runWithTemplateShouldReturnFailureForUnexpectedArgs`
   - 输入：`GET_SYSTEM_LOAD` + `Map.of("unexpected", true)`。
   - 预期：`success=false`、`exitCode=null`、`stderr` 包含 `invalid command template args`。

2. `CommandRunnerTest.runWithTemplateShouldNotExecuteWhenBuildFails`
   - 可通过 spy/fake registry 或测试用 registry 记录是否进入执行路径较难；最小方案是断言 invalid args 的返回 `exitCode=null` 且 stderr 为模板错误，不出现系统命令 stderr。

3. `CommandTemplateRegistryTest.shouldForbidOldCommandRunnerSpecApiOutsideExecutorProductionCode`
   - 扫描 `src/main/java`，排除 `executor` 包，禁止调用 `run(` 时使用 `CommandSpec` 或 import `CommandSpec`。

4. `CommandTemplateRegistryTest.shouldForbidCommandTemplateRegistryUsageOutsideExecutorProductionCode`
   - 扫描全生产代码，排除 `executor` 包，禁止出现 `CommandTemplateRegistry`。

5. `CommandTemplateRegistryTest.shouldForbidCommandSpecUsageOutsideExecutorProductionCode`
   - 扫描全生产代码，排除 `executor` 包，禁止出现 `CommandSpec`。

6. `ServiceRestartVerifierTest.shouldRecordVerifyServiceActiveTemplateIdInEvidence`
   - 断言 evidence 包含 `statusTemplateId=VERIFY_SERVICE_ACTIVE`。

7. `ServiceRestartVerifierTest.shouldRejectNullCommandRunnerInConstructor`
   - 如果按 P2-4 修复，断言构造器 null fail-fast。

### 静态测试是否需要增强

需要增强，但属于 P2。

当前静态测试对 tool/verifier 包已经很强，对命令执行原语也覆盖充分。下一步应从“禁止工具层绕过”升级到“禁止 executor 包外任何生产代码绕过”，也就是把命令构造和 spec 执行 API 收口为 executor 内部能力。

建议静态约束目标：

1. `ProcessBuilder` 只允许 `CommandRunner`。
2. `Runtime.exec` 全生产禁止。
3. `new CommandSpec(` 只允许 `CommandTemplateRegistry`。
4. `CommandSpec` 类型只允许 executor 包生产代码使用。
5. `CommandTemplateRegistry` 类型只允许 executor 包生产代码使用。
6. `CommandRunner.run(String, CommandSpec)` 只允许 executor 包内部和测试代码使用。

## 4. 下一阶段建议

### 是否降级 `run(toolName, CommandSpec)`

建议降级。

推荐分阶段：

1. 当前阶段：保留 public，不阻塞 MVP 发布。
2. 第 1 步：新增静态测试，确认生产代码中 executor 包外无旧 API 调用。
3. 第 2 步：给旧 API 加注释或 `@Deprecated(forRemoval = true)`，标记仅用于内部兼容和测试。
4. 第 3 步：将旧 API 改为 package-private。
5. 第 4 步：测试中需要 shell wrapper/mismatch 覆盖时，放到 `com.cup.opsagent.executor` 包内继续测 package-private 方法，或者改用可控测试模板/测试 registry。

### `CommandSpec` 是否 package-private

建议中期 package-private，不建议立刻做。

原因：

1. 当前 `CommandTemplateRegistry` typed factory 仍返回 `CommandSpec`。
2. 多个测试直接构造 `CommandSpec` 来验证 shell wrapper、template mismatch、防御性复制。
3. 立刻改为 package-private 会引发测试结构调整，收益小于先降级旧 run API。

推荐顺序：

1. 先禁止 executor 包外生产代码使用 `CommandSpec`。
2. 再将 `CommandRunner.run(String, CommandSpec)` 降级。
3. 再将 `CommandTemplateRegistry` typed factory 降级为 package-private。
4. 最后将 `CommandSpec` 改为 package-private。

### `ToolExecutionResult.failureCode` 是否现在做

不建议作为本轮 API 收口的前置项。建议下一小阶段单独做。

当前 `ToolExecutionResult` 是 record：

```java
public record ToolExecutionResult(
        String toolName,
        boolean success,
        String stdout,
        String stderr,
        Integer exitCode,
        long durationMs,
        Instant executedAt
) { ... }
```

直接新增字段会影响所有构造点和测试。它值得做，但最好单独规划。

### failureCode 最小枚举

如果要做，建议最小枚举如下：

```java
public enum ToolExecutionFailureCode {
    COMMAND_TEMPLATE_ID_MISSING,
    COMMAND_EXECUTION_OPTIONS_MISSING,
    INVALID_COMMAND_TEMPLATE_ARGS,
    COMMAND_SPEC_MISSING,
    SHELL_WRAPPER_DENIED,
    COMMAND_TEMPLATE_MISMATCH,
    PROCESS_START_FAILED,
    COMMAND_TIMEOUT,
    COMMAND_INTERRUPTED,
    NON_ZERO_EXIT
}
```

注意：`NON_ZERO_EXIT` 要谨慎。某些只读系统命令返回非 0 可能是业务状态，不一定是安全失败。例如 `systemctl is-active` 非 0 表示服务不 active。是否归为 runner failure，需要结合 tool/verifier 语义决定。

### failureCode 最小迁移路径

推荐迁移路径：

1. 新增 `ToolExecutionFailureCode` enum。
2. 给 `ToolExecutionResult` 增加 nullable `failureCode` 字段，或新增包装型 failure metadata。
3. 保留兼容静态工厂，例如：
   - `ToolExecutionResult.commandFailure(toolName, failureCode, message, startedAt)`
   - `ToolExecutionResult.processResult(toolName, stdout, stderr, exitCode, startedAt)`
   - `ToolExecutionResult.skipped(toolName, reason)`
4. 先只让 `CommandRunner` 的 fail-fast 分支填 failureCode。
5. 再逐步让 tools/verifier 对 failureCode 做断言，避免上层继续解析 stderr 文本。

### 建议的下一阶段最小路径

1. 新增静态测试：禁止 executor 包外使用 `CommandSpec`、`CommandTemplateRegistry`、旧 spec run API。
2. 将旧 `run(String, CommandSpec)` 改为 package-private。
3. `CommandTemplateRegistry` typed factory 保持 public 一轮，但生产外部禁止使用。
4. 稳定后将 typed factory 改为 package-private，只保留 `build(...)` 给 `CommandRunner`。
5. 最后将 `CommandSpec` 变成 executor 包内部对象。
6. 单独规划 `ToolExecutionResult.failureCode`，不要和 API 降级混在一个提交中。

## 5. 关键判断逐项回应

1. 新 run API 对 `templateId null`、`options null`、`invalid args` 的处理是安全的，均在执行前返回失败。
2. invalid args 不会执行 `ProcessBuilder`，因为 `build(...)` 异常在进入私有 spec 执行路径前被捕获。
3. build 成功后仍保留 `matches(spec)` 二次防线。
4. 工具和 verifier 生产代码已经不依赖 `CommandTemplateRegistry` 或 `CommandSpec`。
5. 当前静态测试足以防止 tool/verifier 回退到旧模型，但建议扩展到 executor 包外所有生产代码。
6. `ServiceRestartVerifier` evidence 从完整命令改成 `statusTemplateId + serviceName` 是可接受且更安全的做法。
7. 旧 public `run(toolName, CommandSpec)` 仍是剩余开放面，建议继续降级。
8. 不建议把 `failureCode` 作为本轮必须修复项，但建议下一阶段引入，减少 stderr 字符串解析。
