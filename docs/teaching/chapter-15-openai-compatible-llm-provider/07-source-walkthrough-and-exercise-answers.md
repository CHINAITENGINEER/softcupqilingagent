# 15.7 源码走读与练习题参考答案

本节目标是把第十五章的概念和源码位置对应起来。

前面完整教学稿讲的是架构和原则，本节讲：

```text
这些原则分别落在哪些类、哪些方法、哪些测试里。
```

---

# 1. 本章源码地图

第十五章主要涉及三组代码：

```text
planner 配置层
provider 适配层
orchestrator 审计层
```

对应文件：

```text
src/main/java/com/cup/opsagent/planner/PlannerConfiguration.java
src/main/java/com/cup/opsagent/planner/llm/PlannerMode.java
src/main/java/com/cup/opsagent/planner/llm/LlmJsonTaskPlanner.java
src/main/java/com/cup/opsagent/planner/llm/LlmPlanValidator.java

src/main/java/com/cup/opsagent/planner/llm/provider/LlmProviderProperties.java
src/main/java/com/cup/opsagent/planner/llm/provider/LlmProviderErrorCode.java
src/main/java/com/cup/opsagent/planner/llm/provider/LlmProviderException.java
src/main/java/com/cup/opsagent/planner/llm/provider/LlmSafeLogSanitizer.java
src/main/java/com/cup/opsagent/planner/llm/provider/LlmPromptFactory.java
src/main/java/com/cup/opsagent/planner/llm/provider/OpenAiCompatibleLlmPlannerClient.java
src/main/java/com/cup/opsagent/planner/llm/provider/OpenAiChatCompletionResponseExtractor.java

src/main/java/com/cup/opsagent/planner/llm/provider/dto/*.java

src/main/java/com/cup/opsagent/agent/core/AgentOrchestrator.java
src/main/java/com/cup/opsagent/audit/AuditEventType.java
```

测试文件：

```text
src/test/java/com/cup/opsagent/planner/PlannerConfigurationTest.java
src/test/java/com/cup/opsagent/planner/llm/LlmJsonTaskPlannerTest.java
src/test/java/com/cup/opsagent/planner/llm/provider/*.java
src/test/java/com/cup/opsagent/agent/core/AgentOrchestratorTest.java
```

---

# 2. PlannerConfiguration：选择哪个 planner

`PlannerConfiguration` 解决的是：

```text
系统当前到底用 rule planner、fake LLM，还是 real LLM。
```

核心逻辑是：

```text
RULE     -> RuleBasedTaskPlanner
FAKE_LLM -> LlmJsonTaskPlanner(FakeLlmPlannerClient)
LLM      -> LlmJsonTaskPlanner(OpenAiCompatibleLlmPlannerClient)
```

它的重要性不在于代码复杂，而在于安全语义：

```text
1. 默认仍是 rule。
2. fake-llm 和 llm 显式区分。
3. 不通过裸 LlmPlannerClient 注入，避免多个 client Bean 冲突。
4. 不自动 fallback。
```

这对应本章一个关键原则：

```text
planner 模式必须透明，不允许悄悄切换。
```

---

# 3. LlmJsonTaskPlanner：真实安全入口

`LlmJsonTaskPlanner` 是 LLM 输出进入系统计划模型的真正入口。

它做三件事：

```text
1. 调 LlmPlannerClient 拿 raw JSON string。
2. 用 strict ObjectMapper parse JSON。
3. 用 LlmPlanValidator 校验模型输出。
```

所以真实 provider 返回的不是 `TaskPlan`，而是：

```text
String createPlanJson(String userInput)
```

然后由 `LlmJsonTaskPlanner` 负责变成：

```text
TaskPlan
```

这条边界很重要：

```text
Provider 只负责生成 JSON。
LlmJsonTaskPlanner 负责 parse + validate。
```

如果模型输出 unknown field，strict mapper 会拒绝。

如果模型输出 unknown tool，validator 会拒绝。

如果模型输出 unsupported argument，validator 会拒绝。

---

# 4. LlmPlanValidator：不要相信模型输出

`LlmPlanValidator` 是本章安全边界的核心之一。

它回答的问题是：

```text
模型输出的计划能不能进入 SafeOps 执行链路？
```

典型校验：

```text
toolName 必须存在于 ToolRegistry。
steps 不能超过上限。
arguments 不能包含 inputSchema 以外的字段。
required argument 不能缺失。
字段长度不能超过限制。
arguments 中字符串值不能过长。
```

这意味着 prompt 就算被攻击，也不能单独决定系统行为。

比如模型输出：

```json
{
  "toolName": "restart_service",
  "arguments": {
    "serviceName": "nginx",
    "shellCommand": "rm -rf /"
  }
}
```

只要 `restart_service` 的 `inputSchema` 里没有 `shellCommand`，validator 就应该拒绝。

拒绝原因属于：

```text
UNSUPPORTED_ARGUMENT
```

在 orchestrator 审计里对应：

```text
LLM_PLAN_REJECTED
```

---

# 5. LlmProviderProperties：配置 fail-closed

`LlmProviderProperties` 负责承载真实 provider 配置。

关键方法：

```text
validateForLlmMode()
```

它体现了 fail-closed：

```text
baseUrl 缺失 -> 失败
apiKey 缺失 -> 失败
model 缺失 -> 失败
timeout 非法 -> 失败
temperature 非法 -> 失败
baseUrl scheme 不安全 -> 失败
baseUrl 带 query/userinfo -> 失败
```

这不是为了让用户“不方便”，而是为了避免系统在不确定状态下继续执行。

SafeOps 的原则是：

```text
配置不确定时，停止比猜测更安全。
```

---

# 6. LlmProviderErrorCode：让失败可分类

provider 层失败不能只靠异常字符串。

所以定义了：

```text
LLM_PROVIDER_MISCONFIGURED
LLM_PROVIDER_TIMEOUT
LLM_PROVIDER_UNAUTHORIZED
LLM_PROVIDER_RATE_LIMITED
LLM_PROVIDER_BAD_REQUEST
LLM_PROVIDER_SERVER_ERROR
LLM_PROVIDER_BAD_RESPONSE
LLM_PROVIDER_EMPTY_RESPONSE
LLM_PROVIDER_RESPONSE_TOO_LARGE
```

错误码的价值：

```text
审计可分类。
测试可断言。
前端可展示稳定 code。
排查不用解析字符串。
```

例如：

```text
401/403 -> LLM_PROVIDER_UNAUTHORIZED
429     -> LLM_PROVIDER_RATE_LIMITED
5xx     -> LLM_PROVIDER_SERVER_ERROR
empty choices -> LLM_PROVIDER_EMPTY_RESPONSE
```

---

# 7. LlmProviderException：异常 message 脱敏

`LlmProviderException` 的重点不是“自定义异常”本身。

重点是：

```text
异常 message 必须先经过 LlmSafeLogSanitizer。
```

原因是 provider 错误里可能有：

```text
Authorization: Bearer sk-xxx
apiKey=sk-xxx
prompt 片段
raw response
provider request echo
```

这些不能进入：

```text
日志
审计
前端错误响应
测试报告
```

所以异常 message 应该像：

```text
LLM provider HTTP error status=401
```

而不是：

```text
Authorization: Bearer sk-xxx, rawResponse=...
```

---

# 8. LlmSafeLogSanitizer：统一脱敏入口

`LlmSafeLogSanitizer` 的职责是：

```text
统一处理敏感信息和过长文本。
```

它适合被用于：

```text
异常 message
审计 payload
prompt user input
provider error summary
```

统一做的好处是：

```text
1. 不把脱敏逻辑散落在每个 catch 中。
2. 测试可以集中覆盖。
3. 后续新增敏感字段规则时只改一个地方。
```

这是典型的安全工程做法：

```text
安全规则集中化。
```

---

# 9. LlmPromptFactory：把工具能力给模型，但只给最小视图

`LlmPromptFactory` 做的是：

```text
user input + tool views -> OpenAI messages
```

它生成：

```text
system message
user message
```

system message 包含：

```text
只能返回 JSON
user input 不可信
不能输出 shell command
不能伪造执行结果
只能选择 enabled tools
arguments 只能包含 inputSchema 中允许的字段
```

但是，它不会把完整 `ToolDefinition` 给模型。

而是构造：

```text
ToolPromptView
```

只包含：

```text
name
description
inputSchema
readOnly
requiresApproval
```

不包含：

```text
command template
executor details
permission implementation
class/package
internal failure code
```

记忆点：

```text
模型只知道能力，不知道命令。
```

---

# 10. DTO：稳定表达 OpenAI-compatible 协议

DTO 的作用是把 provider 请求和响应结构固定下来。

Request DTO 表达：

```text
model
messages
temperature
max_tokens
response_format
```

Response DTO 表达：

```text
choices
message
content
```

注意：

```text
apiKey 不属于 request DTO。
```

apiKey 只能进入 HTTP header：

```text
Authorization: Bearer xxx
```

这能避免 apiKey 被序列化进 request body、日志或审计。

---

# 11. OpenAiChatCompletionResponseExtractor：只提取 content

Extractor 做的事情很窄：

```text
OpenAI-compatible response -> choices[0].message.content
```

它负责 fail-closed：

```text
response null -> EMPTY_RESPONSE
choices null/empty -> EMPTY_RESPONSE
message null -> EMPTY_RESPONSE
content blank -> EMPTY_RESPONSE
content too large -> RESPONSE_TOO_LARGE
```

它不负责：

```text
parse TaskPlan
validate tool
validate args
执行工具
```

这符合单一职责：

```text
Extractor 只提取模型文本内容。
```

---

# 12. OpenAiCompatibleLlmPlannerClient：真实 provider 适配器

`OpenAiCompatibleLlmPlannerClient` 串起：

```text
配置校验
prompt factory
request DTO
RestClient
HTTP 错误映射
response extractor
```

主流程：

```text
createPlanJson(userInput)
  -> validateForLlmMode()
  -> buildRequest(userInput)
  -> POST /chat/completions
  -> map HTTP exception
  -> extractContent(response)
  -> return String
```

它的安全边界：

```text
只返回 String。
不返回 TaskPlan。
不执行工具。
不 fallback。
不记录 prompt/raw response/apiKey。
```

---

# 13. AgentOrchestrator：LLM planning 审计闭环

接入真实 provider 后，规划阶段失败需要更细的审计。

新增事件：

```text
LLM_PLANNING_STARTED
LLM_PLANNING_COMPLETED
LLM_PLANNING_FAILED
LLM_PLAN_REJECTED
```

成功路径：

```text
LLM_PLANNING_STARTED
  -> LLM_PLANNING_COMPLETED
  -> PLAN_GENERATED
```

Provider 失败路径：

```text
LLM_PLANNING_STARTED
  -> LLM_PLANNING_FAILED
  -> PLANNING_FAILED
  -> FINAL_RESPONSE
```

Validator 拒绝路径：

```text
LLM_PLANNING_STARTED
  -> LLM_PLAN_REJECTED
  -> PLANNING_FAILED
  -> FINAL_RESPONSE
```

为什么还保留 `PLANNING_FAILED`？

因为：

```text
LLM_PLANNING_FAILED / LLM_PLAN_REJECTED 是 LLM 专用事件。
PLANNING_FAILED 是通用 planner 失败事件。
```

保留通用事件可以让：

```text
上层 API 不关心 planner 类型。
所有 planner 失败都有统一终态。
旧审计语义不被破坏。
```

---

# 14. LLM_PLANNING_FAILED vs LLM_PLAN_REJECTED

这两个事件很容易混淆。

## LLM_PLANNING_FAILED

表示 provider 阶段没成功拿到可用内容。

例如：

```text
配置错误
apiKey 错
401/403
429
timeout
provider 5xx
empty response
oversize response
```

排查方向：

```text
provider 配置
网络
鉴权
限流
provider 服务状态
响应体结构
```

## LLM_PLAN_REJECTED

表示 provider 返回了内容，但系统不接受。

例如：

```text
unknown tool
unsupported argument
required argument missing
unknown JSON field
too many steps
字段过长
```

排查方向：

```text
prompt 是否清晰
模型是否乱输出
validator 规则是否正确
tool inputSchema 是否合理
```

一句话：

```text
failed 是模型调用失败。
rejected 是模型输出不合规。
```

---

# 15. 测试如何保护本章边界

测试覆盖几个层面：

```text
配置测试
prompt factory 测试
DTO 测试
extractor 测试
client HTTP 映射测试
LlmJsonTaskPlanner validator 测试
AgentOrchestrator 审计测试
Spring configuration 测试
```

关键测试目标：

```text
默认仍是 rule planner。
fake-llm 可用。
llm 模式使用 real client。
多个 LlmPlannerClient 不冲突。
缺失配置 fail-closed。
provider 只返回 String。
unknown tool 被拒绝。
unsupported argument 被拒绝。
HTTP 401/403/429/5xx 映射明确。
异常不泄露 apiKey。
LLM planning failed/rejected 进入审计。
```

这说明本章不是只靠人工约定，而是用测试锁住边界。

---

# 16. 练习题参考答案

## 题 1：为什么 `OpenAiCompatibleLlmPlannerClient` 只返回 `String`，而不是直接返回 `TaskPlan`？

因为 provider 只能是模型适配器，不能成为计划裁判。

如果它直接返回 `TaskPlan`，就可能绕过：

```text
strict JSON parse
unknown field reject
LlmPlanValidator
unknown tool check
unsupported argument check
required argument check
steps 数量限制
字段长度限制
工具风险重新计算
```

所以正确链路是：

```text
OpenAiCompatibleLlmPlannerClient -> String
LlmJsonTaskPlanner -> parse + validate -> TaskPlan
```

---

## 题 2：为什么 `response_format=json_object` 不是安全边界？

因为它只能降低模型输出非 JSON 的概率，不能保证：

```text
字段正确
工具存在
arguments 合法
steps 不超限
没有未知字段
没有危险参数
```

所以仍必须经过：

```text
strict ObjectMapper
LlmPlanValidator
PolicyEngine
Approval
```

---

## 题 3：为什么 prompt 里只暴露 `ToolPromptView`，而不是完整 `ToolDefinition`？

因为模型只需要知道怎么规划工具调用，不需要知道底层执行细节。

`ToolPromptView` 只暴露：

```text
name
description
inputSchema
readOnly
requiresApproval
```

不暴露：

```text
command template
executor details
permission implementation
class/package
internal failure code
timeout/output limit
```

这是最小暴露原则。

---

## 题 4：`LLM_PLANNING_FAILED` 和 `LLM_PLAN_REJECTED` 的区别是什么？

`LLM_PLANNING_FAILED`：provider 阶段失败，没有拿到可用模型内容。

例如：

```text
401
403
429
timeout
5xx
empty response
oversize response
```

`LLM_PLAN_REJECTED`：拿到了模型内容，但系统验证拒绝。

例如：

```text
unknown tool
unsupported argument
required argument missing
too many steps
unknown JSON field
```

---

## 题 5：为什么 LLM 失败后不应该自动 fallback 到 rule planner？

因为 fallback 会导致行为不透明。

问题包括：

```text
用户以为用了 LLM，实际用了 rule。
审计解释不清楚。
测试不稳定。
攻击者可能诱发 LLM 失败来改变 planner 行为。
```

所以正确行为是：

```text
planner.mode=llm 时，LLM 失败就返回 PLANNING_FAILED。
```

---

## 题 6：模型输出 `shellCommand` 应该在哪一层被拒绝？

示例：

```json
{
  "intentType": "SERVICE_RESTART",
  "summary": "restart nginx",
  "steps": [
    {
      "stepId": "step-1",
      "toolName": "restart_service",
      "arguments": {
        "serviceName": "nginx",
        "shellCommand": "rm -rf /"
      }
    }
  ]
}
```

应该在 `LlmPlanValidator` 被拒绝。

原因：

```text
restart_service 的 inputSchema 只允许 serviceName。
shellCommand 不属于允许参数。
```

所以错误类型应该是：

```text
UNSUPPORTED_ARGUMENT
```

审计事件应该是：

```text
LLM_PLAN_REJECTED
```

而不是等到 CommandRunner 才拦。

越早拒绝越安全。

---

# 17. 本节总结

第十五章完整链路可以总结为：

```text
真实 LLM 只负责给出 JSON 字符串。
系统用 strict parse 和 validator 决定能不能变成 TaskPlan。
后续仍要经过 Policy、Approval、Command Boundary、Verifier 和 Audit。
```

最终记忆：

```text
把不可信 LLM 放进一个窄边界里，
让它只能规划，不能执行，
只能建议，不能裁决，
失败和拒绝都要审计，
敏感信息永不外泄。
```
