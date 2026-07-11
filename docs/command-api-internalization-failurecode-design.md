# QilingOS SafeOps Agent 命令执行 API 内部化复审与 failureCode 最小方案

复审日期：2026-06-28

复审范围：

- `src/main/java/com/cup/opsagent/executor/CommandSpec.java`
- `src/main/java/com/cup/opsagent/executor/CommandRunner.java`
- `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java`
- `src/main/java/com/cup/opsagent/executor/CommandExecutionOptions.java`
- `src/main/java/com/cup/opsagent/tool/builtin/*.java`
- `src/main/java/com/cup/opsagent/verifier/ServiceRestartVerifier.java`
- `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java`
- `src/test/java/com/cup/opsagent/executor/CommandRunnerTest.java`
- `src/test/java/com/cup/opsagent/OpsAgentApplicationContextTest.java`

说明：本轮只做静态复审和方案设计，没有修改 Java 生产代码，没有执行 `mvn test`。测试结果以用户提供为准：`BUILD SUCCESS, Tests run: 83, Failures: 0, Errors: 0`。

## 1. 总体结论

### 是否发现 P0/P1

未发现 P0 或 P1。

本轮命令执行 API 内部化已经落到 Java 可见性层面：

- `CommandSpec` 已是 package-private record。
- `CommandRunner.run(String toolName, CommandSpec spec)` 已是 package-private。
- `CommandTemplateRegistry.build(...)`、typed factory、`matches(CommandSpec)` 均已是 package-private。
- `CommandTemplateRegistry` 类本身仍是 public Spring bean。
- 工具和 verifier 生产代码只调用 `CommandRunner.run(toolName, templateId, args, options)`。

当前命令执行路径可以概括为：

```text
Tool / Verifier
  -> CommandRunner.run(toolName, templateId, args, options)
  -> CommandTemplateRegistry.build(...)
  -> package-private CommandSpec
  -> shell wrapper denylist
  -> CommandTemplateRegistry.matches(spec)
  -> ProcessBuilder
```

该链路已经符合 MVP 的安全闭环目标。

### 当前命令 API 是否闭环

是，当前命令 API 已经闭环。

从静态代码看，executor 包外生产代码已无法：

1. 声明或构造 `CommandSpec`；
2. 注入或调用 `CommandTemplateRegistry`；
3. 调用旧 spec run API；
4. 绕过 `CommandRunner` 直接使用 `ProcessBuilder`；
5. 使用 `Runtime.exec` 或其他 `.exec(` 形式。

`CommandTemplateRegistry` 类保持 public 不构成问题。Spring 只需要能发现和实例化 bean；其业务方法是否 public 不影响 `CommandRunner` 在同包内调用，也不影响当前工具/verifier 注入 `CommandRunner`。

### 是否建议进入 failureCode 阶段

建议进入 `ToolExecutionResult.failureCode` 阶段。

命令 API 收口已经完成，下一步最有价值的是把 runner 层失败从纯文本 `stderr` 结构化为机器可读的失败码。这样审计、前端、AgentResponse、测试都可以基于稳定枚举判断失败原因，而不是解析字符串。

建议将 failureCode 作为独立小阶段推进，不和更多命令 API 改造混在一起。

## 2. P0/P1/P2 问题列表

### P0

无。

### P1

无。

### P2-1：`ToolExecutionResult` 缺少结构化失败码

- 文件/类/方法：`ToolExecutionResult`
- 位置：`src/main/java/com/cup/opsagent/tool/core/ToolExecutionResult.java:5`
- 问题描述：当前失败原因仍通过 `stderr` 文本表达，例如 `invalid command template args`、`denied shell wrapper command`、`command does not match template`。
- 为什么是问题：审计、前端、测试和上层 orchestrator 如果要识别失败类型，只能解析字符串。字符串容易变化，也不利于统计和策略聚合。
- 最小修复建议：新增 `ToolExecutionFailureCode` enum，并在 `ToolExecutionResult` 增加 nullable `failureCode` 字段。第一阶段只让 `CommandRunner` 自身产生的框架级失败填 code。
- 建议新增测试：为 `CommandRunner` 每个 fail-fast 分支断言 `failureCode`。

### P2-2：`IOException.getMessage()` 可能为 null，失败信息可读性不足

- 文件/类/方法：`CommandRunner.run(String, CommandSpec, Instant)`
- 位置：`src/main/java/com/cup/opsagent/executor/CommandRunner.java:98`
- 问题描述：`IOException` 分支直接把 `exception.getMessage()` 写入 stderr。
- 为什么是问题：极端情况下 message 可能为 null，导致上层看到空失败原因。引入 failureCode 后，即使 stderr 为空也能识别 `PROCESS_START_FAILED`，但建议同时给 stderr 一个兜底文本。
- 最小修复建议：封装 `safeMessage(exception, "process start failed")`，stderr 为空时用默认值。
- 建议新增测试：用不可执行命令触发 `IOException`，断言 `failureCode=PROCESS_START_FAILED`，且 stderr 非空。

### P2-3：静态架构测试仍是字符串扫描，长期可升级为 ArchUnit

- 文件/类/方法：`CommandTemplateRegistryTest`
- 位置：
  - `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java:167`
  - `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java:177`
  - `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java:187`
  - `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java:199`
  - `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java:211`
- 问题描述：当前静态测试用 `Files.readString(...).contains(...)` 做源码扫描。
- 为什么是问题：对当前项目规模足够实用，但不是 AST/字节码级架构约束，存在少量误报或漏报空间。
- 最小修复建议：MVP 不必改。进入更大规模开发时，引入 ArchUnit，将“命令执行能力只能在 executor 包内”表达为架构规则。
- 建议新增测试：非必须。长期用 ArchUnit 覆盖：executor 包外不得依赖 `CommandSpec` / `CommandTemplateRegistry`，只有 `CommandRunner` 可依赖 `ProcessBuilder`。

### P2-4：`CommandTemplateRegistry` 类仍 public，但方法已 package-private

- 文件/类/方法：`CommandTemplateRegistry`
- 位置：`src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java:12`
- 问题描述：类本身仍是 public Spring bean，但 build/factory/matches 方法已 package-private。
- 为什么是问题：这不是安全问题。保留 public 类有利于 Spring bean 创建、测试注入和未来配置扩展；方法 package-private 已经把能力面收回 executor 包。
- 最小修复建议：无需修改。继续保留静态测试禁止 executor 包外生产代码出现 `CommandTemplateRegistry`。
- 建议新增测试：已有 `OpsAgentApplicationContextTest` 覆盖 Spring bean 创建；无需新增。

## 3. failureCode 最小设计

### enum 名称和位置

建议新增：

```java
package com.cup.opsagent.tool.core;

public enum ToolExecutionFailureCode {
    COMMAND_TEMPLATE_ID_MISSING,
    COMMAND_EXECUTION_OPTIONS_MISSING,
    INVALID_COMMAND_TEMPLATE_ARGS,
    COMMAND_SPEC_MISSING,
    SHELL_WRAPPER_DENIED,
    COMMAND_TEMPLATE_MISMATCH,
    PROCESS_START_FAILED,
    COMMAND_TIMEOUT,
    COMMAND_INTERRUPTED
}
```

命名理由：

- 放在 `tool.core`，因为 `ToolExecutionResult` 属于 tool core，failureCode 是执行结果的通用语义。
- 第一阶段只覆盖 `CommandRunner` 框架级失败，不覆盖业务状态。
- 不加入 `NON_ZERO_EXIT`，避免把业务语义误判为框架失败。

### ToolExecutionResult 字段如何加

建议在 record 中新增 nullable 字段：

```java
public record ToolExecutionResult(
        String toolName,
        boolean success,
        String stdout,
        String stderr,
        Integer exitCode,
        long durationMs,
        Instant executedAt,
        ToolExecutionFailureCode failureCode
) { ... }
```

语义约定：

- `failureCode == null` 表示没有框架级失败码。
- `success == true` 时 `failureCode` 应为 null。
- `success == false` 且 `failureCode == null` 可以表示业务层失败、命令正常执行但 exit code 非 0、人工审批跳过、平台不支持等非 runner 框架失败。

### 如何兼容现有构造点

当前 `new ToolExecutionResult(...)` 构造点不少，直接改 record 主构造会造成大面积编译改动。建议保留旧签名的重载构造器：

```java
public ToolExecutionResult(
        String toolName,
        boolean success,
        String stdout,
        String stderr,
        Integer exitCode,
        long durationMs,
        Instant executedAt
) {
    this(toolName, success, stdout, stderr, exitCode, durationMs, executedAt, null);
}
```

这样现有调用点可以先不动，第一阶段只改 `CommandRunner.result(...)` 增加可选 failureCode。

建议同时新增静态工厂，逐步减少直接 `new`：

```java
public static ToolExecutionResult failure(
        String toolName,
        String stderr,
        ToolExecutionFailureCode failureCode,
        long durationMs,
        Instant executedAt
)
```

但第一阶段不必强制迁移所有调用点，避免一次性改动过大。

### CommandRunner 哪些分支填哪个 code

第一阶段建议只填 `CommandRunner` 自身产生的框架级失败码：

| 分支 | 位置 | failureCode |
|---|---|---|
| `templateId == null` | `CommandRunner.java:49` | `COMMAND_TEMPLATE_ID_MISSING` |
| `options == null` | `CommandRunner.java:52` | `COMMAND_EXECUTION_OPTIONS_MISSING` |
| `CommandTemplateRegistry.build(...)` 抛 `IllegalArgumentException` | `CommandRunner.java:58` | `INVALID_COMMAND_TEMPLATE_ARGS` |
| `spec == null` | `CommandRunner.java:69` | `COMMAND_SPEC_MISSING` |
| shell wrapper denylist 命中 | `CommandRunner.java:72` | `SHELL_WRAPPER_DENIED` |
| `matches(spec) == false` | `CommandRunner.java:75` | `COMMAND_TEMPLATE_MISMATCH` |
| 超时 | `CommandRunner.java:91` | `COMMAND_TIMEOUT` |
| `IOException` | `CommandRunner.java:98` | `PROCESS_START_FAILED` |
| `InterruptedException` | `CommandRunner.java:100` | `COMMAND_INTERRUPTED` |

建议将 `CommandRunner.result(...)` 重载为两个版本：

```java
private ToolExecutionResult result(
        String toolName,
        boolean success,
        String stdout,
        String stderr,
        Integer exitCode,
        Instant startedAt
)

private ToolExecutionResult result(
        String toolName,
        boolean success,
        String stdout,
        String stderr,
        Integer exitCode,
        Instant startedAt,
        ToolExecutionFailureCode failureCode
)
```

旧调用默认 `failureCode=null`，失败分支显式传 code。

### 哪些分支暂不填 code

第一阶段建议以下场景暂不填 failureCode：

1. **命令执行完成但 exit code 非 0**
   - 位置：`CommandRunner.java:97`
   - 原因：非 0 可能是业务状态，不一定是执行框架失败。例如 `systemctl is-active` 非 0 可表示服务未 active，应该由 verifier 解释。

2. **`ToolExecutionResult.skipped(...)`**
   - 位置：`ToolExecutionResult.java:14`
   - 原因：当前 skipped 混合了审批跳过、Windows 环境跳过、参数非法跳过等语义。直接给一个统一 failureCode 反而会误导。可在第二阶段拆分业务 skipped code。

3. **工具层参数非法**
   - 例如 `PortUsageTool` 中 port 提取失败。
   - 原因：这是工具业务参数校验失败，和 runner 框架失败不同。后续可单独设计 `ToolSkippedReasonCode` 或扩展业务 failureCode。

4. **Verifier 判断失败**
   - 原因：Verifier 的失败属于验证语义，已有 `VerificationResult` 表达，不应塞进 `ToolExecutionResult.failureCode`。

5. **输出读取失败被写入 stdout/stderr 文本**
   - 位置：`CommandRunner.readLimitedSafely(...)`
   - 原因：当前读取失败不会中断命令执行主流程。是否单独建 code 需要更细的执行结果模型，第一阶段不做。

## 4. 测试方案

### CommandRunner fail-fast failureCode 测试

建议在 `CommandRunnerTest` 增加：

1. `runWithTemplateShouldSetFailureCodeForNullTemplateId`
   - 输入：`templateId=null`
   - 预期：`success=false`、`exitCode=null`、`failureCode=COMMAND_TEMPLATE_ID_MISSING`

2. `runWithTemplateShouldSetFailureCodeForNullOptions`
   - 输入：`options=null`
   - 预期：`failureCode=COMMAND_EXECUTION_OPTIONS_MISSING`

3. `runWithTemplateShouldSetFailureCodeForInvalidArgs`
   - 输入：`CHECK_PORT_USAGE + Map.of()`
   - 预期：`failureCode=INVALID_COMMAND_TEMPLATE_ARGS`

4. `runWithTemplateShouldSetFailureCodeForUnexpectedArgs`
   - 输入：`GET_SYSTEM_LOAD + Map.of("unexpected", true)`
   - 预期：`failureCode=INVALID_COMMAND_TEMPLATE_ARGS`

5. `runWithSpecShouldSetFailureCodeForNullSpec`
   - 输入：`spec=null`
   - 预期：`failureCode=COMMAND_SPEC_MISSING`

6. `runWithSpecShouldSetFailureCodeForShellWrapper`
   - 输入：`sh -c`
   - 预期：`failureCode=SHELL_WRAPPER_DENIED`

7. `runWithSpecShouldSetFailureCodeForTemplateMismatch`
   - 输入：`GET_SYSTEM_LOAD + python -c ...`
   - 预期：`failureCode=COMMAND_TEMPLATE_MISMATCH`

### timeout / interrupted / process start failed 测试

1. `runWithSpecShouldSetFailureCodeForTimeout`
   - 可使用 canonical template 不容易稳定触发 timeout。更稳妥的办法是为测试引入可控 `ProcessLauncher` 抽象，但这会扩大改造。
   - 最小方案：先不强求 timeout 单测；或在 executor 包内用极短 timeout 跑一个必定可执行但可能阻塞的命令。该方案平台差异较大，不推荐作为稳定单测。

2. `runWithSpecShouldSetFailureCodeForProcessStartFailed`
   - 输入：构造一个 `CommandSpec`，templateId 与 command 需要通过 `matches` 才能进入 `ProcessBuilder`。由于 canonical 命令如 `uptime` 在 Windows/Kylin 差异明显，直接依赖不存在命令会不稳定。
   - 推荐最小架构改造：引入 `ProcessLauncher` 或 `ProcessBuilderFactory` 仅供测试替身模拟 `IOException`。如果暂不引入抽象，可先不测该分支。

3. `runShouldSetFailureCodeWhenInterrupted`
   - 当前 `InterruptedException` 来自 `waitFor`，稳定模拟需要可控 process 抽象。
   - 推荐同上，通过 `ProcessLauncher` 测试替身模拟。

结论：fail-fast 分支可以立即测；timeout/interrupted/process start failed 如果不想引入抽象，不建议写脆弱的跨平台测试。

### skipped 结果是否需要 failureCode

第一阶段不建议。

`ToolExecutionResult.skipped(...)` 当前承载多种非执行状态：

- Windows 开发环境跳过；
- 参数非法跳过；
- `restart_service` 等待人工审批，不直接执行；
- 未来可能还有工具禁用、环境能力缺失。

这些状态不是同一种 runner 框架失败。第一阶段保持 `failureCode=null` 更稳妥。后续可以单独设计：

```java
public enum ToolExecutionStatusCode {
    SKIPPED_UNSUPPORTED_PLATFORM,
    SKIPPED_INVALID_ARGUMENT,
    SKIPPED_APPROVAL_REQUIRED,
    EXECUTION_FRAMEWORK_FAILURE
}
```

但这已经超出 failureCode 最小方案。

### verifier 是否应读取 failureCode

第一阶段不建议 verifier 依赖 failureCode。

理由：

1. Verifier 当前关注执行结果语义，例如 restart 后服务是否 active。
2. `failureCode` 第一阶段只表达 runner 框架级失败。
3. `ServiceRestartVerifier` 已通过 `statusSuccess`、`statusExitCode`、`serviceStatus`、`statusTemplateId` 记录 evidence。

建议 verifier 暂时只把 `failureCode` 作为可选 evidence：

- 如果 `statusResult.failureCode() != null`，可记录 `statusFailureCode`。
- 不要根据 failureCode 改变核心 verified 逻辑。

## 5. 复审逐项结论

1. `CommandSpec` package-private 不影响 Spring、工具、verifier。测试位于 executor 包内，仍可覆盖内部行为。
2. `CommandTemplateRegistry` factory/build/matches package-private 合理；它们现在是 executor 内部协作方法。
3. `CommandTemplateRegistry` 类 public 但方法 package-private 符合 Spring bean 预期；Spring 创建 bean 不要求业务方法 public。
4. 静态测试足以防止 executor 外重新依赖 `CommandSpec` / `CommandTemplateRegistry`，当前项目规模下可接受。
5. 旧 spec API 已经完全 executor 内部化。
6. 当前无 P0/P1。
7. 命令 API 收口上不需要继续大改；后续只需维护静态测试和可见性约束。
8. 建议下一步进入 `ToolExecutionResult.failureCode` 阶段。
9. failureCode 最小 enum 建议包含 9 个 runner 框架级失败码。
10. `ToolExecutionResult` 建议新增 nullable `failureCode` 字段，并保留旧 7 参数构造器以兼容现有构造点。
11. `CommandRunner` 第一阶段只给 fail-fast、超时、启动失败、中断分支填 failureCode。
12. 非 0 exit code、skipped、工具业务参数非法、verifier 失败第一阶段不填 failureCode。
13. 测试优先覆盖 fail-fast failureCode；timeout/interrupted/process start failed 建议等有可控 process 抽象后再稳定覆盖。
