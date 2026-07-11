# 第十四章：Role、Permission 与风险操作授权

本章讲 SafeOps Agent 里的身份、角色、权限和中高风险操作授权。

这一章不是讲“登录系统”，而是讲：

```text
当 Agent 想执行一个有风险的操作时，谁有资格批准，谁有资格执行，系统如何防止越权和自批自审。
```

## 本章目标

学完本章，你应该能回答：

```text
1. Role 和 Permission 有什么区别？
2. Actor 是谁？ActorType 有什么用？
3. 请求头如何解析成当前操作者？
4. APPROVER、EXECUTOR、ADMIN 分别能做什么？
5. 为什么 requester 不能 approve 自己的 action？
6. 中风险操作为什么要 approval lease？
7. approvalId、leaseId、actionHash 分别解决什么问题？
8. 审批通过后为什么还要消费 ExecutionLease 才能执行？
9. 当前代码先实现中风险权限，高风险权限未来如何按同一模式扩展？
```

## 推荐阅读顺序

1. `full-lesson.md`：完整复习课，先建立整体理解。
2. `01-source-walkthrough.md`：源码走读，第二遍对照代码学习。

## 本章工程主线

```text
HTTP Request
  -> ActorMethodArgumentResolver
  -> ActorResolver
  -> Actor(role + permission)
  -> ApprovalController
  -> ApproverPolicy
  -> ApprovalService
  -> ExecutionLease
  -> ApprovedActionExecutor
  -> Tool Execution
```

## 本章核心类

```text
Actor
ActorType
Role
Permission
ActorResolver
ActorMethodArgumentResolver
CurrentActor
ApproverPolicy
ApprovalService
ApprovalRecord
ExecutionLease
ApprovedActionExecutor
ApprovalController
ActionHasher
```

## 本章记忆点

```text
1. Role 是权限集合，Permission 是具体能力。
2. Actor 是当前操作者，不等于原始请求用户。
3. 默认角色是 OPERATOR，没有审批和执行已批准动作的权限。
4. APPROVER 可以批准/拒绝中风险操作，但不能执行 lease。
5. EXECUTOR 可以执行已批准动作，但不能批准。
6. ADMIN 同时拥有审批、拒绝和执行已批准动作权限。
7. requester 不能 approve 自己的 action，防止自批自审。
8. 非 HUMAN actor 不能 approve/reject approval。
9. approval 通过后只生成 lease，不等于立即执行。
10. ExecutionLease 必须匹配 actionHash，防止批准 A 却执行 B。
```
