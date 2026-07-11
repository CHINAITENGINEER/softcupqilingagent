# 第十四章：Role、Permission 与风险操作授权完整复习课

> 这一章的核心不是“用户登录”，而是“谁有权批准和执行风险操作”。
>
> SafeOps Agent 面对的是运维动作，不能让任何人随便批准重启、删除、变更这类操作。

---

# 1. 先建立直觉：为什么需要 Role 和 Permission

Agent 生成工具调用后，不是所有动作都能直接执行。

有些动作风险低，可以自动执行。

有些动作风险中等或较高，需要人工审批。

问题来了：

```text
谁可以审批？
谁可以拒绝？
谁可以执行已经批准的动作？
请求发起人能不能自己批准自己？
机器账号能不能审批？
批准的是 nginx restart，能不能拿这个批准去执行 rm -rf？
```

第 14 章就是解决这些问题。

一句话：

```text
Role / Permission 是风险操作的人类授权边界。
```

---

# 2. Role 和 Permission 的区别

## 2.1 Permission 是具体能力

当前权限定义在：

```text
Permission
```

包括：

```text
APPROVE_MEDIUM_RISK_ACTION
REJECT_MEDIUM_RISK_ACTION
EXECUTE_APPROVED_ACTION
```

也就是说，当前代码先把中风险审批链路做完整。

如果未来要扩展高风险，可以按同一模式新增：

```text
APPROVE_HIGH_RISK_ACTION
REJECT_HIGH_RISK_ACTION
```

再在 `Role` 和 `ApproverPolicy` 里补对应映射。

所以 Permission 是非常具体的能力。

例如：

```text
能批准中风险动作
能拒绝中风险动作
能执行已经批准的动作
```

## 2.2 Role 是权限集合

当前角色定义在：

```text
Role
```

角色和权限关系：

```text
OPERATOR  -> 无特殊权限
APPROVER  -> APPROVE_MEDIUM_RISK_ACTION + REJECT_MEDIUM_RISK_ACTION
EXECUTOR  -> EXECUTE_APPROVED_ACTION
ADMIN     -> APPROVE_MEDIUM_RISK_ACTION + REJECT_MEDIUM_RISK_ACTION + EXECUTE_APPROVED_ACTION
```

所以：

```text
Role 是一组 Permission 的打包。
Permission 是真正被检查的能力。
```

记忆：

```text
Role 是身份标签，Permission 是能不能做某件事的判断依据。
```

---

# 3. Actor：当前操作者

`Actor` 表示当前正在调用 API 的操作者。

字段：

```text
actorId
actorType
permissions
roles
```

## 3.1 actorId

`actorId` 是当前操作者 ID。

例如：

```text
alice
bob
ops-admin-01
```

它用于审计和权限判断。

## 3.2 actorType

`ActorType` 用来区分操作者类型。

当前审批逻辑里最重要的是：

```text
只有 HUMAN actor 可以 approve/reject。
```

为什么？

```text
审批是人类责任动作，不能让自动化程序自己批准高风险操作。
```

## 3.3 roles 和 permissions

Actor 构造时会合并：

```text
直接 permissions
角色自带 permissions
```

所以一个 actor 最终能不能做事，看的是：

```text
actor.hasPermission(permission)
```

---

# 4. ActorResolver：请求头如何变成 Actor

当前系统通过 HTTP header 表示当前操作者。

关键请求头：

```text
X-Actor-Id
X-Actor-Type
X-Actor-Roles
X-Actor-Permissions
```

例如：

```text
X-Actor-Id: alice
X-Actor-Type: HUMAN
X-Actor-Roles: APPROVER
```

会解析成：

```text
Actor(
  actorId=alice,
  actorType=HUMAN,
  roles=[APPROVER],
  permissions=[APPROVE_MEDIUM_RISK_ACTION, REJECT_MEDIUM_RISK_ACTION]
)
```

如果没有传角色：

```text
默认 OPERATOR
```

也就是：

```text
普通操作者没有审批和执行已批准动作的权限。
```

---

# 5. CurrentActor：Controller 怎么拿到当前操作者

Controller 方法里可以写：

```java
@CurrentActor Actor actor
```

背后是：

```text
ActorMethodArgumentResolver
```

它会从 request header 里读取：

```text
X-Actor-Id
X-Actor-Type
X-Actor-Roles
X-Actor-Permissions
```

然后交给：

```text
ActorResolver
```

最终得到 `Actor`。

所以 Controller 不需要自己解析 header。

---

# 6. ApproverPolicy：真正的授权检查

文件：

```text
ApproverPolicy
```

它负责回答：

```text
这个 actor 能不能 approve？
这个 actor 能不能 reject？
这个 actor 能不能 execute approved action？
```

## 6.1 approve 检查

`assertCanApprove(actor, approvalRecord)` 会检查：

```text
actor 不能为空
actor 必须是 HUMAN
actor 必须有对应风险等级的 approve permission
requester 不能 approve 自己的 action
```

最重要的是最后一条：

```text
requester cannot approve their own action
```

它防止：

```text
我发起一个高风险动作，然后我自己批准自己。
```

这叫防止自批自审。

## 6.2 reject 检查

`assertCanReject(actor, approvalRecord)` 会检查：

```text
actor 不能为空
actor 必须是 HUMAN
actor 必须有 reject permission
```

reject 不需要检查 requester 不能拒绝自己。

因为拒绝是更保守的安全动作。

## 6.3 execute approved action 检查

`assertCanExecuteApprovedAction(actor)` 检查：

```text
actor 必须有 EXECUTE_APPROVED_ACTION
```

也就是说：

```text
能批准的人，不一定能执行。
能执行的人，不一定能批准。
```

这是职责分离。

---

# 7. 中风险操作的完整授权流程

一条中风险操作不会直接执行，而是进入 approval 流程。

主线：

```text
PolicyEngine 判断需要审批
  -> ApprovalService.requestApproval
  -> 生成 ApprovalRecord
  -> 返回 WAITING_APPROVAL
  -> 有权限的人 approve
  -> 生成 ExecutionLease
  -> 有执行权限的人 execute lease
  -> ApprovedActionExecutor 执行工具
```

## 7.1 requestApproval

生成：

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

这里 `actionHash` 很关键。

它绑定：

```text
toolName + canonicalArguments + riskLevel
```

也就是：

```text
批准的是这个具体动作，而不是随便一个动作。
```

## 7.2 approve

审批通过后，不是直接执行。

它会生成：

```text
ExecutionLease
```

lease 包含：

```text
leaseId
approvalId
actionHash
toolName
canonicalArguments
expiresAt
consumedAt
```

注意：

```text
approve 只产生执行许可，不执行工具。
```

## 7.3 consumeLease

执行前必须消费 lease。

消费时检查：

```text
lease 没被消费过
lease 没过期
actionHash 匹配
approval 状态是 APPROVED
```

这防止：

```text
重复执行
过期执行
批准 A 执行 B
未批准就执行
```

---

# 8. ApprovalController 三个入口

## 8.1 approve

```text
POST /api/approvals/approve
```

流程：

```text
查 approval
ApproverPolicy.assertCanApprove
ApprovalService.approve
写 APPROVAL_GRANTED 审计
返回 leaseId
```

需要权限：

```text
APPROVE_MEDIUM_RISK_ACTION
```

并且：

```text
必须 HUMAN
不能是 requester 自己
```

## 8.2 reject

```text
POST /api/approvals/reject
```

流程：

```text
查 approval
ApproverPolicy.assertCanReject
ApprovalService.reject
写 APPROVAL_REJECTED 审计
```

需要权限：

```text
REJECT_MEDIUM_RISK_ACTION
```

## 8.3 execute

```text
POST /api/approvals/execute
```

流程：

```text
ApproverPolicy.assertCanExecuteApprovedAction
ApprovedActionExecutor.execute
consume lease
执行工具
验证结果
写审计
```

需要权限：

```text
EXECUTE_APPROVED_ACTION
```

---

# 9. 为什么要 approvalId、leaseId、actionHash 三个东西

## approvalId

表示：

```text
这次审批请求
```

用于 approve/reject。

## leaseId

表示：

```text
审批通过后产生的一次性执行许可
```

用于 execute。

## actionHash

表示：

```text
被批准的具体动作指纹
```

用于防止：

```text
批准 nginx restart，却执行 rm -rf
批准参数 A，却执行参数 B
```

一句话：

```text
approvalId 管审批，leaseId 管执行许可，actionHash 管动作一致性。
```

---

# 10. 按角色看能做什么

## OPERATOR

```text
默认角色
不能 approve
不能 reject
不能 execute approved action
```

适合普通请求发起者。

## APPROVER

```text
可以 approve 中风险动作
可以 reject 中风险动作
不能 execute approved action
```

注意：

```text
不能审批自己发起的动作。
```

## EXECUTOR

```text
不能 approve
不能 reject
可以 execute approved action
```

适合执行已授权动作的服务或岗位。

## ADMIN

```text
可以 approve
可以 reject
可以 execute approved action
```

但仍然受规则限制：

```text
如果是 requester 自己，也不能 approve 自己的 action。
```

---

# 11. 常见路径复盘

## 11.1 需要审批

```text
PolicyDecision = WAITING_APPROVAL
  -> ApprovalService.requestApproval
  -> APPROVAL_REQUIRED
  -> EXECUTION_SKIPPED
  -> status=WAITING_APPROVAL
```

重点：

```text
此时工具没有执行。
```

## 11.2 审批通过

```text
POST /api/approvals/approve
  -> assertCanApprove
  -> ApprovalService.approve
  -> ExecutionLease
  -> APPROVAL_GRANTED
```

重点：

```text
审批通过不等于执行，只是生成 lease。
```

## 11.3 执行已批准动作

```text
POST /api/approvals/execute
  -> assertCanExecuteApprovedAction
  -> consumeLease
  -> EXECUTION_LEASE_CONSUMED
  -> EXECUTION_STARTED
  -> TOOL_EXECUTED
  -> VERIFICATION_STARTED
  -> VERIFICATION_PASSED / FAILED
  -> APPROVED_ACTION_EXECUTED
```

重点：

```text
执行前必须消费 lease。
```

---

# 12. 小练习

## 练习 1

`APPROVER` 和 `EXECUTOR` 有什么区别？

答案：

```text
APPROVER 能批准/拒绝中风险动作，但不能执行 lease。EXECUTOR 能执行已批准动作，但不能批准/拒绝。
```

## 练习 2

为什么 requester 不能 approve 自己的 action？

答案：

```text
为了防止自批自审。风险操作必须有独立的人类审批。
```

## 练习 3

为什么 approve 之后不直接执行工具？

答案：

```text
职责分离。approve 只产生 ExecutionLease，execute 需要有 EXECUTE_APPROVED_ACTION 权限，并且要消费 lease。
```

## 练习 4

actionHash 解决什么问题？

答案：

```text
保证执行动作和审批动作一致，防止批准 A 却执行 B。
```

## 练习 5

默认没有传角色时是什么角色？有什么权限？

答案：

```text
默认 OPERATOR，没有审批、拒绝、执行已批准动作的特殊权限。
```

---

# 13. 本章最后记忆

如果只记一句：

```text
Role/Permission 不是为了登录，而是为了控制谁能批准和执行风险操作。
```

如果记三句：

```text
1. Permission 是具体能力，Role 是权限集合。
2. requester 不能 approve 自己的 action。
3. approval 通过只生成 lease，真正执行前必须校验并消费 lease。
```

最重要的安全链路：

```text
WAITING_APPROVAL
  -> APPROVAL_GRANTED
  -> EXECUTION_LEASE_CONSUMED
  -> TOOL_EXECUTED
  -> VERIFICATION_PASSED / FAILED
```
