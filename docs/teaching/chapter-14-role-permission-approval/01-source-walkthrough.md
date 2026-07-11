# 14.1 Role、Permission 与 Approval 源码走读

这一节按源码顺序走读第 14 章。

推荐打开这些文件对照看：

```text
Permission
Role
Actor
ActorResolver
ActorMethodArgumentResolver
ApproverPolicy
ApprovalService
ApprovalController
ApprovedActionExecutor
ActionHasher
```

---

# 1. Permission：最小授权单位

文件：

```text
src/main/java/com/cup/opsagent/auth/Permission.java
```

当前权限：

```text
APPROVE_MEDIUM_RISK_ACTION
REJECT_MEDIUM_RISK_ACTION
EXECUTE_APPROVED_ACTION
```

它们表示具体能力。

记住：

```text
系统最终检查的是 permission，不是角色名字。
```

---

# 2. Role：权限集合

文件：

```text
src/main/java/com/cup/opsagent/auth/Role.java
```

当前角色：

```text
OPERATOR
APPROVER
EXECUTOR
ADMIN
```

对应关系：

```text
OPERATOR -> 无特殊权限
APPROVER -> approve + reject medium risk
EXECUTOR -> execute approved action
ADMIN -> approve + reject + execute
```

源码里每个 Role 持有：

```text
Set<Permission>
```

所以 role 是 permission 的打包。

---

# 3. Actor：当前操作者

文件：

```text
src/main/java/com/cup/opsagent/auth/Actor.java
```

字段：

```text
actorId
actorType
permissions
roles
```

构造时会合并：

```text
直接 permissions
roles 自带 permissions
```

最终检查：

```java
actor.hasPermission(permission)
```

老师提醒：

```text
Actor 不是 AgentRequest.userId 的简单复制。
Actor 表示当前调用审批/执行接口的人。
```

---

# 4. ActorResolver：请求头到 Actor

文件：

```text
src/main/java/com/cup/opsagent/auth/ActorResolver.java
```

它读取这些 header：

```text
X-Actor-Id
X-Actor-Type
X-Actor-Roles
X-Actor-Permissions
```

没有传 roles 时：

```text
默认 OPERATOR
```

角色和权限都支持 CSV：

```text
X-Actor-Roles: APPROVER,EXECUTOR
X-Actor-Permissions: EXECUTE_APPROVED_ACTION
```

---

# 5. ActorMethodArgumentResolver：Controller 自动注入 Actor

文件：

```text
src/main/java/com/cup/opsagent/auth/ActorMethodArgumentResolver.java
```

当 Controller 参数是：

```java
@CurrentActor Actor actor
```

它会从 request header 解析出 Actor。

所以 ApprovalController 里不用手动读 header。

---

# 6. ApproverPolicy：权限检查核心

文件：

```text
src/main/java/com/cup/opsagent/auth/ApproverPolicy.java
```

这是本章最关键的类。

## 6.1 assertCanApprove

检查：

```text
actor 不为空
actor 必须是 HUMAN
actor 有 approve permission
requester 不能 approve 自己的 action
```

这四条分别防：

```text
匿名审批
机器自审批
无权限审批
自批自审
```

## 6.2 assertCanReject

检查：

```text
actor 不为空
actor 必须是 HUMAN
actor 有 reject permission
```

拒绝是保守动作，所以没有禁止 requester reject 自己的 action。

## 6.3 assertCanExecuteApprovedAction

检查：

```text
actor 有 EXECUTE_APPROVED_ACTION
```

说明：

```text
能审批不等于能执行。
能执行不等于能审批。
```

---

# 7. ApprovalService：审批状态机

文件：

```text
src/main/java/com/cup/opsagent/approval/ApprovalService.java
```

## 7.1 requestApproval

创建：

```text
ApprovalRecord
```

包含：

```text
approvalId
traceId
stepId
requesterId
toolName
canonicalArguments
riskLevel
actionHash
status=PENDING
expiresAt
```

`actionHash` 由：

```text
toolName + arguments + riskLevel
```

算出。

## 7.2 approve

审批通过：

```text
PENDING -> APPROVED
```

然后生成：

```text
ExecutionLease
```

注意：

```text
approve 不执行工具，只生成 lease。
```

## 7.3 reject

拒绝：

```text
PENDING -> REJECTED
```

## 7.4 consumeLease

执行前消费 lease。

检查：

```text
lease 未消费
lease 未过期
actionHash 匹配
approval 状态是 APPROVED
```

然后：

```text
lease.consumedAt = now
approval.status = CONSUMED
```

---

# 8. ApprovalController：三个接口

文件：

```text
src/main/java/com/cup/opsagent/api/ApprovalController.java
```

## 8.1 approve

```text
POST /api/approvals/approve
```

流程：

```text
find approval
assertCanApprove
approvalService.approve
写 APPROVAL_GRANTED 审计
返回 leaseId
```

## 8.2 reject

```text
POST /api/approvals/reject
```

流程：

```text
find approval
assertCanReject
approvalService.reject
写 APPROVAL_REJECTED 审计
```

## 8.3 execute

```text
POST /api/approvals/execute
```

流程：

```text
assertCanExecuteApprovedAction
approvedActionExecutor.execute
```

---

# 9. ApprovedActionExecutor：用 lease 执行动作

文件：

```text
src/main/java/com/cup/opsagent/approval/ApprovedActionExecutor.java
```

核心步骤：

```text
find tool
consume lease
拿 approval.traceId
写 EXECUTION_LEASE_CONSUMED
执行 tool
写 TOOL_EXECUTED
启动 verifier
写 VERIFICATION_PASSED / VERIFICATION_FAILED
写 APPROVED_ACTION_EXECUTED
```

注意：

```text
审批后的执行仍然写回原 traceId。
```

这样一次请求从：

```text
WAITING_APPROVAL
```

到：

```text
APPROVED_ACTION_EXECUTED
```

都能在同一条审计链里复盘。

---

# 10. ActionHasher：防止批准 A 执行 B

文件：

```text
src/main/java/com/cup/opsagent/approval/ActionHasher.java
```

它给动作生成指纹。

绑定：

```text
toolName
arguments
riskLevel
```

所以如果有人试图：

```text
批准 restart nginx
执行 delete file
```

consumeLease 时 actionHash 会不匹配。

---

# 11. 本节总结

源码主线：

```text
Permission 定义能力
Role 打包能力
Actor 表示当前操作者
ActorResolver 从 header 解析操作者
ApproverPolicy 做权限检查
ApprovalService 管审批状态和 lease
ApprovalController 暴露 approve/reject/execute
ApprovedActionExecutor 消费 lease 并执行工具
ActionHasher 保证动作一致性
```

一句话：

```text
第 14 章的核心是：中高风险动作必须经过有权限的人类审批，并用 lease + actionHash 保证执行动作和审批动作一致。
```
