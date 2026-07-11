# 15.1 Provider 职责和安全边界

## 这一节解决什么问题

真实 LLM Provider 的职责必须非常窄：

```text
自然语言 -> 模型返回的 JSON 字符串
```

它不能成为新的 planner 裁判，也不能绕过后续安全链路。

## 关键边界

Provider 允许做：

```text
1. 构造 OpenAI-compatible request。
2. 调用 HTTP API。
3. 映射 HTTP 错误。
4. 提取 choices[0].message.content。
5. 返回 JSON 字符串。
```

Provider 禁止做：

```text
1. 直接构造 TaskPlan。
2. 直接构造 PlanStep。
3. 调用 ToolRegistry 执行工具。
4. 调用 CommandRunner。
5. 绕过 LlmPlanValidator。
6. 绕过 PolicyEngine。
7. 绕过 Approval。
8. 自动 fallback 到 rule planner。
9. 把 prompt/raw response/apiKey 写入日志或审计。
```

## 为什么 Provider 不能直接返回 TaskPlan

如果 provider 直接返回 `TaskPlan`，会绕过：

```text
strict ObjectMapper
unknown field reject
unknown tool reject
unsupported argument reject
required argument check
too many steps check
字段长度限制
工具风险重新计算
```

所以当前设计坚持：

```text
OpenAiCompatibleLlmPlannerClient -> String
LlmJsonTaskPlanner -> parse + validate + TaskPlan
```

## 一句话记忆

```text
Provider 是模型适配器，不是计划裁判。
```

## 练习

1. 为什么真实 provider 只实现 `LlmPlannerClient`？
2. 如果 provider 直接构造 `TaskPlan`，会绕过哪些安全检查？
3. 为什么 provider 不应该知道 `CommandRunner`？
