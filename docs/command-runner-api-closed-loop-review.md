# QilingOS SafeOps Agent CommandRunner API 闭环收口静态复审

复审日期：2026-06-28

复审范围：

- `src/main/java/com/cup/opsagent/executor/CommandRunner.java`
- `src/main/java/com/cup/opsagent/executor/CommandSpec.java`
- `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java`
- `src/main/java/com/cup/opsagent/executor/CommandExecutionOptions.java`
- `src/main/java/com/cup/opsagent/tool/builtin/*.java`
- `src/main/java/com/cup/opsagent/verifier/ServiceRestartVerifier.java`
- `src/test/java/com/cup/opsagent/executor/CommandRunnerTest.java`
- `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java`
- `src/test/java/com/cup/opsagent/verifier/ServiceRestartVerifierTest.java`
- `src/test/java/com/cup/opsagent/OpsAgentApplicationContextTest.java`

说明：本轮只做静态复审，没有修改 Java 生产代码，没有执行 `mvn test`。测试结果以用户提供为准：`BUILD SUCCESS, Tests run: 82, Failures: 0, Errors: 0`。

## 1. 总体结论

### 是否发现 P0/P1

未发现 P0 或 P1。

本轮闭环收口后，旧 spec API 已从 public 降级为 package-private：

- `CommandRunner.run(String toolName, CommandSpec spec)` 位于 `CommandRunner.java:64`，已无 `public` 修饰符。
- executor 包外生产代码无法直接调用旧 spec API。
- 工具和 verifier 均通过新 API：`run(toolName, templateId, args, options)`。

从静态代码看，当前命令执行路径仍保持完整安全链路：

1. 工具/verifier 只传 `CommandTemplateId + args + CommandExecutionOptions`。
2. `CommandRunner` 调 `CommandTemplateRegistry.build(...)` 做 fail-closed 参数 schema 校验。
3. `invalid args` 和 `unexpected args` 在 `ProcessBuilder` 前返回失败。
4. build 成功后仍进入 package-private spec 路径。
5. spec 路径执行前继续做 shell wrapper denylist 和 `matches(spec)` 二次防线。
6. `ProcessBuilder` 仍只出现在 `CommandRunner`。

### 当前 API 闭环是否达到 MVP

达到 MVP 安全边界。

这轮修复把上一轮最大的剩余开放面，即 public 旧 spec API，收到了 executor 包内部。结合静态架构测试，当前生产代码已经很难绕过模板工厂和 runner 新 API 去手写命令数组。

### 是否建议继续内部化 CommandSpec

建议继续内部化，但不阻塞当前 MVP。

`CommandSpec` 仍是 public record，`CommandTemplateRegistry` 的 typed factory 仍是 public 并返回 `CommandSpec`。由于静态测试已经禁止 executor 包外生产代码使用 `CommandSpec` 和 `CommandTemplateRegistry`，这不是当前安全漏洞，而是架构封口的最后一段。

推荐下一阶段：

1. 将 `CommandTemplateRegistry` typed factory 方法降级为 package-private。
2. 保留 `build(...)` 给 `CommandRunner` 使用，后续也可降级为 package-private。
3. 将 `CommandSpec` 从 public 改为 package-private。
4. 保留 `CommandTemplateRegistryTest` 和 `CommandRunnerTest` 在 `com.cup.opsagent.executor` 包内测试内部对象。

## 2. P0/P1/P2 问题列表

### P0

无。

### P1

无。

### P2-1：`CommandSpec` 仍是 public 类型

- 文件/类/方法：`CommandSpec`
- 位置：`src/main/java/com/cup/opsagent/executor/CommandSpec.java`
- 问题描述：旧 spec API 已经 package-private，但 `CommandSpec` 本身仍为 public record。
- 为什么是问题：当前静态测试已禁止 executor 包外生产代码使用它，因此不是直接绕过风险。但 public 类型会继续向外表达“命令数组对象是公开建模对象”的信号，长期不利于 API 闭环。
- 最小修复建议：下一阶段将 `public record CommandSpec` 改为包内可见 `record CommandSpec`。测试类已在 `com.cup.opsagent.executor` 包内，理论上仍可继续覆盖内部行为。
- 建议新增测试：保留并强化 `shouldForbidCommandTemplateRegistryAndCommandSpecUsageOutsideExecutorProductionCode`，确保 executor 包外无法重新引入 `CommandSpec`。

### P2-2：`CommandTemplateRegistry` typed factory 仍为 public

- 文件/类/方法：
  - `CommandTemplateRegistry.getSystemLoad(...)`
  - `CommandTemplateRegistry.getTopProcesses(...)`
  - `CommandTemplateRegistry.getOpenPorts(...)`
  - `CommandTemplateRegistry.checkPortUsage(...)`
  - `CommandTemplateRegistry.getServiceStatus(...)`
  - `CommandTemplateRegistry.verifyServiceActive(...)`
- 位置：
  - `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java:54`
  - `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java:58`
  - `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java:67`
  - `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java:71`
  - `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java:82`
  - `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java:93`
- 问题描述：typed factory 仍对外 public，且返回 `CommandSpec`。
- 为什么是问题：当前 executor 包外生产代码被静态测试禁止使用 registry，因此没有实际生产绕过路径。但 typed factory 仍是公开 API，会让后续调用者误以为可以绕过 `CommandRunner.run(templateId,args,options)`，直接拿 spec。
- 最小修复建议：将 typed factory 改为 package-private，仅保留给 registry 内部和 executor 包内测试使用。`CommandRunner` 只依赖 `build(...)`。
- 建议新增测试：静态测试继续禁止 executor 包外出现 `CommandTemplateRegistry`，并增加对 public API 收口的反射/源码测试，确认 typed factory 不再是 public。

### P2-3：`CommandTemplateRegistry.build(...)` 仍是 public

- 文件/类/方法：`CommandTemplateRegistry.build(...)`
- 位置：`src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java:16`
- 问题描述：`build(...)` 是模板命令构造入口，目前仍是 public。
- 为什么是问题：当前静态测试已禁止 executor 包外生产代码使用 `CommandTemplateRegistry`，因此不是当前绕过风险。长期看，真正公开的命令执行 API 应只剩 `CommandRunner.run(templateId,args,options)`，registry 应成为 executor 内部组件。
- 最小修复建议：在 `CommandSpec` 内部化之后，再考虑将 `build(...)` 降级为 package-private。保留 `CommandTemplateRegistry` 类 public 以降低 Spring bean 创建和测试改造成本。
- 建议新增测试：`shouldKeepCommandTemplateRegistryMethodsPackagePrivateExceptConstructorOrSpringBeanNeed`，可用源码扫描或反射判断关键 factory/build 方法可见性。

### P2-4：静态测试依赖字符串扫描，建议保留但补强边界说明

- 文件/类/方法：
  - `CommandTemplateRegistryTest.shouldForbidCommandTemplateRegistryAndCommandSpecUsageOutsideExecutorProductionCode`
  - `CommandTemplateRegistryTest.shouldForbidOldCommandRunnerSpecApiOutsideExecutorProductionCode`
  - `CommandTemplateRegistryTest.shouldForbidNewCommandSpecOutsideCommandTemplateRegistryInProductionCode`
  - `CommandTemplateRegistryTest.shouldKeepProcessBuilderInsideCommandRunnerOnly`
  - `CommandTemplateRegistryTest.shouldRejectRuntimeExecInProductionCode`
- 位置：
  - `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java:154`
  - `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java:164`
  - `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java:174`
  - `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java:186`
  - `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java:198`
- 问题描述：当前静态测试使用 `Files.readString(...).contains(...)` 做源码扫描，简单有效，但不是 AST 级别约束。
- 为什么是问题：字符串扫描可能误报，例如普通文本中出现 `.exec(`；也可能漏掉反射式调用、间接封装或非常规写法。当前项目规模下可接受，但需要明确这是防回归护栏，不是形式化架构验证。
- 最小修复建议：MVP 继续保留字符串扫描。后续如果项目扩大，可引入 ArchUnit 或 Error Prone 自定义检查，把“命令执行能力只能在 executor 包内”的规则固化为架构测试。
- 建议新增测试：短期无需新增；长期用 ArchUnit 表达：
  - executor 包外不得依赖 `CommandSpec`。
  - executor 包外不得依赖 `CommandTemplateRegistry`。
  - 只有 `CommandRunner` 可以调用 `ProcessBuilder`。

### P2-5：失败语义仍通过 stderr 文本表达

- 文件/类/方法：`ToolExecutionResult`
- 位置：`src/main/java/com/cup/opsagent/tool/core/ToolExecutionResult.java`
- 问题描述：`invalid command template args`、`denied shell wrapper command`、`command does not match template` 等失败原因仍通过 `stderr` 字符串表达。
- 为什么是问题：审计、前端、AgentResponse 或后续 Policy/Verifier 如果需要区分失败类型，只能解析文本，稳定性弱，也不利于统计。
- 最小修复建议：下一阶段新增 `ToolExecutionFailureCode`，先覆盖 `CommandRunner` 自身产生的失败，不急于覆盖所有工具业务失败。
- 建议新增测试：为每个 runner fail-fast 分支断言 `failureCode`，同时保留现有 `stderr` 文本断言作为兼容。

## 3. 测试覆盖评价

### 已覆盖的安全边界

当前测试覆盖已经比较扎实：

1. `CommandExecutionOptions` 拒绝非正 timeout/output limit。
2. 新 API 覆盖 `templateId == null`。
3. 新 API 覆盖 `options == null`。
4. 新 API 覆盖缺失必需参数，例如 `CHECK_PORT_USAGE` 缺少 `port`。
5. 新 API 覆盖 unexpected args，例如 `GET_SYSTEM_LOAD + unexpected`。
6. invalid args 和 unexpected args 返回 `success=false`、`exitCode=null`、`stderr` 包含模板参数错误。
7. package-private spec API 仍覆盖 shell wrapper denylist，包括 `sh`、`/bin/sh`、`cmd.exe`、`powershell`。
8. package-private spec API 仍覆盖 template mismatch。
9. `CommandSpec` 防御性复制、拒绝 null/blank element、拒绝 null templateId。
10. `CommandTemplateRegistry` 覆盖 6 个 canonical template commands。
11. registry build 覆盖 fail-closed 参数 schema。
12. `CHECK_PORT_USAGE` 覆盖端口越界、无冒号、注入、`:+22`、`:00022`。
13. systemctl 模板覆盖缺少 `--`、子命令互换、注入 serviceName。
14. `ServiceNameValidator` 与模板层 serviceName 校验一致性。
15. executor 包外生产代码禁止出现 `CommandSpec`。
16. executor 包外生产代码禁止出现 `CommandTemplateRegistry`。
17. executor 包外生产代码禁止旧 spec run API。
18. `new CommandSpec(` 只能出现在 `CommandTemplateRegistry.java`。
19. `ProcessBuilder` 只能出现在 `CommandRunner.java`。
20. 全生产禁止 `Runtime.exec` / `.exec(`。
21. `ServiceRestartVerifier` 构造器 null fail-fast。
22. `ServiceRestartVerifier` evidence 包含 `statusTemplateId=VERIFY_SERVICE_ACTIVE`。
23. `ServiceRestartVerifier` 不暴露 systemctl stdout/stderr 原文。
24. Spring context 覆盖核心 bean 创建。

### 还缺哪些测试

没有必须阻塞 MVP 的测试缺口。建议补充的是下一阶段内部化和 failureCode 相关测试：

1. `CommandSpec` 改为 package-private 后，保留 executor 包内单元测试，确认防御性复制和参数校验不丢。
2. `CommandTemplateRegistry` typed factory 改为 package-private 后，增加可见性测试或源码扫描，确认不再 public。
3. 如果 `build(...)` 也降级为 package-private，增加 Spring context 测试，确认 `CommandRunner` 仍可通过构造器注入 registry 并运行新 API。
4. 如果引入 `failureCode`，为以下分支增加断言：
   - templateId missing
   - options missing
   - invalid template args
   - spec missing
   - shell wrapper denied
   - template mismatch
   - process start failed
   - timeout
   - interrupted

### 静态测试是否需要继续增强

短期不需要为 MVP 继续增强。

当前静态测试已经覆盖主要绕过面。下一步如果要增强，建议从字符串扫描升级为更清晰的架构规则，而不是继续堆更多 `contains(...)`：

1. 引入 ArchUnit。
2. 规则一：`..tool..`、`..verifier..`、`..agent..`、`..controller..` 不得依赖 `CommandSpec` / `CommandTemplateRegistry`。
3. 规则二：只有 `CommandRunner` 可依赖 `ProcessBuilder`。
4. 规则三：生产代码禁止依赖 `java.lang.Runtime.exec`。
5. 规则四：`CommandRunner` 是唯一命令执行入口。

## 4. 下一阶段建议

### CommandSpec 是否 package-private

建议做，优先级 P2。

理由：

- 旧 spec API 已经 package-private。
- executor 包外生产代码已禁止使用 `CommandSpec`。
- 测试类在 `com.cup.opsagent.executor` 包内，改为 package-private 后仍可测试。
- 这一步能让“命令数组对象是 executor 内部细节”在 Java 可见性层面成立。

最小路径：

1. 将 `public record CommandSpec` 改为 `record CommandSpec`。
2. 保持现有 executor 包内测试。
3. 跑 `mvn test`，重点看非 executor 包是否有编译依赖。

### CommandTemplateRegistry typed factory 是否 package-private

建议做，优先级 P2，最好和 `CommandSpec` package-private 同一阶段或后一阶段做。

理由：

- 工具和 verifier 已经不调用 typed factory。
- `CommandRunner` 只调用 `build(...)`。
- typed factory 主要服务 registry 内部和 executor 包测试。

最小路径：

1. 将 `getSystemLoad(...)`、`getTopProcesses(...)`、`getOpenPorts(...)`、`checkPortUsage(...)`、`getServiceStatus(...)`、`verifyServiceActive(...)` 从 public 改为 package-private。
2. 保持 `CommandTemplateRegistryTest` 在 executor 包内。
3. 新增源码或反射测试，确认这些 typed factory 不再 public。

### `CommandTemplateRegistry.build(...)` 是否 package-private

可以做，但建议排在 typed factory 之后。

当前 `build(...)` 只有 `CommandRunner` 需要调用。若没有其他生产调用者，可降级为 package-private，让 registry 完全成为 executor 包内部协作者。

最小路径：

1. 确认 `CommandRunner` 是唯一生产调用方。
2. 将 `build(...)` 改为 package-private。
3. 保持 `CommandTemplateRegistry` 类本身 public 或至少确保 Spring 能稳定创建 bean。
4. 跑 Spring context 测试。

### ToolExecutionResult.failureCode 是否现在做

建议作为下一小阶段做，不建议和 `CommandSpec` 内部化混在一起。

原因：

- API 闭环已经达到 MVP。
- `failureCode` 会改动 `ToolExecutionResult` record 构造签名，影响面比可见性收口更大。
- 单独做可以更清楚地区分“命令执行 API 收口”和“执行结果语义结构化”。

### failureCode 最小枚举

建议先只覆盖 `CommandRunner` 自身生成的失败：

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
    COMMAND_INTERRUPTED
}
```

暂不建议第一阶段加入 `NON_ZERO_EXIT`。

理由：非 0 exit code 不一定是 runner 层失败。例如 `systemctl is-active` 返回非 0 可能是 verifier 需要解释的业务状态，而不是命令执行框架错误。是否引入 `NON_ZERO_EXIT` 应由工具/verifier 语义决定。

### failureCode 最小分阶段路径

1. 新增 `ToolExecutionFailureCode` enum。
2. 给 `ToolExecutionResult` 增加 nullable `failureCode` 字段，或新增一个轻量 metadata 字段。
3. 为兼容现有构造点，优先增加静态工厂方法，而不是让所有调用点直接 new record：
   - `ToolExecutionResult.commandFailure(...)`
   - `ToolExecutionResult.processResult(...)`
   - `ToolExecutionResult.skipped(...)`
4. 第一阶段只改 `CommandRunner` 的 fail-fast 分支填 failureCode。
5. 保留 stderr 文本，避免前端和审计展示立刻破坏。
6. 测试覆盖每个 failureCode。
7. 第二阶段再评估工具层业务失败是否需要独立 code，例如 `WINDOWS_SKIPPED`、`INVALID_TOOL_ARGUMENT`、`APPROVAL_REQUIRED`。

## 5. 逐项复审结论

1. 旧 spec API 降级为 package-private 未破坏 Spring 或工具/verifier 调用；工具/verifier 均走新 public API。
2. executor 包外生产代码当前不应再能使用 `CommandSpec` 或 `CommandTemplateRegistry`，静态测试已有覆盖。
3. 静态测试排除 executor 包的方式对当前项目结构可用；主要局限是字符串扫描不是 AST 级验证。
4. 新 API 覆盖当前所有生产执行场景：系统负载、进程、端口、端口占用、服务状态、服务重启验证。
5. invalid args 和 unexpected args 均在 `ProcessBuilder` 前失败。
6. shell wrapper denylist 和 `matches(spec)` 二次防线仍保留。
7. `ServiceRestartVerifier` 使用 `statusTemplateId + serviceName` 作为 evidence 足够，且比记录完整 systemctl 命令更安全。
8. 当前无 P0/P1。
9. 建议继续将 `CommandSpec` 改为 package-private。
10. 建议继续将 `CommandTemplateRegistry` typed factory 改为 package-private。
11. 建议下一步单独做 `ToolExecutionResult.failureCode`，但不要阻塞 API 闭环收口。
