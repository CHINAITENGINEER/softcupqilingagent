# OpenAI-Compatible LLM Provider 实现计划审计

审计日期：2026-06-30

审计对象：`qilingos-safeops-agent` OpenAI-Compatible LLM Provider 实现计划

当前项目关键现状：

- 已有 `RuleBasedTaskPlanner`
- 已有 `FakeLlmPlannerClient`
- 已有 `LlmJsonTaskPlanner`
- 已有 `LlmPlanValidator`
- `LlmPlannerClient` 当前接口只返回 JSON 字符串：`String createPlanJson(String userInput)`
- `LlmJsonTaskPlanner` 使用 strict `ObjectMapper`，开启 `FAIL_ON_UNKNOWN_PROPERTIES`
- `LlmPlanValidator` 已校验 tool 存在、steps 数量、required 参数、unsupported 参数
- `AgentOrchestrator` 后续仍会走 Policy、Approval、Audit、Tool Execution

## 1. 总体结论

这份实现计划的主方向是正确的：真实 LLM Provider 只实现 `LlmPlannerClient`，只负责“自然语言 -> 模型 JSON 字符串”，不直接构造 `TaskPlan`，不执行工具，不绕过 `LlmPlanValidator`。

最重要的安全边界也基本清楚：

```text
OpenAiCompatibleLlmPlannerClient
  -> raw JSON string
  -> LlmJsonTaskPlanner
  -> strict JSON parse
  -> LlmPlanValidator
  -> TaskPlan
  -> SafetyPolicy
  -> ApprovalPolicy
  -> Audit
  -> Tool Execution
```

但是，进入真实 Provider 前必须补齐几个硬约束：

1. 多个 `LlmPlannerClient` Bean 的注入冲突必须先解决。
2. LLM 规划失败、Validator 拒绝、HTTP 失败必须有审计闭环，且不能泄露 prompt/apiKey/raw response。
3. 真实 Provider 的异常、日志、audit payload 必须统一脱敏和截断。
4. 不应自动 fallback 到 rule planner。
5. Prompt 只能作为第一层提示，安全必须依赖 `LlmPlanValidator + PolicyEngine + Approval`。
6. LLM 输出长度、字段长度、错误内容长度需要显式限制，避免日志/内存/审计污染。

## 2. 主要风险清单

### R1：多个 `LlmPlannerClient` Bean 导致 Spring 注入冲突

当前 `PlannerConfiguration` 注入的是单个 `LlmPlannerClient`：

```java
LlmPlannerClient llmPlannerClient
```

如果新增 `OpenAiCompatibleLlmPlannerClient` 并标注为 Spring bean，同时 `FakeLlmPlannerClient` 也存在，就会出现候选 Bean 冲突，或误选真实 provider。

风险等级：高

原因：启动失败是显性问题，误注入真实 provider 是更危险的问题，会导致测试/本地环境意外访问外部 LLM。

### R2：Planner 阶段异常目前可能缺少审计闭环

`AgentOrchestrator` 在生成 plan 时直接调用：

```java
TaskPlan plan = taskPlanner.plan(request.message());
```

如果真实 provider 抛 `LlmProviderException`、`LlmPlanValidationException` 或 JSON parse error，需要确保：

- 不泄露敏感数据
- 有 `FINAL_RESPONSE`
- AuditTrace 有终态
- 用户得到安全的失败响应

否则真实 LLM 接入后，最容易出现“规划阶段异常直接冒泡”的可观测性缺口。

风险等级：高

### R3：API key / Authorization / prompt / raw response 泄露

计划已经提出不能记录：

- Authorization header
- apiKey
- 完整 prompt
- 完整 user input
- 完整 raw response

这是正确的，但还需要工程化落点，否则很容易通过以下路径泄露：

- `LlmProviderException.message`
- RestClient error body
- debug log
- `@ConfigurationProperties` 对象 `toString`
- audit payload
- 测试失败日志

风险等级：高

### R4：Prompt injection 防护不能依赖 prompt 本身

Prompt Factory 中加入 JSON-only、不可执行 shell、不可伪造结果等规则是必要的，但它只能降低模型乱输出概率，不能作为安全边界。

真正边界必须是：

- strict JSON parse
- unknown field reject
- `LlmPlanValidator`
- `PolicyEngine`
- `ToolRegistry`
- `Approval`
- `CommandTemplateRegistry`

风险等级：中高

### R5：ToolRegistry 暴露给模型的字段需要最小化

计划建议暴露：

- name
- description
- inputSchema
- readOnly
- requiresApproval

这个范围基本合理。不要暴露：

- command template
- permission implementation
- executor details
- system path
- internal class/package
- failureCode/internal audit event

是否暴露 `riskLevel` 要谨慎。模型给出的 risk 不能可信，系统会重新计算风险。暴露 `requiresApproval` 已足够让模型知道“该工具可能需要审批”。

风险等级：中

### R6：`response_format=json_object` 合理但不是安全保证

`response_format={"type":"json_object"}` 可以降低非 JSON 输出概率，但不能保证 schema 正确，也不能防止额外字段、未知工具、危险参数。

必须继续依赖：

- `FAIL_ON_UNKNOWN_PROPERTIES`
- `LlmPlanValidator`
- SafetyPolicy

风险等级：中

### R7：HTTP 错误分类需要避免把上游错误体原样抛出

HTTP 400/401/403/429/5xx 映射是合理的，但很多 provider 的错误体可能包含 request echo、prompt 片段、trace 信息。异常 message 只能包含 sanitized summary。

风险等级：中

### R8：自动 fallback 到 rule planner 会制造安全和审计语义混乱

计划中“不做自动 fallback”是正确的。

如果用户配置 `planner.mode=llm`，真实 LLM 失败后自动 fallback 到 rule planner，会导致：

- 用户以为使用了 LLM，实际用了规则
- audit 难以解释
- 安全测试不稳定
- 攻击者可通过诱发 LLM 错误改变 planner 行为

风险等级：中

### R9：LLM 输出字段长度和响应体大小未明确限制

当前计划有 `maxOutputTokens=1000`，但这只是请求侧约束，不应完全信任 provider。还需要本地限制：

- raw response 最大读取大小
- content 最大长度
- intentType / summary / reason / actionName / stepId 最大长度
- arguments 中字符串值最大长度

风险等级：中

## 3. 必须修改项

### M1：先解决 LlmPlannerClient Bean 冲突

必须在新增真实 provider 前调整 `PlannerConfiguration`。

推荐：

```java
@Bean
@Primary
TaskPlanner taskPlanner(
    RuleBasedTaskPlanner ruleBasedTaskPlanner,
    FakeLlmPlannerClient fakeLlmPlannerClient,
    OpenAiCompatibleLlmPlannerClient openAiCompatibleLlmPlannerClient,
    ToolRegistry toolRegistry,
    ObjectMapper objectMapper,
    ...
)
```

不要直接注入裸 `LlmPlannerClient`。

`PlannerMode` 增加：

```text
RULE
FAKE_LLM
LLM
```

### M2：真实 Provider 只能返回 String，禁止构造 TaskPlan

需要通过代码结构和测试约束：

- `OpenAiCompatibleLlmPlannerClient` 只实现 `LlmPlannerClient`
- 返回 `choices[0].message.content`
- 不 import `TaskPlan`
- 不 import `PlanStep`
- 不 import `OpsTool`
- 不调用 `ToolRegistry.findTool(...)`
- 不执行任何 tool

建议新增静态架构测试：

- provider 包不得依赖 `com.cup.opsagent.agent.model.TaskPlan`
- provider 包不得依赖 `OpsTool`
- provider 包不得依赖 executor/CommandRunner

### M3：Provider 配置必须 fail-closed

当 `ops-agent.planner.mode=llm` 时，以下配置缺失必须失败：

- `baseUrl`
- `apiKey`
- `model`

建议启动期 fail-fast，而不是首次调用才失败。若为了本地启动体验选择首次调用失败，也必须返回明确错误码：

- `LLM_PROVIDER_MISCONFIGURED`
- `LLM_PROVIDER_DISABLED`

禁止静默 fallback。

### M4：新增统一脱敏/截断工具

不要把脱敏散落在各 catch 里。

建议新增：

```text
com.cup.opsagent.planner.llm.provider.LlmSafeLogSanitizer
```

最小职责：

- redact apiKey
- redact Authorization header
- truncate user input
- truncate provider error body
- truncate model content
- 不输出完整 prompt
- 不输出完整 raw response

### M5：Planner 阶段失败要有审计和安全响应

建议新增 audit event：

```text
LLM_PLANNING_STARTED
LLM_PLANNING_COMPLETED
LLM_PLANNING_FAILED
LLM_PLAN_REJECTED
```

最低要求：

- provider 调用失败有 `LLM_PLANNING_FAILED`
- validator 拒绝有 `LLM_PLAN_REJECTED`
- audit payload 只记录 errorCode、provider、model、durationMs、sanitizedSummary
- `AgentOrchestrator` 最终必须产生 `FINAL_RESPONSE`

### M6：禁止 raw response 默认审计

`auditRawResponse=false` 默认正确。

如果保留该开关，必须额外限制：

- 仅 test/dev profile 可启用
- 截断，例如 2KB
- 脱敏
- 不包含 Authorization / apiKey / prompt

生产环境建议直接禁止。

### M7：Prompt Factory 暴露字段必须最小化

建议只给模型：

- tool name
- description
- inputSchema
- readOnly
- requiresApproval

不要给：

- enabled=false 的工具
- command template
- permission implementation
- package/class 名
- executor 细节
- audit event
- internal failure code

### M8：补充 LLM 输出长度限制

建议在 `LlmPlanValidator` 或 `LlmJsonTaskPlanner` 增加：

- `intentType` 最大长度
- `summary` 最大长度
- `stepId` 最大长度
- `actionName` 最大长度
- `reason` 最大长度
- `arguments` 字符串值最大长度
- raw JSON content 最大长度

这不是替代 `maxOutputTokens`，而是本地 fail-closed。

## 4. 建议修改项

### S1：错误码再补充两个场景

现有建议错误码合理：

- `LLM_PROVIDER_DISABLED`
- `LLM_PROVIDER_MISCONFIGURED`
- `LLM_PROVIDER_TIMEOUT`
- `LLM_PROVIDER_UNAUTHORIZED`
- `LLM_PROVIDER_RATE_LIMITED`
- `LLM_PROVIDER_BAD_REQUEST`
- `LLM_PROVIDER_SERVER_ERROR`
- `LLM_PROVIDER_BAD_RESPONSE`
- `LLM_PROVIDER_EMPTY_RESPONSE`

建议补充：

- `LLM_PROVIDER_UNSUPPORTED_RESPONSE_FORMAT`
- `LLM_PROVIDER_RESPONSE_TOO_LARGE`

### S2：timeout 拆成 connect/read 更清晰

计划只有：

```text
timeoutMs
```

建议改为：

```text
connectTimeoutMs
readTimeoutMs
```

如果为了第一版简单保留单字段，也要明确同时应用到 connect/read，避免只配了 read timeout。

### S3：baseUrl 做基本校验

建议校验：

- scheme 只允许 `https`，本地测试可允许 `http://localhost`
- 不允许 query
- 不允许 userinfo
- 日志只记录 host，不记录完整 URL

### S4：Provider request DTO 不要把 apiKey 放进 DTO

apiKey 只应进入 Authorization header 构造逻辑，不要成为 request body DTO 或可序列化对象字段。

### S5：Prompt 中不要放完整 ToolDefinition

不要直接序列化 `ToolDefinition`。应构造专门的 `ToolPromptView`：

```java
record ToolPromptView(
    String name,
    String description,
    Map<String, String> inputSchema,
    boolean readOnly,
    boolean requiresApproval
) {}
```

### S6：LlmProviderException message 不要包含 cause 原文

可以保留 `cause` 作为 Java exception cause，但 message 只放 sanitized 内容。

建议：

```text
OpenAI-compatible provider failed: code=LLM_PROVIDER_RATE_LIMITED, provider=openai-compatible, model=xxx, status=429, durationMs=1234
```

不要：

```text
requestBody=...
Authorization=...
prompt=...
rawResponse=...
```

### S7：Provider 成功响应也不要直接 audit content

即使 JSON content 看起来只是 plan，也可能包含用户输入片段或模型注入内容。只记录：

- content length
- step count after validation
- model
- durationMs

## 5. 可以延后项

以下计划中延后是合理的：

1. Streaming
2. 多轮上下文
3. 自动 fallback 到 rule planner
4. 原始 prompt/raw response 全量审计
5. 多 provider 动态路由
6. 重试机制
7. function calling
8. tool calling

其中“不做自动 fallback”“不做 tool calling”尤其正确。

原因：

- 当前安全模型是 JSON plan -> validator -> policy -> approval -> tool execution。
- function/tool calling 容易让模型接口语义和工具执行语义混在一起。
- streaming 会增加部分响应、半包 JSON、审计截断等复杂度。

## 6. 推荐最终实现方案

### Package 结构

```text
com.cup.opsagent.planner.llm.provider
  LlmProviderProperties
  LlmProviderErrorCode
  LlmProviderException
  LlmPromptFactory
  OpenAiCompatibleLlmPlannerClient
  LlmSafeLogSanitizer

com.cup.opsagent.planner.llm.provider.dto
  OpenAiChatCompletionRequest
  OpenAiChatMessage
  OpenAiResponseFormat
  OpenAiChatCompletionResponse
  OpenAiChoice
  OpenAiAssistantMessage
  ToolPromptView
```

### 调用链

```text
AgentOrchestrator
  -> TaskPlanner
     -> LlmJsonTaskPlanner
        -> OpenAiCompatibleLlmPlannerClient.createPlanJson(userInput)
           -> LlmPromptFactory
           -> RestClient /v1/chat/completions
           -> choices[0].message.content
        -> strict ObjectMapper
        -> LlmPlanValidator
        -> TaskPlan
  -> PolicyEngine
  -> Approval
  -> Audit
  -> Tool Execution
```

### PlannerConfiguration

推荐显式注入 fake 和 real：

```text
RULE -> RuleBasedTaskPlanner
FAKE_LLM -> new LlmJsonTaskPlanner(fakeLlmPlannerClient, toolRegistry, objectMapper)
LLM -> new LlmJsonTaskPlanner(openAiCompatibleLlmPlannerClient, toolRegistry, objectMapper)
```

不要靠 `@Primary LlmPlannerClient`。

### Provider 职责

允许：

- 构造 OpenAI-compatible request
- 调 HTTP
- 映射 HTTP 错误
- 提取 `choices[0].message.content`
- 返回 String

禁止：

- parse 成 `LlmPlanResponse`
- validate tool
- 创建 `TaskPlan`
- 调用 SafetyPolicy
- 调用 ToolRegistry 执行能力
- fallback 到 rule planner
- 写完整 prompt/raw response 到日志

### response_format

第一版可以默认启用：

```json
{"type":"json_object"}
```

但要有配置：

```text
ops-agent.llm.response-format-json-object=true
```

因为部分 OpenAI-compatible provider 不支持该字段。

无论是否启用，都必须经过 `LlmPlanValidator`。

## 7. 不应该做的设计

### D1：不要让真实 Provider 直接返回 TaskPlan

原因：会绕过 strict JSON parse 和 validator 的单一入口，破坏当前安全边界。

### D2：不要自动 fallback 到 rule planner

原因：会造成 planner 行为不透明，审计难解释，也可能被攻击者通过诱发 LLM 错误改变系统行为。

### D3：不要记录完整 prompt/raw response

原因：prompt 包含工具列表、系统约束、用户输入；raw response 可能包含注入文本、敏感回显、错误体。默认禁止是正确选择。

### D4：不要在第一版接入 OpenAI tool calling/function calling

原因：当前项目安全模型是 ToolCall JSON 经过 validator/policy/approval，而不是模型直接调用工具。tool calling 容易混淆边界。

### D5：不要向模型暴露命令模板或执行器细节

原因：模型只需要知道可规划的工具，不需要知道底层命令结构。暴露底层命令会增加 prompt injection 和攻击面。

## 8. 测试计划评价

计划中的测试方向是对的，但还不够。建议扩充为以下矩阵。

### Prompt Factory

必须覆盖：

- system prompt 包含 JSON-only 约束
- system prompt 声明 user input 不可信
- system prompt 禁止 shell command
- system prompt 禁止伪造执行结果
- tool list 只来自 enabled tools
- prompt 不包含 apiKey
- prompt 不包含 Authorization
- prompt 不包含 command template
- prompt 不包含 executor/package/class 细节
- tool 字段仅包含 `name/description/inputSchema/readOnly/requiresApproval`

### Provider 成功响应

必须覆盖：

- 2xx + valid choices -> 返回 content 字符串
- content 为空 -> `LLM_PROVIDER_EMPTY_RESPONSE`
- choices 为空 -> `LLM_PROVIDER_EMPTY_RESPONSE`
- message 缺失 -> `LLM_PROVIDER_EMPTY_RESPONSE`
- content 超长 -> `LLM_PROVIDER_RESPONSE_TOO_LARGE`

### HTTP 错误

必须覆盖：

- 400 -> `LLM_PROVIDER_BAD_REQUEST`
- 401/403 -> `LLM_PROVIDER_UNAUTHORIZED`
- 429 -> `LLM_PROVIDER_RATE_LIMITED`
- 5xx -> `LLM_PROVIDER_SERVER_ERROR`
- 其他非 2xx -> `LLM_PROVIDER_BAD_RESPONSE`
- timeout -> `LLM_PROVIDER_TIMEOUT`
- malformed response JSON -> `LLM_PROVIDER_BAD_RESPONSE`

### 敏感信息不泄露

必须覆盖：

- exception message 不包含 apiKey
- exception message 不包含 Authorization
- exception message 不包含完整 prompt
- exception message 不包含完整 user input
- exception message 不包含完整 raw response
- audit payload 不包含 apiKey/prompt/raw response

### LlmJsonTaskPlanner 集成

必须覆盖：

- unknown tool 被 `LlmPlanValidator` 拒绝
- unsupported argument 被拒绝
- required argument missing 被拒绝
- unknown JSON field 被 strict mapper 拒绝
- too many steps 被拒绝
- model suggestedRiskLevel 不决定最终 risk，最终用工具 risk
- restart_service 输出进入 WAITING_APPROVAL，而不是执行

### Spring 配置

必须覆盖：

- `planner.mode=rule` 不创建或不使用真实 provider
- `planner.mode=fake-llm` 使用 fake client
- `planner.mode=llm` 使用 real client
- 多个 `LlmPlannerClient` 不冲突
- llm mode 缺少 apiKey/baseUrl/model fail-closed
- 默认仍是 rule

### Audit

建议覆盖：

- `LLM_PLANNING_STARTED`
- `LLM_PLANNING_COMPLETED`
- `LLM_PLANNING_FAILED`
- `LLM_PLAN_REJECTED`
- payload 不包含敏感字段

## 9. 更好的实现顺序

推荐顺序调整为：

1. 修改 `PlannerMode / PlannerConfiguration`，先解决多 client 注入模型。
2. 新增 `LlmProviderProperties`，加配置校验。
3. 新增 `LlmProviderErrorCode / LlmProviderException`。
4. 新增 `LlmSafeLogSanitizer`。
5. 新增 `LlmPromptFactory` 和 `ToolPromptView`。
6. 新增 OpenAI request/response DTO。
7. 新增 `OpenAiCompatibleLlmPlannerClient`。
8. 增加 LLM audit events 和 planner 异常审计闭环。
9. 增加 provider 单元测试。
10. 增加 `LlmJsonTaskPlanner` 集成测试。
11. 增加 Spring mode/context 测试。
12. `mvn test`。
13. 再做安全复审。

这个顺序比原计划更稳，因为先处理 Bean 冲突和配置 fail-closed，再接 HTTP。

## 10. 最终验收标准

实现完成后至少满足：

1. 默认仍是 rule planner。
2. fake-llm 仍可用。
3. llm 模式只能显式配置开启。
4. llm 模式缺少关键配置 fail-closed。
5. apiKey 不硬编码。
6. apiKey / Authorization 不进入日志、异常、audit。
7. prompt / user input / raw response 不完整进入日志、异常、audit。
8. Provider 只返回 JSON 字符串。
9. Provider 不构造 TaskPlan。
10. Provider 不执行工具。
11. 所有真实模型输出必须经过 strict parse 和 `LlmPlanValidator`。
12. unknown field 被拒绝。
13. unknown tool 被拒绝。
14. unsupported argument 被拒绝。
15. required argument missing 被拒绝。
16. too many steps 被拒绝。
17. HTTP 错误映射明确。
18. timeout 明确。
19. 不自动 fallback。
20. LLM planning failure 有审计终态。
21. `mvn test` 全部通过。

## 11. 结论

这份计划可以继续推进，但必须先补齐安全工程落点，尤其是：

- Bean 注入不冲突
- fail-closed 配置
- 敏感信息脱敏
- LLM planning 审计闭环
- Provider 不越权
- 不自动 fallback
- 输出长度限制

如果这些约束落实，OpenAI-compatible Provider 可以安全地作为 `LlmPlannerClient` 接入现有链路。当前最不应该做的是让真实 provider 直接创建 `TaskPlan`、自动 fallback、记录完整 prompt/raw response、或引入 function/tool calling。
