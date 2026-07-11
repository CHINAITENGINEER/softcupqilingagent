# 15.4 OpenAI-Compatible Request / Response DTO 与 Extractor

## 这一节解决什么问题

本节把 OpenAI-compatible chat/completions 的请求和响应变成稳定 DTO，并用 extractor 提取模型真正返回的 JSON 字符串。

## Request DTO

请求体大致长这样：

```json
{
  "model": "test-model",
  "messages": [
    {
      "role": "system",
      "content": "..."
    },
    {
      "role": "user",
      "content": "..."
    }
  ],
  "temperature": 0.0,
  "max_tokens": 1000,
  "response_format": {
    "type": "json_object"
  }
}
```

注意：

```text
apiKey 不在 request DTO 里。
```

apiKey 只能进入：

```text
Authorization header
```

不能进入：

```text
request body
DTO 字段
日志 payload
异常 message
审计 payload
```

## response_format=json_object

`response_format={"type":"json_object"}` 可以降低模型返回非 JSON 的概率。

但它不是安全边界。

即使启用了它，模型仍可能返回：

```text
unknown field
unknown tool
unsupported argument
required argument missing
too many steps
超长字段
危险内容
```

所以仍然必须经过：

```text
strict ObjectMapper
LlmPlanValidator
```

## Response DTO

Provider 响应大致长这样：

```json
{
  "choices": [
    {
      "message": {
        "role": "assistant",
        "content": "{\"intentType\":\"...\",\"steps\":[]}"
      }
    }
  ]
}
```

我们真正需要的是：

```text
choices[0].message.content
```

也就是模型输出的 JSON 字符串。

## Extractor 的职责

Extractor 负责：

```text
response 为空 -> LLM_PROVIDER_EMPTY_RESPONSE
choices 为空 -> LLM_PROVIDER_EMPTY_RESPONSE
message 为空 -> LLM_PROVIDER_EMPTY_RESPONSE
content 为空 -> LLM_PROVIDER_EMPTY_RESPONSE
content 超长 -> LLM_PROVIDER_RESPONSE_TOO_LARGE
否则返回 content
```

Extractor 不负责：

```text
parse TaskPlan
validate tool
validate arguments
执行工具
```

## 一句话记忆

```text
Extractor 只做 OpenAI response -> String content。
```

## 练习

1. 为什么 apiKey 不能放进 request DTO？
2. 为什么 response_format=json_object 不是安全保证？
3. extractor 为什么不直接 parse `TaskPlan`？
