# 15.5 OpenAiCompatibleLlmPlannerClient

## 这一节解决什么问题

`OpenAiCompatibleLlmPlannerClient` 把配置、prompt factory、HTTP 调用、错误映射和 response extractor 串起来。

它实现的是：

```text
LlmPlannerClient.createPlanJson(userInput)
```

返回值是：

```text
String
```

也就是模型返回的 JSON 字符串。

## 调用流程

```text
createPlanJson(userInput)
  -> properties.validateForLlmMode()
  -> promptFactory.createMessages(userInput)
  -> build OpenAiChatCompletionRequest
  -> RestClient POST /chat/completions
  -> map HTTP exceptions
  -> extractor.extractContent(response)
  -> return String
```

## 为什么返回 String

因为 provider 只负责模型适配：

```text
自然语言 -> JSON 字符串
```

它不能直接返回：

```text
TaskPlan
PlanStep
ToolCall
```

否则会绕过 `LlmJsonTaskPlanner` 和 `LlmPlanValidator`。

## HTTP 错误映射

常见映射：

```text
401/403 -> LLM_PROVIDER_UNAUTHORIZED
408     -> LLM_PROVIDER_TIMEOUT
429     -> LLM_PROVIDER_RATE_LIMITED
4xx     -> LLM_PROVIDER_BAD_REQUEST
5xx     -> LLM_PROVIDER_SERVER_ERROR
其他    -> LLM_PROVIDER_BAD_RESPONSE
```

网络失败或超时：

```text
ResourceAccessException -> LLM_PROVIDER_TIMEOUT
```

其他 RestClient 异常：

```text
RestClientException -> LLM_PROVIDER_SERVER_ERROR
```

## 错误体为什么不能原样抛出

Provider 的错误体可能包含：

```text
apiKey
Authorization header
prompt 片段
request echo
provider trace
raw response
```

所以异常 message 只保留安全摘要，例如：

```text
LLM provider HTTP error status=401
```

## 一句话记忆

```text
Client 负责调模型，但不解释模型计划。
```

## 练习

1. `OpenAiCompatibleLlmPlannerClient` 为什么不 import `TaskPlan`？
2. 429 应该映射成什么错误码？为什么？
3. 为什么 HTTP error body 不能进入异常 message？
