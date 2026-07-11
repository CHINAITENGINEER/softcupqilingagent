# 第十七章：Audit Trace 审计闭环完整复习课

> 这一章可以理解成 SafeOps Agent 的“行车记录仪”。
>
> 系统不只是要做对事情，还要能证明自己每一步为什么这么做。

---

# 1. 审计不是普通日志

普通日志通常是：

```text
开始执行
执行成功
执行失败
```

但 SafeOps Agent 的 audit 是：

```text
一次用户请求的证据链
```

它要回答：

```text
用户说了什么？
系统生成了什么计划？
安全策略如何判断？
是否需要审批？
工具有没有执行？
验证有没有通过？
最后给用户什么响应？
这条记录有没有被篡改？
```

一句话：

```text
日志是给开发看过程，审计是给系统自证和事故复盘看证据。
```

---

# 2. 审计主线在哪里

SafeOps Agent 主链路：

```text
AgentRequest
  -> AgentOrchestrator
  -> Planner
  -> PolicyEngine
  -> Approval
  -> Tool Execution
  -> Verifier
  -> AgentResponse
```

Audit Trace 横穿整条链路：

```text
RECEIVE_REQUEST
  -> PLAN_GENERATED / PLANNING_FAILED
  -> POLICY_CHECKED
  -> APPROVAL_REQUIRED / TOOL_EXECUTED
  -> VERIFICATION_PASSED / VERIFICATION_FAILED
  -> FINAL_RESPONSE
```

主链路负责做事，审计链路负责记录证据。

---

# 3. traceId：一次请求的证据链 ID

`traceId` 是一次用户请求的唯一 ID。

`AgentOrchestrator.handle(...)` 一开始会做：

```java
AuditTrace trace = auditLogService.startTrace(request.message());
String traceId = trace.getTraceId();
```

之后所有事件都挂到这个 `traceId` 上：

```text
traceId=xxx
  RECEIVE_REQUEST
  PLAN_GENERATED
  POLICY_CHECKED
  TOOL_EXECUTED
  FINAL_RESPONSE
```

记住：

```text
一次用户请求 = 一条 traceId = 一条证据链
```

LLM、RAG、Approval、ToolCall、Verifier 都应该使用同一个 `traceId`。

---

# 4. AuditTrace：整条链

`AuditTrace` 是一次请求的审计档案袋。

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

可以这么理解：

```text
AuditTrace = 一整个档案袋
AuditEvent = 档案袋里的一页记录
```

生命周期：

```text
startTrace
  -> 创建 AuditTrace
  -> 写 RECEIVE_REQUEST

append
  -> 追加中间事件

finish
  -> 设置 status/finalAnswer/endedAt
  -> 写 FINAL_RESPONSE
```

---

# 5. AuditEvent：链上的节点

`AuditEvent` 是一条具体事件。

字段：

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

常见 eventType：

```text
RECEIVE_REQUEST
PLAN_GENERATED
PLANNING_FAILED
POLICY_CHECKED
TOOL_EXECUTED
VERIFICATION_PASSED
VERIFICATION_FAILED
FINAL_RESPONSE
```

payload 是结构化证据，例如：

```text
PLAN_GENERATED:
  intentType
  summary
  riskLevel
  stepCount

POLICY_CHECKED:
  decision
  riskLevel
  reason
  violatedPolicies

TOOL_EXECUTED:
  success
  exitCode
  failureCode
  durationMs
```

payload 原则：

```text
够排查，但不泄密。
```

不要记录：

```text
apiKey
Authorization
完整 prompt
完整 raw response
完整命令输出
完整 RAG 文档内容
embedding 向量
```

---

# 6. AuditEventType 分组记忆

## 请求头尾

```text
RECEIVE_REQUEST
FINAL_RESPONSE
```

注意：

```text
FINAL_RESPONSE 不代表成功，只代表请求结束。
```

最终状态看：

```text
trace.status
FINAL_RESPONSE.payload.status
```

可能是：

```text
SUCCESS
BLOCKED
WAITING_APPROVAL
PLANNING_FAILED
VERIFICATION_FAILED
```

## Planning

```text
PLAN_GENERATED
PLANNING_FAILED
```

LLM 专用：

```text
LLM_PLANNING_STARTED
LLM_PLANNING_COMPLETED
LLM_PLANNING_FAILED
LLM_PLAN_REJECTED
```

区别：

```text
LLM_PLANNING_FAILED：provider 调用失败、超时、鉴权失败。
LLM_PLAN_REJECTED：模型输出被 strict parse 或 validator 拒绝。
PLANNING_FAILED：通用 planner 失败终态。
```

## RAG

```text
RAG_RETRIEVAL_STARTED
RAG_RETRIEVAL_COMPLETED
RAG_RETRIEVAL_FAILED
```

RAG payload 只记录：

```text
queryHash
resultCount
topKnowledgeIds
topScores
durationMs
```

不记录完整 userInput、完整文档和 embedding。

## Tool / Policy / Verification

```text
TOOLCALL_PARSED
POLICY_CHECKED
TOOL_VALIDATED
EXECUTION_STARTED
TOOL_EXECUTED
VERIFICATION_STARTED
VERIFICATION_PASSED
VERIFICATION_FAILED
EXECUTION_BLOCKED
EXECUTION_SKIPPED
```

这组是安全闭环核心。

## Approval

```text
APPROVAL_REQUIRED
APPROVAL_GRANTED
APPROVAL_REJECTED
EXECUTION_LEASE_CONSUMED
APPROVED_ACTION_EXECUTED
```

证明高风险操作不是偷偷执行的。

---

# 7. Hash Chain：为什么能发现篡改

每个 `AuditEvent` 都有：

```text
previousHash
eventHash
```

第一条事件：

```text
previousHash = GENESIS
```

后续事件：

```text
event[1].previousHash = event[0].eventHash
event[2].previousHash = event[1].eventHash
```

如果有人修改中间事件的 payload、eventTime、success、errorMessage：

```text
重新计算 eventHash 会不一致
```

如果有人删除中间事件：

```text
下一条事件的 previousHash 会对不上
```

所以 hash chain 的作用是：

```text
不是阻止篡改，而是发现篡改。
```

对应代码：

```text
AuditHasher
AuditTrace.addEvent
AuditTrace.hasValidHashChain
AuditIntegrityService
```

---

# 8. AuditLogService：业务使用的入口

业务代码主要用它，不直接操作 repository。

## startTrace

```java
auditLogService.startTrace(userInput)
```

做：

```text
生成 traceId
创建 AuditTrace
保存 trace
追加 RECEIVE_REQUEST
```

## append

```java
auditLogService.append(traceId, event)
```

做：

```text
找到 trace
追加 event
自动计算 previousHash 和 eventHash
保存 trace
```

## finish

```java
auditLogService.finish(traceId, status, finalAnswer)
```

做：

```text
设置 status
设置 finalAnswer
设置 endedAt
追加 FINAL_RESPONSE
```

---

# 9. Repository：审计数据存储

当前两个实现：

```text
InMemoryAuditTraceRepository
JdbcAuditTraceRepository
```

默认使用内存：

```text
适合本地和测试
进程重启丢失
```

`postgres` profile 使用 JDBC：

```text
写入 audit_traces / audit_events
payload 存 JSON
适合持久化
```

业务层只依赖：

```text
AuditTraceRepository
```

这就是接口隔离。

---

# 10. AgentOrchestrator 中的典型审计路径

## 成功路径

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

含义：

```text
计划生成了
策略放行了
工具执行了
验证通过了
最终成功了
```

## 策略阻断路径

```text
RECEIVE_REQUEST
PLAN_GENERATED
TOOLCALL_PARSED
POLICY_CHECKED
EXECUTION_BLOCKED
FINAL_RESPONSE
```

说明：

```text
不是系统崩了，而是安全策略主动阻断。
```

## 等待审批路径

```text
RECEIVE_REQUEST
PLAN_GENERATED
TOOLCALL_PARSED
POLICY_CHECKED
APPROVAL_REQUIRED
EXECUTION_SKIPPED
FINAL_RESPONSE
```

状态：

```text
WAITING_APPROVAL
```

说明：

```text
高风险操作还没有执行，只是产生了审批请求。
```

## LLM 规划失败路径

```text
RECEIVE_REQUEST
LLM_PLANNING_STARTED
LLM_PLANNING_FAILED
PLANNING_FAILED
FINAL_RESPONSE
```

## LLM 输出被拒绝路径

```text
RECEIVE_REQUEST
LLM_PLANNING_STARTED
LLM_PLAN_REJECTED
PLANNING_FAILED
FINAL_RESPONSE
```

## RAG 失败路径

```text
RECEIVE_REQUEST
LLM_PLANNING_STARTED
RAG_RETRIEVAL_STARTED
RAG_RETRIEVAL_FAILED
LLM_PLANNING_FAILED
PLANNING_FAILED
FINAL_RESPONSE
```

说明：

```text
RAG fail-closed，不继续调用 LLM provider。
```

---

# 11. 怎么查看审计

接口：

```text
GET /api/audit/traces
GET /api/audit/traces/{traceId}
GET /api/audit/traces/{traceId}/integrity
```

排查时先看：

```text
trace.status
finalAnswer
events 的顺序
失败事件的 payload
integrity 是否 valid
```

---

# 12. 读源码顺序

建议按这个顺序：

```text
1. AuditEventType
2. AuditEvent
3. AuditTrace
4. AuditHasher
5. AuditLogService
6. AuditIntegrityService
7. AgentOrchestrator
8. AuditController
9. InMemoryAuditTraceRepository / JdbcAuditTraceRepository
```

不要一开始就看 `AgentOrchestrator`，容易被业务分支绕晕。

先搞懂：

```text
事件是什么
链是什么
hash 怎么算
service 怎么 append
```

再回主链路看每个事件在哪里写。

---

# 13. 小练习

## 练习 1

为什么 `FINAL_RESPONSE` 不代表业务成功？

答案：

```text
FINAL_RESPONSE 只表示请求已经结束。最终状态可能是 SUCCESS、BLOCKED、WAITING_APPROVAL、PLANNING_FAILED、VERIFICATION_FAILED。
```

## 练习 2

为什么 audit payload 不能记录完整 prompt？

答案：

```text
prompt 可能包含用户敏感输入、内部工具信息、RAG 内容或策略提示。写入审计会让审计系统变成泄密点。
```

## 练习 3

如果有人修改历史事件 payload，怎么发现？

答案：

```text
AuditIntegrityService 会重新计算 eventHash。修改后 hash 不一致，integrity 变 invalid。
```

## 练习 4

`LLM_PLANNING_FAILED` 和 `LLM_PLAN_REJECTED` 区别是什么？

答案：

```text
LLM_PLANNING_FAILED 是 provider 调用或响应失败。LLM_PLAN_REJECTED 是拿到模型输出后，被 strict parse 或 validator 拒绝。
```

## 练习 5

RAG 检索失败为什么不继续调用 LLM？

答案：

```text
当前策略是 fail-closed。RAG 启用时系统预期基于检索上下文规划，检索失败后继续无上下文规划会造成行为不透明。
```

---

# 14. 本章最后记忆

如果只记一句：

```text
AuditTrace 是一次请求的证据链，AuditEvent 是证据链上的节点，hash chain 用来发现篡改。
```

如果记三句：

```text
1. 审计不是日志，是安全闭环的证据。
2. 每个关键决策点都要写事件，尤其是失败路径。
3. payload 要够排查，但不能泄露敏感信息。
```

排查问题时先问：

```text
traceId 是什么？
最终 status 是什么？
失败发生在哪个 eventType？
payload 里的 errorCode / failureCode / decision 是什么？
hash chain 是否 valid？
```
