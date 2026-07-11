# 第十五章：OpenAI-Compatible LLM Provider 安全接入

本章主题不是简单接入一个大模型 API，而是把真实 LLM 放进 SafeOps Agent 的安全边界中。

## 本章目标

```text
LLM 可以参与规划，不能直接执行工具。
LLM 可以输出 JSON，JSON 必须被验证。
LLM 可以失败，失败必须进入审计。
LLM 可能返回恶意内容，系统不能信任它。
```

## 本章工程主线

```text
User Request
  -> TaskPlanner
     -> LlmJsonTaskPlanner
        -> OpenAiCompatibleLlmPlannerClient
           -> LlmPromptFactory
           -> OpenAI-compatible HTTP API
           -> OpenAiChatCompletionResponseExtractor
        -> strict JSON parse
        -> LlmPlanValidator
        -> TaskPlan
  -> PolicyEngine
  -> Approval
  -> Tool Execution
  -> Verifier
  -> AuditTrace
  -> AgentResponse
```

## 阅读入口

完整教学稿：`full-lesson.md`

如果想按小节学习，再阅读下面的小节文档。

## 小节目录

1. `01-provider-role-and-boundary.md`：Provider 职责和安全边界
2. `02-provider-config-and-error-model.md`：Provider 配置和错误模型
3. `03-llm-prompt-factory.md`：LLM Prompt Factory
4. `04-openai-dto-and-extractor.md`：OpenAI-Compatible DTO 与 response extractor
5. `05-openai-compatible-client.md`：OpenAiCompatibleLlmPlannerClient
6. `06-llm-planning-audit-loop.md`：LLM planning 审计闭环
7. `07-source-walkthrough-and-exercise-answers.md`：源码走读与练习题参考答案

## 本章记忆点

```text
1. Provider 是模型适配器，不是计划裁判。
2. Prompt 是提示，不是安全边界。
3. LLM 输出必须经过 strict parse 和 validator。
4. 模型只知道工具能力，不知道底层命令。
5. 配置必须 fail-closed。
6. 失败必须审计，但不能泄露 prompt/apiKey/raw response。
7. LLM failed 和 LLM rejected 是两类不同问题。
8. 不自动 fallback，保证行为透明。
```
