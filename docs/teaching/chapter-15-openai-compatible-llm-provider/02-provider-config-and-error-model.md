# 15.2 Provider 配置和错误模型

## 这一节解决什么问题

真实 LLM 调用依赖配置，但配置不能不完整、不安全、不透明。

本节核心是：

```text
配置 fail-closed
错误结构化
异常脱敏
```

## 真实运行需要的配置

只有显式启用 `llm` 模式时才需要真实 API 配置：

```properties
ops-agent.planner.mode=llm
ops-agent.llm.provider=openai-compatible
ops-agent.llm.base-url=https://api.example.com/v1
ops-agent.llm.api-key=sk-xxx
ops-agent.llm.model=xxx-model
```

默认 planner 仍然是：

```text
rule
```

所以默认不会访问外部 LLM。

## fail-closed

fail-closed 的意思是：

```text
不满足安全条件就失败，不猜、不继续、不自动降级。
```

典型规则：

```text
baseUrl 为空 -> 失败
apiKey 为空 -> 失败
model 为空 -> 失败
connectTimeoutMs 非正数 -> 失败
readTimeoutMs 非正数 -> 失败
maxOutputTokens 非正数 -> 失败
temperature 不在 0 到 2 -> 失败
baseUrl scheme 不安全 -> 失败
baseUrl 带 userinfo/query -> 失败
```

## Provider 错误码

Provider 错误不能只靠异常文本表达，而要结构化：

```text
LLM_PROVIDER_DISABLED
LLM_PROVIDER_MISCONFIGURED
LLM_PROVIDER_TIMEOUT
LLM_PROVIDER_UNAUTHORIZED
LLM_PROVIDER_RATE_LIMITED
LLM_PROVIDER_BAD_REQUEST
LLM_PROVIDER_SERVER_ERROR
LLM_PROVIDER_BAD_RESPONSE
LLM_PROVIDER_EMPTY_RESPONSE
LLM_PROVIDER_UNSUPPORTED_RESPONSE_FORMAT
LLM_PROVIDER_RESPONSE_TOO_LARGE
```

错误码的价值：

```text
1. 审计可分类。
2. 测试可断言。
3. 前端可展示稳定错误。
4. 排查不依赖解析字符串。
```

## 异常脱敏

Provider 失败时不能泄露：

```text
apiKey
Authorization header
完整 prompt
完整 user input
完整 raw response
provider error body 中的敏感字段
```

异常 message 应该保留安全摘要，例如：

```text
LLM provider HTTP error status=401
```

而不是：

```text
Authorization: Bearer sk-xxx
prompt=...
rawResponse=...
```

## 一句话记忆

```text
配置不可信时失败，错误要可分类，异常不能泄密。
```

## 练习

1. 为什么 `apiKey` 缺失时不能 fallback 到 rule planner？
2. 为什么 HTTP 401 不能直接把 provider body 放进异常 message？
3. `LLM_PROVIDER_MISCONFIGURED` 和 `LLM_PROVIDER_UNAUTHORIZED` 的区别是什么？
