# QilingOS SafeOps Agent failureCode 不变量与 Verifier Evidence 静态复审

复审日期：2026-06-28

复审范围：

- `src/main/java/com/cup/opsagent/tool/core/ToolExecutionResult.java`
- `src/main/java/com/cup/opsagent/tool/core/ToolExecutionFailureCode.java`
- `src/main/java/com/cup/opsagent/verifier/ReadOnlyToolVerifier.java`
- `src/test/java/com/cup/opsagent/tool/core/ToolExecutionResultTest.java`
- `src/test/java/com/cup/opsagent/verifier/ReadOnlyToolVerifierTest.java`
- 所有 `new ToolExecutionResult(...)` 调用点
- 所有 `ToolExecutionResult.skipped(...)` 调用点
- `AgentOrchestrator` / verifier 中读取 `ToolExecutionResult` 的地方

说明：本轮只做静态复审，没有修改 Java 生产代码，没有执行 `mvn test`。测试结果以用户提供为准：`BUILD SUCCESS, Tests run: 99, Failures: 0, Errors: 0`。

## 1. 总体结论

### 是否发现 P0/P1

未发现 P0 或 P1。

当前 `ToolExecutionResult` 不变量加固和 `ReadOnlyToolVerifier` failureCode evidence 增强是合理的：

- `toolName` 非空非 blank fail-fast 不会破坏当前调用点。
- `durationMs >= 0` 校验合理，能防止审计指标被非法值污染。
- `success=true` 时禁止 `failureCode` 仍然合理，当前成功路径不会携带 failureCode。
- `stdout/stderr/executedAt` null 归一化合理，降低 audit/verifier 空值防御成本。
- `skipped(...)` 仍保持 `failureCode=null`，没有混淆审批跳过、平台跳过、参数非法和 runner 框架失败。
- `ReadOnlyToolVerifier` evidence 新增 `failureCode` 后，read-only 验证失败也能看到结构化执行失败原因。

### 当前 failureCode 工程线是否可收尾

可以收尾。

这条工程线目前已经完成：

1. failureCode enum。
2. `ToolExecutionResult` 字段与不变量。
3. 7 参数构造器兼容。
4. `CommandRunner` runner 框架级 failureCode 映射。
5. `ProcessLauncher` 抽象。
6. `PROCESS_START_FAILED` / `COMMAND_TIMEOUT` / `COMMAND_INTERRUPTED` 稳定单测。
7. `TOOL_EXECUTED` audit payload 可观测。
8. `ServiceRestartVerifier.statusFailureCode`。
9. `ReadOnlyToolVerifier.failureCode` evidence。

从 MVP 安全边界和测试覆盖看，failureCode 工程线已经具备闭环。

### 是否建议进入课程文档同步阶段

建议进入。

当前代码已经明显超出最初第 6～8 章概念对齐文档中的“简化版”状态，新增了几个很适合课程讲解的生产化概念：

- `CommandTemplateRegistry`：Action Model 的命令模板化。
- `CommandRunner + ProcessLauncher`：受控执行边界和可测试执行层。
- `ToolExecutionFailureCode`：失败语义结构化。
- audit payload / verifier evidence：安全运维 Agent 的可观测性闭环。
- 静态架构测试：防止能力绕过的工程护栏。

建议下一步更新课程规划文档，并新增第 9 章、第 10 章。

## 2. P0/P1/P2 问题列表

### P0

无。

### P1

无。

### P2-1：ReadOnlyToolVerifier evidence 字段名 `failureCode` 可更明确

- 文件/类/方法：`ReadOnlyToolVerifier.verify(...)`
- 位置：`src/main/java/com/cup/opsagent/verifier/ReadOnlyToolVerifier.java:33`
- 问题描述：read-only verifier evidence 中字段名为 `failureCode`，语义实际是 `executionResult.failureCode()`。
- 为什么是问题：当前不会造成功能错误，但从审计语义上看，`failureCode` 可能被误解为 verifier 自身失败码，而不是工具执行失败码。`ServiceRestartVerifier` 已使用更具体的 `statusFailureCode`。
- 最小修复建议：可选改名为 `executionFailureCode`。如果已有测试或前端依赖 `failureCode`，可以短期同时保留两个字段，下一阶段再移除旧字段。
- 建议新增测试：
  - `ReadOnlyToolVerifierTest.shouldIncludeExecutionFailureCodeInEvidenceWhenPresent`
  - `ReadOnlyToolVerifierTest.shouldIncludeExecutionFailureCodeNoneWhenAbsent`

### P2-2：AgentResponse 是否暴露 failureCode 需要产品语义决策

- 文件/类/方法：`AgentResponse` / `StepResult`
- 位置：
  - `src/main/java/com/cup/opsagent/agent/model/AgentResponse.java`
  - `src/main/java/com/cup/opsagent/agent/model/StepResult.java`
- 问题描述：`AgentResponse` 没有新增摘要字段，但 `StepResult.executionResult` 已经包含 `ToolExecutionResult.failureCode`。
- 为什么是问题：如果前端直接序列化 `StepResult.executionResult`，failureCode 已可见；如果前端只看顶层摘要，则不能快速看到失败码。当前不是安全问题，而是响应模型设计问题。
- 最小修复建议：MVP 暂不新增顶层摘要字段。等前端或课程演示需要时，再考虑在 `StepResult` 增加 `executionFailureCode` 摘要，避免顶层 `AgentResponse` 过载。
- 建议新增测试：暂不需要。若新增摘要字段，再补 `AgentOrchestratorTest.shouldExposeExecutionFailureCodeInStepResult`。

### P2-3：skipped / 业务失败仍没有独立 statusCode

- 文件/类/方法：
  - `ToolExecutionResult.skipped(...)`
  - `AgentOrchestrator.handle(...)`
  - builtin tools
- 位置：
  - `src/main/java/com/cup/opsagent/tool/core/ToolExecutionResult.java:42`
  - `src/main/java/com/cup/opsagent/agent/core/AgentOrchestrator.java:99`
  - `src/main/java/com/cup/opsagent/agent/core/AgentOrchestrator.java:112`
- 问题描述：`failureCode` 只表达 runner 框架级失败，skipped 和业务失败仍通过 stderr/reason 表达。
- 为什么是问题：这是刻意边界，不是缺陷。但后续如果要把审批等待、策略阻断、平台不支持、参数非法都结构化，需要独立 statusCode，不应塞进当前 `ToolExecutionFailureCode`。
- 最小修复建议：failureCode 工程线收尾后，单独设计 `ToolExecutionStatusCode` 或 `SkipReasonCode`。
- 建议新增测试：等 statusCode 设计后再补。

### P2-4：课程文档仍需同步最新工程线

- 文件/类/方法：`docs/course-ch06-ch08-alignment-review.md`
- 问题描述：现有课程对齐文档较早，未完整反映 CommandTemplateRegistry、ProcessLauncher、failureCode、audit/evidence 可观测性和架构测试这些新增能力。
- 为什么是问题：课程材料如果不更新，会低估当前代码的生产化程度，也会错过第 9～10 章的自然主题。
- 最小修复建议：更新 `course-ch06-ch08-alignment-review.md`，并新增第 9 章 Audit & Failure Semantics、第 10 章 Testing & Architecture Guardrails 的规划。
- 建议新增测试：文档任务无需测试。

## 3. 测试覆盖评价

### 当前覆盖了哪些不变量

`ToolExecutionResultTest` 已覆盖：

1. `toolName == null` 被拒绝。
2. `toolName` blank 被拒绝。
3. `durationMs < 0` 被拒绝。
4. `success=true` 且 `failureCode != null` 被拒绝。
5. `success=false` 允许携带 failureCode。
6. 7 参数构造器默认 `failureCode=null`。
7. `stdout==null` 和 `stderr==null` 归一化为空字符串。
8. `executedAt==null` 归一化为非 null 时间。

这些覆盖足够支撑当前不变量。

### 当前覆盖了哪些 failureCode 可观测路径

当前已覆盖：

1. `CommandRunner` fail-fast failureCode：
   - `COMMAND_TEMPLATE_ID_MISSING`
   - `COMMAND_EXECUTION_OPTIONS_MISSING`
   - `INVALID_COMMAND_TEMPLATE_ARGS`
   - `COMMAND_SPEC_MISSING`
   - `SHELL_WRAPPER_DENIED`
   - `COMMAND_TEMPLATE_MISMATCH`

2. `ProcessLauncher` 后的进程生命周期 failureCode：
   - `PROCESS_START_FAILED`
   - `COMMAND_TIMEOUT`
   - `COMMAND_INTERRUPTED`

3. audit 可观测性：
   - `TOOL_EXECUTED.failureCode` 有值时记录枚举名。
   - `TOOL_EXECUTED.failureCode` 无值时记录 `"none"`。

4. verifier evidence：
   - `ServiceRestartVerifier.statusFailureCode=none`。
   - `ServiceRestartVerifier.statusFailureCode=PROCESS_START_FAILED`。
   - `ReadOnlyToolVerifier.failureCode=none`。
   - `ReadOnlyToolVerifier.failureCode=PROCESS_START_FAILED`。

### 是否还有测试缺口

没有阻塞 failureCode 工程线收尾的测试缺口。

可选增强：

1. 如果将 `ReadOnlyToolVerifier` 字段名改为 `executionFailureCode`，补对应测试。
2. 如果未来新增 `ToolExecutionStatusCode` / skipped 结构化状态，补 BLOCK、WAITING_APPROVAL、Windows skipped、参数非法 skipped 的测试。
3. 如果 AgentResponse 新增摘要字段，补响应序列化或 orchestrator 断言。

## 4. 下一阶段建议

### 是否更新 `course-ch06-ch08-alignment-review.md`

建议更新。

建议把文档从“第 6～8 章概念对齐”升级为“第 6～10 章演进路线”，明确哪些概念已经从示例走向 MVP 工程化。

### 如何把 CommandTemplateRegistry / failureCode / ProcessLauncher 纳入课程

建议如下安排：

1. 第 6 章 Action Model：
   - ToolCall 不是 shell。
   - ToolDefinition 描述能力边界。
   - `CommandTemplateId` 和 `CommandTemplateRegistry` 把 OS 命令收敛为模板化 Action。
   - 工具层只传 `templateId + args + options`，不手写命令数组。

2. 第 7 章 Constraint Checker：
   - ToolRegistry allowlist。
   - PolicyEngine。
   - 参数 validator。
   - CommandTemplateRegistry `matches(spec)` 二次防线。
   - 静态架构测试防止绕过。

3. 第 8 章 Verifier：
   - ReadOnlyToolVerifier。
   - ServiceRestartVerifier。
   - verifier evidence 不暴露 stdout/stderr 原文。
   - `statusTemplateId/statusFailureCode` 作为验证证据。

4. 第 9 章 Audit & Failure Semantics：
   - `ToolExecutionFailureCode`。
   - `TOOL_EXECUTED.failureCode`。
   - failureCode 与 skipped/statusCode 的边界。
   - audit trace 如何帮助排障和合规复盘。

5. 第 10 章 Testing & Architecture Guardrails：
   - CommandRunnerTest。
   - CommandTemplateRegistryTest。
   - ProcessLauncher fake process。
   - 静态扫描：禁止 Runtime.exec、限制 ProcessBuilder、禁止 executor 外依赖 CommandSpec/CommandTemplateRegistry。

### 是否建议新增第 9 章 Audit & Failure Semantics

建议新增。

这一章可以讲清楚很多真实系统会踩的坑：

- 为什么不能只靠 stderr 文本。
- 为什么非 0 exit code 不一定是框架失败。
- 为什么 skipped 不应该混入 runner failureCode。
- audit payload 如何承载机器可读失败语义。
- verifier evidence 如何保留足够证据但避免泄露敏感输出。

### 是否建议新增第 10 章 Testing & Architecture Guardrails

建议新增。

当前项目已经有足够好的案例：

- `ProcessLauncher` 让进程生命周期失败可测。
- `CommandTemplateRegistryTest` 做架构防回归。
- 静态测试限制命令执行原语。
- 包可见性把危险对象收回 executor 包内。
- 单元测试覆盖安全边界，而不是只测 happy path。

这章会非常适合作为“从 demo 到可维护安全工程”的收束。

## 5. 逐项复审结论

1. `toolName` 非空非 blank fail-fast 不会破坏现有调用点。
2. `durationMs` 非负校验合理。
3. `success=true` 禁止 failureCode 仍合理。
4. `stdout/stderr/executedAt` null 归一化仍合理。
5. `ToolExecutionResult.skipped(...)` 语义仍正确，failureCode 保持 null。
6. `ReadOnlyToolVerifier` evidence 加 failureCode 已足够支撑当前排障。
7. 字段名 `failureCode` 有轻微语义歧义，可选改为 `executionFailureCode`。
8. 当前其他 verifier 中 `ServiceRestartVerifier` 已记录 `statusFailureCode`，暂无更多必须补的 verifier。
9. 暂不建议新增 AgentResponse 顶层 failureCode 摘要字段。
10. 当前 failureCode 工程线可以收尾。
11. 未发现 P0/P1。
12. 建议下一步更新课程第 6～10 章规划文档。
