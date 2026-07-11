# QilingOS SafeOps Agent ToolExecutionResult.failureCode 第一阶段静态复审

复审日期：2026-06-28

复审范围：

- `src/main/java/com/cup/opsagent/tool/core/ToolExecutionFailureCode.java`
- `src/main/java/com/cup/opsagent/tool/core/ToolExecutionResult.java`
- `src/main/java/com/cup/opsagent/executor/CommandRunner.java`
- `src/test/java/com/cup/opsagent/executor/CommandRunnerTest.java`
- 所有 `new ToolExecutionResult(...)` 调用点
- 所有 `ToolExecutionResult.skipped(...)` 调用点
- verifier 中读取 `ToolExecutionResult` 的地方

说明：本轮只做静态复审，没有修改 Java 生产代码，没有执行 `mvn test`。测试结果以用户提供为准：`BUILD SUCCESS, Tests run: 83, Failures: 0, Errors: 0`。

## 1. 总体结论

### 是否发现 P0/P1

未发现 P0 或 P1。

`ToolExecutionResult.failureCode` 第一阶段实现整体合理：

- `ToolExecutionFailureCode` 枚举值与上一阶段设计一致。
- `ToolExecutionResult` 新增 nullable `failureCode` 字段。
- 保留 7 参数构造器，旧调用点默认 `failureCode=null`，兼容性良好。
- `skipped(...)` 仍走 7 参数构造器，保持 `failureCode=null`。
- `CommandRunner` 框架级失败分支已填充 failureCode。
- 命令正常启动并完成但 `exitCode != 0` 时仍保持 `failureCode=null`，避免把业务状态误判为执行框架失败。
- 工具业务参数非法、skipped、verifier 失败暂不填 failureCode，这个边界是合理的。

### 当前 failureCode 第一阶段是否合理

合理，可以作为第一阶段落地。

当前 failureCode 的边界很清楚：只表达 `CommandRunner` 框架级失败，不承载工具业务失败、审批跳过、平台不支持、verifier 判断失败等语义。这个切法比较稳，不会把执行框架状态和业务状态混在一起。

### 是否建议进入 audit/AgentResponse 暴露阶段

建议进入 audit trace 暴露阶段，但 AgentResponse 可以稍后。

推荐顺序：

1. 先把 `failureCode` 写入 `TOOL_EXECUTED` 审计 payload。
2. 再在 verifier evidence 中可选记录下游命令的 `failureCode`，例如 `ServiceRestartVerifier` 的 `statusFailureCode`。
3. 最后再评估是否需要在 `AgentResponse` 中展示。AgentResponse 面向用户，直接暴露底层执行码可能过细，应先确定前端和课程演示需要。

## 2. P0/P1/P2 问题列表

### P0

无。

### P1

无。

### P2-1：`ToolExecutionResult` 未强制 `success=true` 时 `failureCode=null`

- 文件/类/方法：`ToolExecutionResult`
- 位置：`src/main/java/com/cup/opsagent/tool/core/ToolExecutionResult.java:5`
- 问题描述：record 新增 8 参数 canonical 构造器后，外部代码可以构造 `success=true` 且 `failureCode != null` 的结果。
- 为什么是问题：当前生产代码没有这样做，`CommandRunner` 成功分支也会传 null。但 record 是 public，未来测试或新代码可能误构造出语义矛盾的结果，影响审计和上层判断。
- 最小修复建议：在 compact constructor 中增加不变量校验：如果 `success && failureCode != null`，抛 `IllegalArgumentException`；或者更温和地把 `failureCode` 归一化为 null。安全语义更推荐 fail-fast。
- 建议新增测试：
  - `ToolExecutionResultTest.shouldRejectFailureCodeWhenSuccessIsTrue`
  - `ToolExecutionResultTest.shouldAllowFailureCodeWhenSuccessIsFalse`
  - `ToolExecutionResultTest.shouldKeepSevenArgConstructorFailureCodeNull`

### P2-2：`ToolExecutionResult` 对 stdout/stderr/executedAt 仍缺少 null-safe 归一化

- 文件/类/方法：`ToolExecutionResult`
- 位置：`src/main/java/com/cup/opsagent/tool/core/ToolExecutionResult.java:5`
- 问题描述：record 没有 compact constructor 统一处理 `stdout`、`stderr`、`executedAt` 的 null。
- 为什么是问题：当前大多数构造点都传了非 null 字符串和时间，但 public record 允许外部传 null。后续 audit/verifier 如果直接使用这些字段，可能需要反复做 null 防御。
- 最小修复建议：在 compact constructor 中归一化：`stdout = stdout == null ? "" : stdout`，`stderr = stderr == null ? "" : stderr`，`executedAt = executedAt == null ? Instant.now() : executedAt`。
- 建议新增测试：
  - `ToolExecutionResultTest.shouldNormalizeNullStdoutAndStderr`
  - `ToolExecutionResultTest.shouldDefaultExecutedAtWhenNull`

### P2-3：`TOOL_EXECUTED` 审计 payload 尚未包含 failureCode

- 文件/类/方法：`AgentOrchestrator.handle(...)`
- 位置：`src/main/java/com/cup/opsagent/agent/core/AgentOrchestrator.java:155`
- 问题描述：`TOOL_EXECUTED` 当前记录 `success`、`exitCode`、`durationMs`，没有记录 `failureCode`。
- 为什么是问题：failureCode 的主要价值是审计和上层可观测性。如果不进入 audit trace，只能在内存对象或测试中看到，排障仍要读 stderr 文本。
- 最小修复建议：在 `TOOL_EXECUTED` payload 中增加 `"failureCode", executionResult.failureCode() == null ? "none" : executionResult.failureCode().name()`。
- 建议新增测试：
  - `AgentOrchestratorTest.shouldAuditToolExecutionFailureCodeWhenPresent`
  - `AgentOrchestratorTest.shouldAuditToolExecutionFailureCodeNoneWhenAbsent`

### P2-4：`AgentOrchestrator` 的 tool.execute 异常兜底未设置 failureCode

- 文件/类/方法：`AgentOrchestrator.handle(...)`
- 位置：`src/main/java/com/cup/opsagent/agent/core/AgentOrchestrator.java:145`
- 问题描述：`tool.execute(...)` 抛 RuntimeException 时，orchestrator 构造 7 参数 `ToolExecutionResult`，failureCode 为 null。
- 为什么是问题：这不是 `CommandRunner` 框架级失败，而是工具执行异常。第一阶段不填 code 是可以接受的。但进入更完整的执行结果语义后，这类异常也值得结构化。
- 最小修复建议：第一阶段保持不变。第二阶段如果扩展 enum，可新增 `TOOL_EXECUTION_EXCEPTION` 或放入独立的 `ToolExecutionStatusCode`。
- 建议新增测试：暂不要求。若新增 code，再补 `AgentOrchestratorTest.shouldSetFailureCodeWhenToolThrowsRuntimeException`。

### P2-5：`ServiceRestartVerifier` evidence 未记录 `statusResult.failureCode`

- 文件/类/方法：`ServiceRestartVerifier.verify(...)`
- 位置：`src/main/java/com/cup/opsagent/verifier/ServiceRestartVerifier.java:90`
- 问题描述：restart verifier 对 `systemctl is-active` 的执行结果 evidence 记录了 success、exitCode、serviceStatus、stdout/stderr 长度，但没有记录 failureCode。
- 为什么是问题：如果验证阶段的 `CommandRunner` 发生框架级失败，例如模板参数错误、进程启动失败、超时，evidence 里没有结构化原因，只能通过 statusSuccess=false 和缺失 stdout 间接判断。
- 最小修复建议：可选增加 `"statusFailureCode", statusResult.failureCode() == null ? "none" : statusResult.failureCode().name()`。不要改变 verifier 核心判断逻辑。
- 建议新增测试：
  - `ServiceRestartVerifierTest.shouldIncludeStatusFailureCodeWhenStatusCommandFailsAtRunnerLayer`

### P2-6：`PROCESS_START_FAILED` / `COMMAND_TIMEOUT` / `COMMAND_INTERRUPTED` 尚无稳定单测

- 文件/类/方法：`CommandRunner`
- 位置：
  - `src/main/java/com/cup/opsagent/executor/CommandRunner.java:92`
  - `src/main/java/com/cup/opsagent/executor/CommandRunner.java:100`
  - `src/main/java/com/cup/opsagent/executor/CommandRunner.java:103`
- 问题描述：当前测试覆盖了 fail-fast failureCode，但未覆盖真正进入 process 后的三类框架失败。
- 为什么是问题：这三类分支依赖真实进程行为，在 Windows 开发环境和 Kylin Linux 运行环境下很难写稳定单测。没有可控进程抽象时，测试容易 flaky。
- 最小修复建议：引入最小 `ProcessLauncher` 或 `ProcessFactory` 抽象，仅把 `new ProcessBuilder(spec.command()).start()` 包起来，生产实现仍使用 ProcessBuilder，测试实现可模拟 IOException、timeout、interrupt。
- 建议新增测试：
  - `shouldSetFailureCodeForProcessStartFailed`
  - `shouldSetFailureCodeForCommandTimeout`
  - `shouldSetFailureCodeForCommandInterrupted`

## 3. 测试覆盖评价

### 已覆盖哪些 failureCode

`CommandRunnerTest` 已覆盖以下 6 类：

1. `COMMAND_TEMPLATE_ID_MISSING`
   - `runWithTemplateShouldReturnFailureForNullTemplateId`

2. `COMMAND_EXECUTION_OPTIONS_MISSING`
   - `runWithTemplateShouldReturnFailureForNullOptions`

3. `INVALID_COMMAND_TEMPLATE_ARGS`
   - `runWithTemplateShouldReturnFailureForInvalidArgs`
   - `runWithTemplateShouldReturnFailureForUnexpectedArgs`

4. `COMMAND_SPEC_MISSING`
   - `shouldReturnFailureForNullCommandSpec`

5. `SHELL_WRAPPER_DENIED`
   - `shouldDenyDirectShellWrapper`
   - `shouldDenyShellWrapperWithAbsolutePath`
   - `shouldDenyWindowsShellWrapperWithAbsolutePath`
   - `shouldDenyPowershellEvenWithPreOptions`

6. `COMMAND_TEMPLATE_MISMATCH`
   - `shouldDenyCommandThatDoesNotMatchTemplate`

### 未覆盖哪些 failureCode

暂未覆盖：

1. `PROCESS_START_FAILED`
2. `COMMAND_TIMEOUT`
3. `COMMAND_INTERRUPTED`

这些分支需要可控 process 行为。当前暂不测是可以接受的，但建议下一阶段补测试基础设施。

### 是否建议引入 ProcessLauncher

建议引入，但作为 P2 测试可维护性改造，不阻塞当前阶段。

最小设计：

```java
interface ProcessLauncher {
    Process start(List<String> command) throws IOException;
}
```

生产实现：

```java
final class ProcessBuilderProcessLauncher implements ProcessLauncher {
    public Process start(List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectInput(ProcessBuilder.Redirect.PIPE);
        return builder.start();
    }
}
```

`CommandRunner` 构造器：

```java
public CommandRunner(CommandTemplateRegistry registry) {
    this(registry, new ProcessBuilderProcessLauncher());
}

CommandRunner(CommandTemplateRegistry registry, ProcessLauncher launcher) { ... }
```

注意：仍要保持静态测试只允许生产 `ProcessBuilder` 出现在 process launcher 或 CommandRunner 的明确允许位置。

### 如何最小化测试改造

1. 保持现有 `CommandRunner(CommandTemplateRegistry)` public 构造器，Spring 不受影响。
2. 新增 package-private 测试构造器注入 fake launcher。
3. fake launcher 返回一个可控 `Process` 子类：
   - start 抛 `IOException` -> 测 `PROCESS_START_FAILED`
   - `waitFor(timeout, unit)` 返回 false -> 测 `COMMAND_TIMEOUT`
   - `waitFor(timeout, unit)` 抛 `InterruptedException` -> 测 `COMMAND_INTERRUPTED`
4. 不用真实系统命令，不依赖 Windows/Kylin 差异。

## 4. 下一阶段建议

### 是否把 failureCode 写入 audit trace

建议做，而且优先级高于 AgentResponse 暴露。

最小改动：

```java
"failureCode", executionResult.failureCode() == null ? "none" : executionResult.failureCode().name()
```

加入 `TOOL_EXECUTED` payload 即可。这样不会改变 API 响应结构，却能显著提升审计可观测性。

### 是否把 failureCode 暴露到 AgentResponse

建议暂缓。

理由：

1. `AgentResponse` 面向用户/前端，直接暴露底层 runner code 可能过细。
2. 目前 `StepResult` 已包含 `ToolExecutionResult`，如果序列化会自然带出 failureCode；需要先确认前端展示和脱敏策略。
3. 更稳妥的做法是先进入 audit，再决定用户响应是否需要摘要化字段，例如 `executionFailureCode`。

### 是否给 skipped/业务失败设计独立 statusCode

建议单独设计，不要混入当前 `ToolExecutionFailureCode`。

当前 `skipped` 包含多种语义：

- Policy BLOCK 后构造 skipped；
- WAITING_APPROVAL；
- Windows 开发环境跳过；
- 参数非法跳过；
- `restart_service` Phase 1 不执行。

这些不是同一种 runner 框架失败。建议后续新增独立概念，例如：

```java
public enum ToolExecutionStatusCode {
    SKIPPED_POLICY_BLOCKED,
    SKIPPED_APPROVAL_REQUIRED,
    SKIPPED_UNSUPPORTED_PLATFORM,
    SKIPPED_INVALID_ARGUMENT,
    SKIPPED_PHASE_NOT_EXECUTABLE,
    EXECUTION_FRAMEWORK_FAILURE
}
```

也可以先不加 enum，只在 audit event type 中表达这些状态。

### 是否需要静态测试禁止上层解析 stderr 判断失败类型

建议后续增加，但不必立刻做。

当前可以先做轻量静态测试：

1. 禁止 `agent`、`audit`、`verifier` 包中出现 `stderr().contains("invalid command template args")` 等具体 runner 文本。
2. 允许测试包继续断言 stderr 兼容文本。
3. 如果上层需要判断 runner 失败类型，应使用 `failureCode()`。

长期更推荐用代码 review 规范或 ArchUnit 限制：生产上层只能读取 `failureCode` 判断框架失败，不解析 stderr。

## 5. 逐项复审结论

1. `ToolExecutionResult` 新增字段通过 7 参数构造器保持了兼容。
2. 7 参数构造器足够避免大面积改动，现有调用点无需立即迁移。
3. 当前生产代码未发现 `success=true` 却携带 failureCode 的路径，但建议在 record 层加不变量。
4. exitCode 非 0 但命令执行完成时 `failureCode=null` 合理。
5. skipped 结果 `failureCode=null` 合理。
6. `CommandRunner` 框架级失败分支的 failureCode 映射准确。
7. `IOException` 使用 `safeMessage(exception, "process start failed")` 合理。
8. 未发现应填 failureCode 但遗漏的 runner fail-fast 分支；process 后三类失败已填 code，但测试未覆盖。
9. 未发现不应填 failureCode 却填了的业务语义。
10. 当前测试覆盖第一阶段核心 fail-fast failureCode，足以支撑本阶段。
11. 建议引入最小 `ProcessLauncher` 以稳定测试 timeout、interrupt、process start failed。
12. 建议下一阶段优先将 failureCode 写入 audit trace，再评估 AgentResponse 暴露。
