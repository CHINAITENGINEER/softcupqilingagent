# QilingOS SafeOps Agent 项目驱动教学路线：第六至第十章

更新日期：2026-06-29

本文档从“概念一致性检查”调整为后续项目驱动教学骨架。

教学形式：

```text
先做项目 -> 形成工程闭环 -> 教学复盘 -> 用测试和文档固化 -> 继续下一条工程线
```

当前进度：

```text
第六章 Action Model：已完成主线理解
第七章 Constraint Checker：已完成主线理解
第八章 Verifier：已完成主线理解
第九章 Command Capability Boundary：已完成第一轮教学
第十章 Architecture Guardrails：已完成第一轮教学
```

后续教学原则：

```text
1. 先讲整体架构，不先罗列函数名。
2. 只截关键代码片段，不全文贴代码。
3. 每个片段都解释它在架构中的职责。
4. 每章配练习题，用学生回答校准理解。
5. 工程推进和教学复盘交替进行。
```

---

## 1. 总体教学主线

项目目标不是写一个普通 Agent demo，而是把 Agent 演进为：

```text
安全、受控、可验证、可审计、可测试的 SafeOps Agent
```

主链路：

```text
User Request
  -> Planner
  -> TaskPlan / PlanStep
  -> ToolCall
  -> PolicyEngine
  -> OpsTool
  -> CommandRunner
  -> VerificationOrchestrator
  -> AuditTrace
  -> AgentResponse
```

核心印象：

```text
Planner 只负责计划，不负责执行。
Tool 只表达能力，不直接拥有命令执行权。
Policy 负责执行前约束。
Command Boundary 负责命令能力收口。
Verifier 负责执行后判断目标是否达成。
Audit 负责全链路复盘。
Test Guardrails 负责防止未来代码绕过安全边界。
```

---

## 2. 第六章：Action Model

### 架构主线

```text
自然语言不能直接变成 shell。
自然语言必须先变成受限动作 ToolCall。
```

```text
User Input -> TaskPlanner -> TaskPlan -> PlanStep -> ToolCall -> ToolRegistry -> OpsTool
```

核心记忆点：

```text
Action 不是 command，Action 是受控 capability。
```

### 关键代码片段

文件：`src/main/java/com/cup/opsagent/tool/core/ToolCall.java`

```java
public record ToolCall(
        String toolName,
        Map<String, Object> arguments,
        String traceId,
        String stepId
) {
    ...
}
```

注释：

```text
toolName：Agent 申请哪个能力。
arguments：能力参数，但还不是命令参数。
traceId：把动作挂到审计链路。
stepId：把动作挂到计划步骤。
```

文件：`src/main/java/com/cup/opsagent/tool/core/ToolDefinition.java`

```java
public record ToolDefinition(
        String name,
        String description,
        Map<String, String> inputSchema,
        RiskLevel riskLevel,
        boolean readOnly,
        boolean requiresApproval,
        PermissionRequirement permissionRequirement,
        long timeoutMs,
        int outputLimitBytes,
        boolean enabled
) {
    ...
}
```

注释：

```text
ToolDefinition 是 PolicyEngine、ToolRegistry、Verifier 和 Audit 的安全元数据来源。
```

练习：

```text
1. 为什么 ToolCall 不应该包含 command 字段？
2. 为什么 ToolDefinition 要有 riskLevel 和 requiresApproval？
3. 为什么 ToolRegistry 不能让所有 OpsTool Bean 自动注册？
```

---

## 3. 第七章：Constraint Checker

### 架构主线

```text
Planner 输出不可信，ToolCall 执行前必须经过策略检查。
```

```text
ToolCall -> ToolRegistry -> PolicyContext -> PolicyEngine -> PolicyDecision
```

核心记忆点：

```text
PolicyEngine 是执行前闸门，不是优化建议。
```

### 关键代码片段

文件：`src/main/java/com/cup/opsagent/safety/PolicyDecision.java`

```java
public record PolicyDecision(
        PolicyDecisionType type,
        String reason
) {
    ...
}
```

注释：

```text
ALLOW：可以执行。
BLOCK：必须阻断。
WAITING_APPROVAL：不能自动执行，进入审批。
```

文件：`src/main/java/com/cup/opsagent/safety/policy/RiskLevelPolicy.java`

```java
if (definition.requiresApproval() || definition.riskLevel() == RiskLevel.MEDIUM) {
    return PolicyDecision.waitingApproval(...);
}
```

注释：

```text
restart_service 这类中风险动作不直接执行，而是进入 WAITING_APPROVAL。
```

练习：

```text
1. 为什么只检查 toolName 不够？
2. 为什么 DangerousIntentPolicy 要看 userInput？
3. 为什么 restart_service 不是 BLOCK，而是 WAITING_APPROVAL？
```

---

## 4. 第八章：Verifier

### 架构主线

```text
执行成功不等于任务成功。
```

```text
ToolExecutionResult -> VerificationContext -> VerificationOrchestrator -> ExecutionVerifier -> VerificationResult -> AgentResponse.status
```

核心区别：

```text
ToolExecutionResult.success：工具/命令有没有执行成功。
VerificationResult.verified：用户目标状态有没有达成。
```

### 关键代码片段

文件：`src/main/java/com/cup/opsagent/verifier/VerificationResult.java`

```java
public record VerificationResult(
        String verifierName,
        boolean verified,
        String reason,
        Map<String, Object> evidence,
        String suggestedRecovery,
        Instant checkedAt
) {
    ...
}
```

注释：

```text
verified 是结论，evidence 是证据，suggestedRecovery 是给人看的恢复建议。
```

文件：`src/main/java/com/cup/opsagent/verifier/ReadOnlyToolVerifier.java`

```java
if (!result.success()) {
    return VerificationResult.failed(...);
}
if (result.stdout().isBlank()) {
    return VerificationResult.failed(...);
}
return VerificationResult.passed(...);
```

注释：

```text
只读工具至少要执行成功，并且有可用输出。
```

文件：`src/main/java/com/cup/opsagent/verifier/ServiceRestartVerifier.java`

```java
ToolExecutionResult statusResult = commandRunner.run(
        "verify_service_active",
        CommandTemplateId.VERIFY_SERVICE_ACTIVE,
        Map.of("serviceName", serviceName),
        options
);
```

注释：

```text
restart_service 不能只看 restart 命令是否执行，还要二次检查服务是否 active。
```

练习：

```text
1. success=true 但 verified=false 代表什么？
2. 为什么 restart_service 要二次调用 VERIFY_SERVICE_ACTIVE？
3. 为什么 evidence 不应该保存完整 stdout/stderr？
```

---

## 5. 第九章：Command Capability Boundary

### 架构主线

```text
工具不能拥有自由命令执行能力，工具只能请求一个受控模板能力。
```

```text
Tool
  -> CommandRunner(templateId + args + options)
  -> CommandTemplateRegistry
  -> CommandSpec
  -> Runner 二次检查
  -> ProcessLauncher
  -> ProcessBuilderProcessLauncher
  -> OS Process
```

核心记忆点：

```text
Tool 不是命令作者，Tool 是能力申请者。
```

### 关键代码片段

文件：`src/main/java/com/cup/opsagent/executor/CommandRunner.java`

```java
public ToolExecutionResult run(
        String toolName,
        CommandTemplateId templateId,
        Map<String, Object> args,
        CommandExecutionOptions options
) {
    CommandSpec spec = commandTemplateRegistry.build(
            templateId,
            args,
            options.timeoutMs(),
            options.outputLimitBytes()
    );
    return run(toolName, spec, startedAt);
}
```

注释：

```text
对外入口没有 List<String> command。工具只能提交 templateId、args、options。
```

文件：`src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java`

```java
return switch (templateId) {
    case GET_SYSTEM_LOAD -> { ... }
    case CHECK_PORT_USAGE -> { ... }
    case GET_SERVICE_STATUS -> { ... }
    case VERIFY_SERVICE_ACTIVE -> { ... }
};
```

注释：

```text
所有可执行命令能力都必须出现在模板枚举中。
```

文件：`src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java`

```java
List.of("systemctl", "--no-pager", "status", "--", safeServiceName)
```

注释：

```text
命令形状固定，serviceName 先校验，-- 防止选项注入。
```

文件：`src/main/java/com/cup/opsagent/executor/CommandSpec.java`

```java
record CommandSpec(
        CommandTemplateId templateId,
        List<String> command,
        long timeoutMs,
        int outputLimitBytes
) {
    ...
}
```

注释：

```text
CommandSpec 没有 public，只能在 executor 包内流转，防止外层构造底层命令对象。
```

文件：`src/main/java/com/cup/opsagent/executor/CommandRunner.java`

```java
if (usesDeniedShellWrapper(spec.command())) {
    return result(..., ToolExecutionFailureCode.SHELL_WRAPPER_DENIED);
}
if (!commandTemplateRegistry.matches(spec)) {
    return result(..., ToolExecutionFailureCode.COMMAND_TEMPLATE_MISMATCH);
}
```

注释：

```text
即使拿到了 CommandSpec，执行前也要检查 shell wrapper 和 templateId/command 是否匹配。
```

文件：`src/main/java/com/cup/opsagent/executor/ProcessBuilderProcessLauncher.java`

```java
ProcessBuilder builder = new ProcessBuilder(command);
return builder.start();
```

注释：

```text
真正启动 OS 进程的位置只有一个。危险边界越小，越容易审计和替换。
```

练习：

```text
1. 为什么 Tool 是能力申请者，不是命令作者？
2. 如果每个工具自己写命令，会带来哪三类风险？
3. 为什么 registry 生成命令后，runner 还要 matches(spec)？
4. 为什么 ProcessBuilderProcessLauncher 要尽量小？
5. 用一句话总结 Command Capability Boundary。
```

参考答案：

```text
把危险的自由命令执行，收口成可枚举、可校验、可审计、可测试的模板能力。
```

---

## 6. 第十章：Architecture Guardrails

### 架构主线

```text
用测试把 Command Capability Boundary 锁住。
```

测试矩阵：

```text
模板生成测试：确认标准命令长什么样。
参数拒绝测试：确认非法参数、多余参数进不来。
模板匹配测试：确认伪装命令、错配 templateId、缺 -- 会失败。
Runner 防线测试：确认 shell wrapper 和 mismatch 在执行前被拦。
架构扫描测试：确认危险对象不出现在错误位置。
Fake 进程测试：确认外部进程异常不依赖真实 OS。
```

### 关键测试片段

文件：`src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java`

```java
assertBuilt(
    registry.getServiceStatus("nginx", 1000, 1024),
    CommandTemplateId.GET_SERVICE_STATUS,
    List.of("systemctl", "--no-pager", "status", "--", "nginx")
);
```

注释：

```text
标准模板形状被测试钉死。如果删掉 -- 或改错子命令，测试会失败。
```

```java
assertThatThrownBy(() -> registry.build(
        CommandTemplateId.GET_SYSTEM_LOAD,
        Map.of("unexpected", true),
        1000,
        1024
))
.isInstanceOf(IllegalArgumentException.class)
.hasMessageContaining("unexpected command template args");
```

注释：

```text
unknown args must fail。安全模板不静默忽略未知参数。
```

```java
assertThat(
    matches(CommandTemplateId.GET_SYSTEM_LOAD, List.of("ss", "-tulpn"))
).isFalse();
```

注释：

```text
templateId 不是随便贴的标签。GET_SYSTEM_LOAD 不能伪装成 ss -tulpn。
```

```java
assertThat(Modifier.isPublic(CommandSpec.class.getModifiers())).isFalse();
```

注释：

```text
如果未来有人把 CommandSpec 改成 public record，这个测试会失败。
```

```java
List<Path> offenders = productionJavaFiles().stream()
        .filter(path -> !path.toAbsolutePath().normalize().startsWith(executorPath))
        .filter(path -> contains(path, "CommandTemplateRegistry") || contains(path, "CommandSpec"))
        .toList();

assertThat(offenders).isEmpty();
```

注释：

```text
扫描生产代码，executor 包外不能碰 CommandSpec 或 CommandTemplateRegistry。
```

文件：`src/test/java/com/cup/opsagent/executor/CommandRunnerTest.java`

```java
ToolExecutionResult result = commandRunner.run("test_tool", new CommandSpec(
        CommandTemplateId.GET_SYSTEM_LOAD,
        List.of("sh", "-c", "echo unsafe"),
        1000,
        1024
));

assertThat(result.failureCode()).isEqualTo(ToolExecutionFailureCode.SHELL_WRAPPER_DENIED);
```

注释：

```text
即使有人构造了 CommandSpec，CommandRunner 执行前也要拦住 shell。
```

```java
CommandRunner runner = new CommandRunner(
    new CommandTemplateRegistry(),
    command -> { throw new IOException("boom"); }
);

ToolExecutionResult result = runner.run("test_tool", canonicalSystemLoadSpec());

assertThat(result.failureCode()).isEqualTo(ToolExecutionFailureCode.PROCESS_START_FAILED);
```

注释：

```text
不依赖真实 systemctl 或 Linux 命令，用 fake ProcessLauncher 稳定模拟进程启动失败。
```

### 观察目标

```text
1. 先看标准模板长什么样。
2. 再看非法参数怎么失败。
3. 再看注入和错配怎么失败。
4. 再看访问修饰符怎么锁边界。
5. 再看文件扫描怎么锁架构。
6. 最后看 fake process 怎么测外部异常。
```

练习：

```text
1. 如果 CommandSpec 被改成 public record，哪类测试会失败？
2. 如果 ServiceStatusTool 里直接 new ProcessBuilder，哪类测试会失败？
3. 如果服务状态模板里的 -- 被删掉，哪类测试会失败？
4. 如果 GET_SYSTEM_LOAD 开始接受 debug=true，哪类测试会失败？
5. 如果真实机器没有 systemctl，还能不能测试 PROCESS_START_FAILED？靠什么？
```

---

## 7. 第十一章：Approval Workflow

### 架构主线

Approval Workflow MVP 已形成闭环：

```text
AgentOrchestrator WAITING_APPROVAL
  -> ApprovalService.requestApproval
  -> ApprovalRecord(actionHash)
  -> /api/approvals/approve
  -> ExecutionLease
  -> /api/approvals/execute
  -> ApprovedActionExecutor
  -> ToolRegistry / RestartServiceTool
  -> CommandRunner(RESTART_SERVICE)
  -> VerificationOrchestrator
  -> AuditTrace
```

核心记忆点：

```text
审批不是按钮。
审批是绑定动作、签发一次性授权、消费授权、执行、验证和审计的一条安全链路。
```

### 关键代码片段

文件：`src/main/java/com/cup/opsagent/approval/ApprovalRecord.java`

```java
public record ApprovalRecord(
        String approvalId,
        String traceId,
        String stepId,
        String toolName,
        Map<String, Object> canonicalArguments,
        RiskLevel riskLevel,
        String actionHash,
        ApprovalStatus status,
        ...
) {
    ...
}
```

注释：

```text
ApprovalRecord 绑定的是规范化动作，不是用户自然语言。
actionHash 用于防止审批后参数被替换。
```

文件：`src/main/java/com/cup/opsagent/approval/ExecutionLease.java`

```java
public record ExecutionLease(
        String leaseId,
        String approvalId,
        String actionHash,
        String toolName,
        Map<String, Object> canonicalArguments,
        Instant issuedAt,
        Instant expiresAt,
        Instant consumedAt
) {
    ...
}
```

注释：

```text
ExecutionLease 是审批通过后的“一次性执行票据”。
它必须可过期、只能消费一次、消费时 actionHash 必须匹配。
```

文件：`src/main/java/com/cup/opsagent/approval/ApprovedActionExecutor.java`

```java
ExecutionLease consumedLease = approvalService.consumeLease(
        leaseId,
        toolName,
        arguments,
        definition.riskLevel()
);
```

注释：

```text
审批后执行前，必须先消费 lease。
如果参数被篡改、lease 过期或 lease 已消费，执行不会发生。
```

文件：`src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java`

```java
List.of("systemctl", "restart", "--", safeServiceName)
```

注释：

```text
restart_service 审批通过后仍然不能自由执行命令。
它必须走 RESTART_SERVICE 模板，并保留 -- 防选项注入。
```

### 测试矩阵

文件：`src/test/java/com/cup/opsagent/approval/ApprovalServiceTest.java`

```text
1. 创建 PENDING 审批记录。
2. actionHash 参数顺序不同仍稳定。
3. 参数内容变化时 actionHash 变化。
4. approve 后签发 lease。
5. reject 后不能 approve。
6. lease 只能消费一次。
7. 参数篡改时不能消费 lease。
8. approval 过期后不能 approve。
9. lease 过期后不能 consume。
```

文件：`src/test/java/com/cup/opsagent/approval/ApprovedActionExecutorTest.java`

```text
1. lease 消费后执行已审批工具。
2. 执行后进入 verifier。
3. 执行和验证进入 AuditTrace。
4. 参数被篡改时拒绝执行。
```

练习：

```text
1. 为什么审批不能只保存 userInput？
2. 为什么 actionHash 要绑定 canonical arguments？
3. 为什么审批通过后还需要 ExecutionLease？
4. 为什么 lease 必须一次性消费？
5. 为什么审批后执行仍然要走 CommandRunner 和 Verifier？
```

---

## 8. 第十二章：PostgreSQL 持久化设计

### 本阶段先做什么

本阶段没有直接引入 PostgreSQL，而是先抽象存储边界：

```text
ApprovalService -> ApprovalRepository -> InMemoryApprovalRepository
AuditLogService -> AuditTraceRepository -> InMemoryAuditTraceRepository
```

原因：

```text
长期项目不能让业务逻辑直接依赖存储细节。
如果 ApprovalService 直接写 ConcurrentHashMap，未来切 PostgreSQL 时会改业务逻辑。
如果 ApprovalService 只依赖 ApprovalRepository，未来只需要替换 repository 实现。
```

新增边界：

```text
ApprovalRepository
- saveApproval
- findApproval
- saveLease
- findLease

AuditTraceRepository
- save
- findByTraceId
- findAll
```

教学重点：

```text
Repository 不是为了“多写一层代码”。
Repository 是为了把“业务规则”和“存储方式”拆开。
业务规则应该回答：能不能审批、能不能执行、能不能消费 lease。
存储实现才回答：这些状态存在内存、PostgreSQL、文件，还是其他系统里。
```

测试重点：

```text
1. 原有 ApprovalServiceTest 继续通过，证明业务语义未改变。
2. 新增 ApprovalRepositoryContractTest，定义所有 ApprovalRepository 实现必须满足的行为。
3. InMemoryApprovalRepositoryTest 继承契约测试，证明内存实现符合契约。
4. 新增 AuditTraceRepositoryContractTest，定义所有 AuditTraceRepository 实现必须满足的行为。
5. InMemoryAuditTraceRepositoryTest 继承契约测试，证明审计内存实现符合契约。
6. Spring context test 继续通过，证明构造器注入没有破坏应用启动。
```

### Repository Contract Test

契约测试的目标不是验证某一个具体类，而是验证“一类实现”都必须遵守的行为。

```text
ApprovalRepositoryContractTest
  -> InMemoryApprovalRepositoryTest
  -> Future PostgresApprovalRepositoryTest

AuditTraceRepositoryContractTest
  -> InMemoryAuditTraceRepositoryTest
  -> Future PostgresAuditTraceRepositoryTest
```

这样做的价值：

```text
1. PostgreSQL 实现上线时，不需要重新想测试矩阵。
2. InMemory 和 PostgreSQL 会被同一套行为规则约束。
3. 业务服务不需要知道底层实现差异。
4. 如果 PostgreSQL 实现漏掉覆盖更新、空值返回、lease 保存等行为，契约测试会立刻失败。
```

教学记忆点：

```text
普通测试问：这个类现在对不对？
契约测试问：任何实现都必须怎么表现？
长期项目要多写契约测试，因为它保护的是未来演进。
```

### 下一阶段 PostgreSQL 架构主线

本章工程闭环已经完成：

```text
pom.xml
  -> spring-boot-starter-jdbc
  -> flyway-core
  -> flyway-database-postgresql
  -> postgresql driver
  -> h2 runtime for local/test compatibility

Flyway migration
  -> approval_records
  -> execution_leases
  -> audit_traces
  -> audit_events

Repository implementations
  -> InMemoryApprovalRepository @Profile("!postgres")
  -> JdbcApprovalRepository @Profile("postgres")
  -> InMemoryAuditTraceRepository @Profile("!postgres")
  -> JdbcAuditTraceRepository @Profile("postgres")

Contract tests
  -> InMemoryApprovalRepositoryTest
  -> JdbcApprovalRepositoryTest
  -> InMemoryAuditTraceRepositoryTest
  -> JdbcAuditTraceRepositoryTest
```

### Profile 切换

默认 profile：

```text
InMemory repository
H2 + Flyway schema 可启动，但业务存储仍走内存实现
```

postgres profile：

```text
JdbcApprovalRepository
JdbcAuditTraceRepository
PostgreSQL driver
Flyway schema migration
```

配置文件：

```text
application.properties
application-postgres.properties
```

运行 PostgreSQL profile 时可通过环境变量注入：

```text
SAFEOPS_POSTGRES_URL
SAFEOPS_POSTGRES_USER
SAFEOPS_POSTGRES_PASSWORD
```

### 本章关键取舍

```text
1. 没有直接删除 InMemory，因为比赛演示和本地开发不能被数据库依赖卡死。
2. 没有让业务服务感知 JDBC，因为持久化细节必须停留在 repository 层。
3. 没有为 InMemory 和 JDBC 写两套测试，而是复用同一套 contract test。
4. 审计契约测试不比较对象引用，而比较领域字段，因为 JDBC 会重建对象。
5. Flyway schema 使用文本保存 JSON 内容，先保证 H2/PostgreSQL 路径一致；后续生产化可迁移到 PostgreSQL jsonb。
```

### 本章测试成果

```text
mvn test
Tests run: 132
Failures: 0
Errors: 0
Skipped: 0
```

本章完成后，项目具备了真正的持久化演进能力：

```text
业务规则稳定
存储边界稳定
内存实现可演示
JDBC 实现可持久化
Flyway 可建表
Contract Test 可保护未来演进
```

### 下一章建议

下一章不建议继续扩数据库表，而建议做身份与权限边界：

```text
Actor / User / Role / Permission
Approval approver identity
API 鉴权前置设计
RBAC / ABAC 最小模型
```

原因：

```text
审批系统如果没有身份模型，approve/reject 只是字符串。
生产级审批必须知道“谁在批准、有没有权限批准、批准动作是否可审计”。
```

### PostgreSQL 生产化补强点

```text
ApprovalService / AuditLogService
  -> Repository interface
  -> PostgreSQL implementation
  -> transaction boundary
  -> query API
```

核心记忆点：

```text
PostgreSQL 存系统事实状态：审批、lease、审计、用户、权限、主机和策略。
Milvus 存语义记忆：runbook、历史故障、知识库 embedding。
```

### 表设计草案

```text
approval_records
- approval_id PK
- trace_id
- step_id
- tool_name
- canonical_arguments jsonb
- risk_level
- action_hash
- status
- reason
- created_at
- expires_at
- decided_at
- decided_by

execution_leases
- lease_id PK
- approval_id FK
- action_hash
- tool_name
- canonical_arguments jsonb
- issued_at
- expires_at
- consumed_at

audit_traces
- trace_id PK
- user_input
- status
- final_answer
- started_at
- finished_at

audit_events
- event_id PK
- trace_id FK
- event_type
- success
- payload jsonb
- message
- occurred_at
```

### 事务边界

```text
approve：读取 PENDING approval -> 检查过期 -> 更新 APPROVED -> 插入 lease。
consumeLease：读取 lease -> 检查未消费/未过期 -> 校验 actionHash -> 标记 consumed -> approval 标记 CONSUMED。
appendAudit：写入 audit_events，不允许覆盖历史事件。
```

### 练习

```text
1. 为什么 ApprovalRecord 和 ExecutionLease 不能只存在内存里？
2. consumeLease 为什么必须在事务里完成？
3. audit_events 为什么应该 append-only？
4. canonical_arguments 为什么适合用 jsonb？
```

---

## 10. 第十三章：Identity & Authorization MVP

### 工程目标

审批系统如果没有身份模型，`approve/reject` 只是字符串提交。
本章完成最小身份与权限边界：

```text
Actor
Role
Permission
ApproverPolicy
ApprovalRecord.requesterId
ApprovalController permission check
```

### 最小模型

```text
Actor
- actorId
- roles

Role
- OPERATOR
- APPROVER
- EXECUTOR
- ADMIN

Permission
- APPROVE_MEDIUM_RISK_ACTION
- REJECT_MEDIUM_RISK_ACTION
- EXECUTE_APPROVED_ACTION
```

权限关系：

```text
OPERATOR  -> no approval permission
APPROVER  -> approve/reject medium risk action
EXECUTOR  -> execute approved action
ADMIN     -> approve/reject/execute
```

### 核心安全规则

```text
1. 没有 APPROVE_MEDIUM_RISK_ACTION 不能 approve。
2. 没有 REJECT_MEDIUM_RISK_ACTION 不能 reject。
3. 没有 EXECUTE_APPROVED_ACTION 不能 execute approved action。
4. requester 不能 approve 自己发起的 approval。
```

关键字段：

```text
ApprovalRecord.requesterId
```

这让系统可以判断：

```text
谁发起了危险动作？
谁试图批准这个动作？
两者是不是同一个人？
```

### API MVP 设计

上一阶段 API request 中临时携带：

```text
actor
roles
```

本阶段已经推进到 header-based Actor extraction：

```text
X-Actor-Id: approver-1
X-Actor-Roles: APPROVER,EXECUTOR
```

Controller 不再从 request body 读取 actor/roles，而是通过 `ActorResolver.resolveFromHeaders(...)` 生成 Actor。

当前仍然不是最终认证系统，但它已经更接近真实链路：

```text
HTTP headers
  -> ActorResolver
  -> Actor
  -> ApproverPolicy
  -> ApprovalService / ApprovedActionExecutor
```

生产化下一步应替换为：

```text
HTTP Auth / JWT / Session / mTLS
  -> AuthenticatedPrincipal
  -> ActorContext
  -> ApproverPolicy
```

### 为什么现在不直接上 Spring Security

```text
1. 当前目标是建立领域授权规则，而不是先陷入认证框架配置。
2. ApproverPolicy 是核心业务安全规则，不能被 Controller 或框架细节吞掉。
3. 先把“谁能批、谁不能批、能不能自批”测试清楚，再接入真实认证系统。
```

### 测试矩阵

新增：

```text
ApproverPolicyTest
ActorResolverTest
```

覆盖：

```text
1. APPROVER 可以批准其他 requester 的动作。
2. OPERATOR 不能 approve。
3. requester 不能 approve 自己的动作。
4. EXECUTOR 可以执行 approved action。
5. APPROVER 不能 execute approved action。
6. X-Actor-Roles 支持逗号分隔多角色。
7. role 解析大小写不敏感。
8. roles header 为空时默认 OPERATOR。
9. actorId 为空时拒绝解析。
```

全量测试结果：

```text
mvn test
Tests run: 137
Failures: 0
Errors: 0
Skipped: 0
```

### 本章教学记忆点

```text
认证回答：你是谁？
授权回答：你能做什么？
审批安全还要回答：你能不能批准你自己发起的动作？
```

当前项目完成的是：

```text
领域授权 MVP
```

还没有完成的是：

```text
真实认证系统
API token/JWT
用户和角色持久化
权限管理界面
审计签名
```

如果下一步要进入 Spring Security/JWT/OAuth/RBAC 管理后台，就需要 Codex 参与架构审查。

---

## 11. API Error Handling Boundary

### 工程目标

把 Controller、授权策略、参数解析、审批状态机抛出的异常统一转成稳定 HTTP 响应。

```text
SecurityException        -> 403 FORBIDDEN
IllegalArgumentException -> 400 BAD_REQUEST
ValidationException      -> 400 BAD_REQUEST
NoSuchElementException   -> 404 NOT_FOUND
IllegalStateException    -> 409 CONFLICT
Unexpected Exception     -> 500 INTERNAL_ERROR
```

### 标准错误结构

```text
ApiErrorResponse
- code
- message
- status
- path
- timestamp
- details
```

### 新增组件

```text
ApiErrorResponse
GlobalApiExceptionHandler
GlobalApiExceptionHandlerTest
```

### 作用

调用方不再依赖 Java 异常文本和默认错误页面，而是依赖稳定错误 code：

```text
ACCESS_DENIED
BAD_REQUEST
VALIDATION_FAILED
RESOURCE_NOT_FOUND
STATE_CONFLICT
INTERNAL_ERROR
```

---

## 12. 生产化差距复盘

当前项目已经完成教学型安全 MVP 的核心闭环：

```text
Action Model
Constraint Checker
Verifier
Command Capability Boundary
Architecture Guardrails
Approval Workflow MVP
```

仍未完成的生产化能力：

```text
1. PostgreSQL 持久化 Approval/Audit。
2. 用户身份、RBAC、ABAC。
3. 审计防篡改 hash chain 或签名。
4. 真实 LLM structured output。
5. 多主机与远程执行。
6. 最小权限账户、sudoers、cgroup、seccomp/AppArmor。
7. Verifier 深度语义解析。
8. Milvus/RAG 运维知识库。
9. MCP 外部工具生态接入。
```

推荐后续顺序：

```text
PostgreSQL Approval/Audit
  -> RBAC / ABAC
  -> Verifier semantic deepening
  -> Milvus / RAG
  -> Remote execution
  -> MCP
```

