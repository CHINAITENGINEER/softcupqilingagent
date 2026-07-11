# QilingOS SafeOps Agent failureCode 审计可观测性增强静态复审

复审日期：2026-06-28

复审范围：

- `src/main/java/com/cup/opsagent/tool/core/ToolExecutionResult.java`
- `src/main/java/com/cup/opsagent/tool/core/ToolExecutionFailureCode.java`
- `src/main/java/com/cup/opsagent/executor/CommandRunner.java`
- `src/main/java/com/cup/opsagent/agent/core/AgentOrchestrator.java`
- `src/main/java/com/cup/opsagent/verifier/ServiceRestartVerifier.java`
- `src/test/java/com/cup/opsagent/tool/core/ToolExecutionResultTest.java`
- `src/test/java/com/cup/opsagent/agent/core/AgentOrchestratorTest.java`
- `src/test/java/com/cup/opsagent/verifier/ServiceRestartVerifierTest.java`

说明：本轮只做静态复审，没有修改 Java 生产代码，没有执行 `mvn test`。测试结果以用户提供为准：`BUILD SUCCESS, Tests run: 91, Failures: 0, Errors: 0`。

## 1. 总体结论

### 是否发现 P0/P1

未发现 P0 或 P1。

当前 `failureCode + audit/evidence` 增强是合理的：

- `ToolExecutionResult` compact constructor 已强制 `success=true` 时 `failureCode=null`。
- `stdout`、`stderr`、`executedAt` 已做 null-safe 归一化。
- 7 参数构造器仍保留，兼容旧构造点。
- `skipped(...)` 仍保持 `failureCode=null`，没有把审批跳过、平台跳过、参数非法等业务状态混入 runner 框架失败。
- `TOOL_EXECUTED` audit payload 已记录 `failureCode`。
- `ServiceRestartVerifier` evidence 已记录 `statusFailureCode`，且不改变核心验证逻辑。

### 当前增强是否合理

合理，可以作为 failureCode 第一阶段的完整收尾。

这轮增强把 failureCode 从“内存对象字段”推进到了“审计与 verifier evidence 可观测字段”，但仍保持了边界克制：

- runner 框架失败使用 failureCode；
- 命令非 0 exit code 不强行归类为 failureCode；
- skipped 和工具业务失败暂不使用 failureCode；
- verifier 不根据 failureCode 改写判断逻辑。

这对 MVP 是合适的。

### 是否建议继续进入 ProcessLauncher 阶段

建议进入，但作为测试可控性和执行层解耦阶段，不是安全阻塞项。

当前还缺 `PROCESS_START_FAILED`、`COMMAND_TIMEOUT`、`COMMAND_INTERRUPTED` 的稳定单测。这三类分支都依赖真实 `Process` 行为，不建议用真实系统命令硬测。最小 `ProcessLauncher` 可以让这些分支可控、跨平台、少 flaky。

## 2. P0/P1/P2 问题列表

### P0

无。

### P1

无。

### P2-1：`toolName` 尚未在 `ToolExecutionResult` 中归一化或校验

- 文件/类/方法：`ToolExecutionResult`
- 位置：`src/main/java/com/cup/opsagent/tool/core/ToolExecutionResult.java:6`
- 问题描述：compact constructor 已归一化 `stdout/stderr/executedAt`，但没有校验或归一化 `toolName`。
- 为什么是问题：当前生产调用点基本都传合法 toolName，因此不是安全问题。但 `ToolExecutionResult` 是 public record，未来手动构造可能传入 null/blank，导致 audit、StepResult、前端展示出现空工具名。
- 最小修复建议：建议 fail-fast，而不是归一化为空字符串。工具名是身份字段，`toolName == null || toolName.isBlank()` 时抛 `IllegalArgumentException` 更清晰。
- 建议新增测试：
  - `ToolExecutionResultTest.shouldRejectNullToolName`
  - `ToolExecutionResultTest.shouldRejectBlankToolName`

### P2-2：`durationMs` 未校验非负

- 文件/类/方法：`ToolExecutionResult`
- 位置：`src/main/java/com/cup/opsagent/tool/core/ToolExecutionResult.java:10`
- 问题描述：record 允许外部构造 `durationMs < 0`。
- 为什么是问题：当前 `CommandRunner` 使用 `Duration.between(startedAt, Instant.now()).toMillis()`，正常不会为负；但 public record 的手动构造点很多，负值会污染审计指标。
- 最小修复建议：compact constructor 中增加 `if (durationMs < 0) throw new IllegalArgumentException("durationMs must not be negative")`。
- 建议新增测试：
  - `ToolExecutionResultTest.shouldRejectNegativeDurationMs`

### P2-3：`ReadOnlyToolVerifier` evidence 尚未记录 execution failureCode

- 文件/类/方法：`ReadOnlyToolVerifier.verify(...)`
- 位置：`src/main/java/com/cup/opsagent/verifier/ReadOnlyToolVerifier.java:29`
- 问题描述：read-only verifier evidence 记录了 success、exitCode、durationMs、stdout/stderr 长度，但没有记录 `failureCode`。
- 为什么是问题：`TOOL_EXECUTED` audit 已经有 failureCode，所以主审计链路可观测性足够。但 verifier evidence 在排查 verification failed 时仍少一个结构化失败原因。
- 最小修复建议：可选增加 `"failureCode", result.failureCode() == null ? "none" : result.failureCode().name()`。不改变 verifier 核心判断。
- 建议新增测试：
  - `ReadOnlyToolVerifierTest.shouldIncludeFailureCodeInEvidenceWhenPresent`
  - `ReadOnlyToolVerifierTest.shouldIncludeFailureCodeNoneWhenAbsent`

### P2-4：`PROCESS_START_FAILED` / `COMMAND_TIMEOUT` / `COMMAND_INTERRUPTED` 仍未稳定单测覆盖

- 文件/类/方法：`CommandRunner`
- 位置：
  - `src/main/java/com/cup/opsagent/executor/CommandRunner.java:92`
  - `src/main/java/com/cup/opsagent/executor/CommandRunner.java:100`
  - `src/main/java/com/cup/opsagent/executor/CommandRunner.java:103`
- 问题描述：这三类 failureCode 已在生产分支填充，但没有稳定单测。
- 为什么是问题：这些分支属于 runner 框架级失败，测试缺口会降低防回归能力。直接用真实命令模拟会受 Windows/Kylin 差异影响。
- 最小修复建议：引入最小 `ProcessLauncher` 抽象，用 fake process 模拟启动失败、超时、中断。
- 建议新增测试：
  - `CommandRunnerTest.shouldSetFailureCodeForProcessStartFailed`
  - `CommandRunnerTest.shouldSetFailureCodeForCommandTimeout`
  - `CommandRunnerTest.shouldSetFailureCodeForCommandInterrupted`

### P2-5：`tool.execute` RuntimeException 兜底仍无结构化 code

- 文件/类/方法：`AgentOrchestrator.handle(...)`
- 位置：`src/main/java/com/cup/opsagent/agent/core/AgentOrchestrator.java:145`
- 问题描述：工具执行抛 RuntimeException 时，orchestrator 构造 7 参数 `ToolExecutionResult`，`failureCode=null`。
- 为什么是问题：这不是 runner 框架失败，不适合强塞进当前 `ToolExecutionFailureCode`。但从审计角度，它是另一类值得结构化的执行异常。
- 最小修复建议：本阶段保持不变。后续如果设计业务/执行状态码，可新增独立 `ToolExecutionStatusCode` 或扩展 enum，例如 `TOOL_EXECUTION_EXCEPTION`，但建议不要和当前 runner code 混在一个阶段里。
- 建议新增测试：等引入业务 statusCode 后再补。

## 3. 测试覆盖评价

### 当前覆盖哪些语义

当前新增测试覆盖良好：

1. `ToolExecutionResult` 不变量：
   - success=true 时拒绝 failureCode。
   - success=false 时允许 failureCode。
   - 7 参数构造器 failureCode 为 null。
   - stdout/stderr null 归一化为空字符串。
   - executedAt null 归一化为当前时间。

2. `CommandRunner` fail-fast failureCode：
   - `COMMAND_TEMPLATE_ID_MISSING`
   - `COMMAND_EXECUTION_OPTIONS_MISSING`
   - `INVALID_COMMAND_TEMPLATE_ARGS`
   - `COMMAND_SPEC_MISSING`
   - `SHELL_WRAPPER_DENIED`
   - `COMMAND_TEMPLATE_MISMATCH`

3. audit 可观测性：
   - `TOOL_EXECUTED` payload 中 failureCode 有值时记录枚举名。
   - `TOOL_EXECUTED` payload 中 failureCode 缺失时记录 `"none"`。

4. verifier evidence 可观测性：
   - `ServiceRestartVerifier` active/inactive 路径记录 `statusFailureCode=none`。
   - runner 层失败时记录具体 `statusFailureCode`。

### 仍缺哪些 failureCode

仍缺稳定单测：

1. `PROCESS_START_FAILED`
2. `COMMAND_TIMEOUT`
3. `COMMAND_INTERRUPTED`

这些不是逻辑遗漏，而是测试基础设施尚不可控。

### ProcessLauncher 是否必要

建议做。

原因：

- 可以稳定覆盖 `IOException`、timeout、interrupt；
- 避免用真实系统命令制造 flaky 测试；
- 让 `CommandRunner` 的职责更清晰：模板校验、shell wrapper 阻断、进程生命周期管理；
- 保持 `ProcessBuilder` 仍在受控执行层内，不扩大执行面。

### 如何最小化测试改造

推荐最小改造：

1. 保留现有 `public CommandRunner(CommandTemplateRegistry)` 构造器，Spring 不受影响。
2. 新增 package-private 构造器：

```java
CommandRunner(CommandTemplateRegistry registry, ProcessLauncher launcher)
```

3. 生产默认构造器委托给真实 launcher。
4. 测试在 `com.cup.opsagent.executor` 包内注入 fake launcher。
5. 只调整静态测试允许 `ProcessBuilder` 出现在真实 launcher 类中。

## 4. 下一阶段建议

### ProcessLauncher 最小接口

建议接口放在 `executor` 包内，先 package-private：

```java
interface ProcessLauncher {
    Process start(List<String> command) throws IOException;
}
```

真实实现：

```java
final class ProcessBuilderProcessLauncher implements ProcessLauncher {
    @Override
    public Process start(List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectInput(ProcessBuilder.Redirect.PIPE);
        return builder.start();
    }
}
```

### CommandRunner 构造器如何保持 Spring 兼容

保留当前 public 构造器：

```java
public CommandRunner(CommandTemplateRegistry commandTemplateRegistry) {
    this(commandTemplateRegistry, new ProcessBuilderProcessLauncher());
}
```

新增包内构造器用于测试：

```java
CommandRunner(CommandTemplateRegistry commandTemplateRegistry, ProcessLauncher processLauncher) {
    this.commandTemplateRegistry = Objects.requireNonNull(commandTemplateRegistry, "commandTemplateRegistry must not be null");
    this.processLauncher = Objects.requireNonNull(processLauncher, "processLauncher must not be null");
}
```

这样 Spring bean 创建路径不变，也不需要新增 Spring bean。

### fake Process 如何模拟 IOException、timeout、interrupt

1. `PROCESS_START_FAILED`
   - fake launcher 的 `start(...)` 直接抛 `IOException("boom")`。
   - 断言 `failureCode=PROCESS_START_FAILED`，stderr 为 `"boom"`。

2. `COMMAND_TIMEOUT`
   - fake launcher 返回 fake process。
   - fake process 的 `waitFor(timeout, unit)` 返回 false。
   - `isAlive()` 返回 true，`destroyForcibly()` 标记已调用。
   - 断言 `failureCode=COMMAND_TIMEOUT`。

3. `COMMAND_INTERRUPTED`
   - fake process 的 `waitFor(timeout, unit)` 抛 `InterruptedException`。
   - 断言 `failureCode=COMMAND_INTERRUPTED`。
   - 断言当前线程 interrupt flag 被恢复；测试结束前清理 interrupt flag，避免污染后续测试。

fake process 需要覆盖：

- `getInputStream()`
- `getErrorStream()`
- `waitFor(long, TimeUnit)`
- `exitValue()`
- `destroyForcibly()`
- `isAlive()`

### 静态架构测试如何调整 ProcessBuilder 允许范围

当前静态测试如果只允许 `ProcessBuilder` 出现在 `CommandRunner.java`，引入真实 launcher 后需要更新允许清单：

允许：

- `src/main/java/com/cup/opsagent/executor/ProcessBuilderProcessLauncher.java`

禁止：

- executor 包外任何 `new ProcessBuilder`
- 全生产任何 `Runtime.getRuntime().exec`
- 全生产任何 `.exec(`

如果希望更严格，可以改为：

- `CommandRunner` 不再允许 `new ProcessBuilder`
- 只有 `ProcessBuilderProcessLauncher` 允许 `new ProcessBuilder`

这会让“唯一进程启动点”更清晰。

## 5. 逐项复审结论

1. `ToolExecutionResult` compact constructor 合理。
2. success=true 禁止 failureCode 未发现破坏现有构造点。
3. stdout/stderr/executedAt null 归一化合理。
4. 建议补充 toolName 非空非 blank 校验，而不是归一化。
5. `TOOL_EXECUTED` payload 使用字符串 `"none"` 合适，便于审计查询，也避免 null payload 风险。
6. 不建议 audit payload 保留 null；当前项目 `payload(...)` 本来也会把 null 变成空字符串，`"none"` 更明确。
7. `ServiceRestartVerifier` evidence 中 `statusFailureCode` 足够，且没有改变验证逻辑。
8. 暂不建议额外改 `AgentResponse`。`StepResult` 已包含 `ToolExecutionResult`，序列化时自然可见；若前端需要摘要字段再单独设计。
9. 建议后续给 `ReadOnlyToolVerifier` evidence 也加入 failureCode，但不是阻塞项。
10. 建议进入 ProcessLauncher 阶段，以稳定覆盖三类进程生命周期 failureCode。
11. ProcessLauncher 最小改造路径清晰，能保持 Spring 兼容。
12. 当前无 P0/P1。
