# 17.1 Audit Trace 源码走读

这一节按老师带读源码的方式来走。

建议你一边打开源码一边读，不要只看文字。

推荐顺序：

```text
AuditEventType
  -> AuditEvent
  -> AuditTrace
  -> AuditHasher
  -> AuditLogService
  -> AuditIntegrityService
  -> AgentOrchestrator
  -> ApprovedActionExecutor
  -> AuditController
```

---

# 1. 先看 AuditEventType：系统到底记录哪些事

文件：

```text
src/main/java/com/cup/opsagent/audit/AuditEventType.java
```

它是审计事件枚举。

不要把它当成普通 enum 看，它其实是系统行为边界清单。

当前事件可以分成几组。

## 1.1 请求头尾

```text
RECEIVE_REQUEST
FINAL_RESPONSE
```

一条 trace 通常从 `RECEIVE_REQUEST` 开始，以 `FINAL_RESPONSE` 结束。

注意：

```text
FINAL_RESPONSE 只代表系统给了最终响应，不代表业务成功。
```

## 1.2 Planner / LLM

```text
PLAN_GENERATED
PLANNING_FAILED
LLM_PLANNING_STARTED
LLM_PLANNING_COMPLETED
LLM_PLANNING_FAILED
LLM_PLAN_REJECTED
```

这里要区分三层：

```text
PLAN_GENERATED：通用计划生成成功。
PLANNING_FAILED：通用规划失败终态。
LLM_*：LLM planner 专用细节。
```

老师提醒：

```text
LLM_PLANNING_FAILED 是 provider 调用失败。
LLM_PLAN_REJECTED 是模型输出被系统验证拒绝。
```

## 1.3 RAG

```text
RAG_RETRIEVAL_STARTED
RAG_RETRIEVAL_COMPLETED
RAG_RETRIEVAL_FAILED
```

RAG 是后来接入的，但也挂在同一条 trace 上。

这说明：

```text
RAG 不是独立日志，它属于一次用户请求的证据链。
```

## 1.4 Tool / Policy / Verification

```text
TOOLCALL_PARSED
TOOL_VALIDATED
POLICY_CHECKED
EXECUTION_STARTED
TOOL_EXECUTED
VERIFICATION_STARTED
VERIFICATION_PASSED
VERIFICATION_FAILED
EXECUTION_SKIPPED
EXECUTION_BLOCKED
```

这组是安全执行闭环。

复盘时重点看：

```text
POLICY_CHECKED：为什么放行、阻断或等待审批？
TOOL_EXECUTED：工具有没有执行，执行结果如何？
VERIFICATION_*：执行后验证是否通过？
```

## 1.5 Approval

```text
APPROVAL_REQUIRED
APPROVAL_GRANTED
APPROVAL_REJECTED
EXECUTION_LEASE_CONSUMED
APPROVED_ACTION_EXECUTED
```

这组证明高风险操作经过了人工审批，不是偷偷执行。

---

# 2. 再看 AuditEvent：一条审计事件长什么样

文件：

```text
src/main/java/com/cup/opsagent/audit/AuditEvent.java
```

核心字段：

```text
traceId
eventType
eventTime
success
payload
errorMessage
previousHash
eventHash
```

你可以这样理解：

```text
AuditEvent = 某个时间点发生的一件可审计事情
```

## 2.1 构造器里的防御

`AuditEvent` 会检查：

```text
traceId 不能为空
eventType 不能为空
eventTime 为空就用当前时间
payload 为空就用空 Map
errorMessage 为空就用空字符串
previousHash / eventHash 为空就用空字符串
```

这说明审计事件不能是“半成品”。

## 2.2 success / failure 工厂方法

```java
AuditEvent.success(traceId, eventType, payload)
AuditEvent.failure(traceId, eventType, payload, errorMessage)
```

业务代码一般用这两个方法创建事件。

记忆：

```text
success/failure 是事件状态，不一定等于整条请求最终状态。
```

## 2.3 withHashes

```java
public AuditEvent withHashes(String previousHash, String eventHash)
```

业务代码创建事件时通常不传 hash。

hash 是 `AuditTrace.addEvent(...)` 追加事件时统一补上的。

---

# 3. 看 AuditTrace：一整条证据链

文件：

```text
src/main/java/com/cup/opsagent/audit/AuditTrace.java
```

核心字段：

```text
traceId
userInput
startedAt
endedAt
status
finalAnswer
events
```

一句话：

```text
AuditTrace 是一次请求的审计档案袋。
```

## 3.1 addEvent 是重点

`addEvent` 做两件关键事：

```text
1. 找到 previousHash
2. 计算 eventHash
```

逻辑是：

```text
如果这是第一条事件：previousHash = GENESIS
否则：previousHash = 上一条事件的 eventHash
```

然后：

```text
eventHash = AuditHasher.hashEvent(event, previousHash)
```

最后把带 hash 的事件放进 events。

这就是 hash chain 形成的地方。

## 3.2 hasValidHashChain

它会从 `GENESIS` 开始重新验链：

```text
检查 event.previousHash 是否正确
重新计算 eventHash
比较是否一致
```

如果有人改了中间事件，或者删了中间事件，这里会失败。

## 3.3 finish

```java
finish(status, finalAnswer)
```

它只改 trace 的终态字段：

```text
status
finalAnswer
endedAt
```

真正追加 `FINAL_RESPONSE` 的地方在 `AuditLogService.finish(...)`。

---

# 4. 看 AuditHasher：hash 到底怎么稳定计算

文件：

```text
src/main/java/com/cup/opsagent/audit/AuditHasher.java
```

核心方法：

```java
hashEvent(AuditEvent event, String previousHash)
```

参与 hash 的内容：

```text
previousHash
traceId
eventType
eventTime
success
payload
errorMessage
```

注意 `previousHash` 也参与 hash。

这就是链式结构成立的原因。

## 4.1 canonicalValue

为什么要 canonical？

因为 Map 的遍历顺序可能不稳定。

所以这里会做：

```text
Map 按 key 排序
List 保持原顺序
String 做转义
Instant 截断到毫秒
```

这样同样内容可以算出稳定 hash。

## 4.2 GENESIS_HASH

```java
public static final String GENESIS_HASH = "GENESIS";
```

它是第一条事件的 previousHash。

记忆：

```text
GENESIS 是审计链的起点。
```

---

# 5. 看 AuditLogService：业务代码用的入口

文件：

```text
src/main/java/com/cup/opsagent/audit/AuditLogService.java
```

业务层一般不直接碰 repository，而是用它。

## 5.1 startTrace

```java
AuditTrace trace = auditLogService.startTrace(userInput);
```

做：

```text
生成 UUID traceId
创建 AuditTrace
保存 trace
追加 RECEIVE_REQUEST
```

所以每条请求一进入系统，第一条事件就是：

```text
RECEIVE_REQUEST
```

## 5.2 append

```java
auditLogService.append(traceId, event)
```

做：

```text
找到 trace
trace.addEvent(event)
保存 trace
```

注意：

```text
hash 是 addEvent 自动计算的，不是业务代码手写。
```

## 5.3 finish

```java
auditLogService.finish(traceId, status, finalAnswer)
```

做：

```text
更新 trace.status
更新 trace.finalAnswer
更新 trace.endedAt
保存 trace
追加 FINAL_RESPONSE
```

所以只要一个请求结束，无论成功失败，都应该 finish。

---

# 6. 看 AuditIntegrityService：怎么验链

文件：

```text
src/main/java/com/cup/opsagent/audit/AuditIntegrityService.java
```

入口：

```java
check(traceId)
```

逻辑：

```text
traceId 不能为空
找到 trace
从 GENESIS 开始遍历 events
逐条检查 previousHash
逐条重新计算 eventHash
```

返回：

```text
AuditIntegrityResult
```

结果可能是：

```text
missing：trace 不存在
valid：链完整
invalid：链断了或 hash 不匹配
```

对应 API：

```text
GET /api/audit/traces/{traceId}/integrity
```

---

# 7. 看 AgentOrchestrator：审计事件在哪里写

文件：

```text
src/main/java/com/cup/opsagent/agent/core/AgentOrchestrator.java
```

这是真正把审计和业务串起来的地方。

## 7.1 请求入口

一开始：

```text
startTrace(request.message())
```

产生：

```text
RECEIVE_REQUEST
```

## 7.2 LLM planning started

如果 planner 是 `LlmJsonTaskPlanner`：

```text
LLM_PLANNING_STARTED
```

payload 里记录：

```text
planner
```

## 7.3 planning 失败

如果 planner 抛异常：

```text
LLM_PLANNING_FAILED 或 LLM_PLAN_REJECTED
PLANNING_FAILED
FINAL_RESPONSE
```

这里有一个关键函数：

```java
llmFailureEventType(exception)
```

规则：

```text
LlmPlanValidationException -> LLM_PLAN_REJECTED
其他 -> LLM_PLANNING_FAILED
```

这能帮助排查方向：

```text
failed：查 provider / 网络 / 鉴权 / 限流
rejected：查模型输出 / validator / schema
```

## 7.4 planning 成功

LLM 成功：

```text
LLM_PLANNING_COMPLETED
```

通用成功：

```text
PLAN_GENERATED
```

payload 包括：

```text
intentType
summary
riskLevel
stepCount
```

## 7.5 空计划

如果 plan 为空：

```text
EXECUTION_BLOCKED
FINAL_RESPONSE
```

状态：

```text
BLOCKED
```

说明系统不会为了执行而硬执行。

## 7.6 每个 step 的审计

每个 `PlanStep` 会经历：

```text
TOOLCALL_PARSED
POLICY_CHECKED
```

如果 policy 阻断：

```text
EXECUTION_BLOCKED
FINAL_RESPONSE
```

如果等待审批：

```text
APPROVAL_REQUIRED
EXECUTION_SKIPPED
FINAL_RESPONSE
```

如果放行：

```text
TOOL_VALIDATED
EXECUTION_STARTED
TOOL_EXECUTED
VERIFICATION_STARTED
VERIFICATION_PASSED / VERIFICATION_FAILED
```

最后整条请求：

```text
FINAL_RESPONSE
```

---

# 8. 看 ApprovedActionExecutor：审批通过后的审计

文件：

```text
src/main/java/com/cup/opsagent/approval/ApprovedActionExecutor.java
```

当人工审批通过后，执行动作不是重新开一条 trace。

它会从 approval 里拿回：

```text
traceId
stepId
approvalId
```

然后继续往同一条 trace 追加事件。

典型路径：

```text
EXECUTION_LEASE_CONSUMED
EXECUTION_STARTED
TOOL_EXECUTED
VERIFICATION_STARTED
VERIFICATION_PASSED / VERIFICATION_FAILED
APPROVED_ACTION_EXECUTED
```

这点很重要：

```text
审批前和审批后必须在同一条证据链上。
```

否则复盘时你会不知道这个执行动作来自哪个原始请求。

---

# 9. 看 Repository：内存和数据库只是存储差异

接口：

```text
AuditTraceRepository
```

实现：

```text
InMemoryAuditTraceRepository
JdbcAuditTraceRepository
```

内存实现：

```text
适合本地开发和测试
```

JDBC 实现：

```text
postgres profile 下启用
trace 写 audit_traces
event 写 audit_events
payload 存 JSON
```

老师提醒：

```text
审计语义在 AuditTrace / AuditEvent / AuditLogService 里，repository 只负责保存。
```

---

# 10. 看 AuditController：怎么查审计

文件：

```text
src/main/java/com/cup/opsagent/api/AuditController.java
```

接口：

```text
GET /api/audit/traces
GET /api/audit/traces/{traceId}
GET /api/audit/traces/{traceId}/integrity
```

排查顺序：

```text
先 list traces 找 traceId
再 get trace 看 events
最后 check integrity 看链是否被破坏
```

---

# 11. 一次完整成功路径怎么读

看到：

```text
RECEIVE_REQUEST
PLAN_GENERATED
TOOLCALL_PARSED
POLICY_CHECKED
TOOL_VALIDATED
EXECUTION_STARTED
TOOL_EXECUTED
VERIFICATION_STARTED
VERIFICATION_PASSED
FINAL_RESPONSE
```

你要能翻译成：

```text
系统收到请求。
planner 生成计划。
计划 step 被解析为 tool call。
安全策略检查通过。
工具存在且校验通过。
工具开始执行。
工具执行完成。
verifier 开始验证。
验证通过。
系统返回最终响应。
```

---

# 12. 一次阻断路径怎么读

看到：

```text
RECEIVE_REQUEST
PLAN_GENERATED
TOOLCALL_PARSED
POLICY_CHECKED
EXECUTION_BLOCKED
FINAL_RESPONSE
```

你要能翻译成：

```text
系统不是异常退出，而是 policy 明确阻断。
```

重点看：

```text
POLICY_CHECKED.payload.decision
POLICY_CHECKED.payload.reason
POLICY_CHECKED.payload.violatedPolicies
```

---

# 13. 一次等待审批路径怎么读

看到：

```text
RECEIVE_REQUEST
PLAN_GENERATED
TOOLCALL_PARSED
POLICY_CHECKED
APPROVAL_REQUIRED
EXECUTION_SKIPPED
FINAL_RESPONSE
```

说明：

```text
工具没有执行。
系统创建了 approval。
当前请求状态是 WAITING_APPROVAL。
```

重点看：

```text
approvalId
actionHash
approvalExpiresAt
shouldExecute=false
```

---

# 14. 本节练习

## 练习 1

为什么 `AuditTrace.addEvent` 里计算 hash，而不是业务代码自己算？

答案：

```text
避免每个业务点重复实现 hash 逻辑，保证 previousHash 和 eventHash 的链式规则统一。
```

## 练习 2

为什么审批通过后的执行还要写回原 traceId？

答案：

```text
审批前的请求和审批后的执行属于同一个安全决策链路，必须能在同一条证据链里复盘。
```

## 练习 3

如果 trace 的第 3 个事件被删除，integrity 怎么发现？

答案：

```text
第 4 个事件的 previousHash 仍然指向原第 3 个事件的 eventHash，因此和当前前一个事件对不上。
```

## 练习 4

排查 LLM 输出不合法，应该看哪个事件？

答案：

```text
LLM_PLAN_REJECTED，并结合 PLANNING_FAILED 看最终失败响应。
```

---

# 15. 本节最后记忆

读 audit 源码时记住这条线：

```text
AuditEventType 定义事件边界
AuditEvent 表示一个事件
AuditTrace 串起事件并生成 hash chain
AuditHasher 负责稳定 hash
AuditLogService 是业务入口
AgentOrchestrator 在关键决策点 append
AuditIntegrityService 负责验链
AuditController 暴露查询接口
```

一句话：

```text
audit 源码的核心不是“记录日志”，而是“把一次请求变成可校验的证据链”。
```
