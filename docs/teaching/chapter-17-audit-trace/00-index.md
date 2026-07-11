# 第十七章：Audit Trace 审计闭环

本章专门复习 SafeOps Agent 的审计系统。

这章不是单纯讲“怎么打日志”，而是讲：

```text
一次用户请求从进入系统到最终响应，系统如何留下可追溯、可校验、可排查的证据链。
```

## 本章目标

学完本章，你应该能回答：

```text
1. AuditTrace 和普通日志有什么区别？
2. traceId 为什么重要？
3. AuditEvent 应该记录什么，不应该记录什么？
4. hash chain 是怎么防篡改的？
5. AgentOrchestrator 在哪些关键节点写审计？
6. planning failed、blocked、waiting approval、success 这些状态如何落到审计？
7. LLM / RAG / Approval / Verification 为什么都要写入同一条 trace？
```

## 推荐阅读顺序

1. `full-lesson.md`：完整复习课，适合先建立整体理解。
2. `01-source-walkthrough.md`：源码走读，适合第二遍对照代码学习。

如果你没认真听这一章，建议先读 `full-lesson.md`，再读 `01-source-walkthrough.md`，不要直接跳源码。

## 本章工程主线

```text
AgentRequest
  -> AuditLogService.startTrace
  -> RECEIVE_REQUEST
  -> Planner
  -> PLAN_GENERATED / PLANNING_FAILED
  -> ToolCall parse
  -> PolicyEngine
  -> Approval / Execution / Verification
  -> AuditLogService.finish
  -> FINAL_RESPONSE
```

## 本章核心类

```text
AuditEvent
AuditEventType
AuditTrace
AuditHasher
AuditLogService
AuditTraceRepository
InMemoryAuditTraceRepository
JdbcAuditTraceRepository
AuditIntegrityService
AuditController
```

## 本章记忆点

```text
1. 审计不是日志，审计是证据链。
2. traceId 是一次请求的证据链 ID。
3. AuditTrace 是整条链，AuditEvent 是链上的一个节点。
4. previousHash + eventHash 组成 hash chain。
5. 审计 payload 要够排查，但不能泄露敏感信息。
6. 失败也必须写审计，而且失败路径比成功路径更重要。
7. FINAL_RESPONSE 是终态事件，不代表业务一定成功。
8. LLM、RAG、Approval、Verifier 都必须挂在同一条 trace 上。
9. 审计系统不负责做安全决策，但负责证明安全决策发生过。
```
