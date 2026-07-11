# 15.6 LLM Planning 审计闭环

## 这一节解决什么问题

真实 LLM 接入后，planner 阶段不再只是“生成计划成功/失败”。

我们需要区分：

```text
Provider 调用失败
模型输出被系统拒绝
模型规划成功
```

所以新增 LLM planning 专用审计事件。

## 审计事件

```text
LLM_PLANNING_STARTED
LLM_PLANNING_COMPLETED
LLM_PLANNING_FAILED
LLM_PLAN_REJECTED
```

## 成功路径

```text
LLM_PLANNING_STARTED
  -> LLM_PLANNING_COMPLETED
  -> PLAN_GENERATED
```

含义：

```text
模型规划开始。
模型返回内容。
内容经过 strict parse 和 validator。
系统生成 TaskPlan。
```

## Provider 失败路径

```text
LLM_PLANNING_STARTED
  -> LLM_PLANNING_FAILED
  -> PLANNING_FAILED
  -> FINAL_RESPONSE
```

典型场景：

```text
apiKey 错误
401/403
429 限流
timeout
provider 5xx
empty response
oversize response
```

审计 payload 记录：

```text
errorType
errorMessage
providerErrorCode
```

不记录：

```text
prompt
rawResponse
apiKey
Authorization
```

## 模型输出被拒绝路径

```text
LLM_PLANNING_STARTED
  -> LLM_PLAN_REJECTED
  -> PLANNING_FAILED
  -> FINAL_RESPONSE
```

典型场景：

```text
unknown tool
unsupported argument
required argument missing
unknown JSON field
too many steps
response too large
```

审计 payload 记录：

```text
errorType
errorMessage
validationErrorCode
```

## 为什么仍保留 PLANNING_FAILED

`LLM_PLANNING_FAILED` 和 `LLM_PLAN_REJECTED` 是 LLM 专用事件。

`PLANNING_FAILED` 是通用 planner 失败事件。

保留通用事件的价值：

```text
1. 不破坏旧的审计语义。
2. Controller/API 不需要关心 planner 类型。
3. 所有 planner 失败都有统一终态。
```

## failed vs rejected

```text
LLM_PLANNING_FAILED：Provider 层没成功拿到可用内容。
LLM_PLAN_REJECTED：拿到了模型内容，但系统验证拒绝。
```

排查方向不同：

```text
failed -> 查 provider、网络、鉴权、限流、响应体。
rejected -> 查模型输出、prompt、validator 规则、工具 schema。
```

## 一句话记忆

```text
LLM failed 是模型调用失败，LLM rejected 是模型输出不合规。
```

## 练习

1. 为什么 `LLM_PLAN_REJECTED` 后仍然要记录 `PLANNING_FAILED`？
2. unknown tool 应该进入 `LLM_PLANNING_FAILED` 还是 `LLM_PLAN_REJECTED`？
3. 为什么审计 payload 不能记录 raw response？
