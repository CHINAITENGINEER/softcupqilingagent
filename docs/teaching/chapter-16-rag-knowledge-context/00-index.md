# 第十六章：RAG Knowledge Context 安全设计

本章目标不是马上接入向量数据库，也不是直接把检索结果塞进 prompt。

本章目标是先定义 RAG 在 SafeOps Agent 中的安全位置：

```text
RAG 只能提供知识上下文，不能授予执行权限。
```

## 本章核心问题

RAG 接入后，系统会多一个信息来源：

```text
运维知识库
历史故障案例
Runbook
SOP
告警解释
系统文档
```

但这些内容本身也不能被完全信任。

所以 RAG 必须遵守：

```text
检索结果是上下文，不是命令。
检索结果是参考，不是授权。
检索结果可以影响 planner 的理解，但不能绕过 validator/policy/approval。
```

## 目标架构

```text
User Request
  -> RAG Query Builder
  -> Retriever
  -> Knowledge Context
  -> Prompt Factory
  -> LLM Planner
  -> strict JSON parse
  -> LlmPlanValidator
  -> PolicyEngine
  -> Approval
  -> Tool Execution
  -> Verifier
  -> AuditTrace
```

## 阅读入口

1. `full-lesson.md`：完整复习课，适合先建立整体理解。
2. 按下面小节逐篇复习，适合第二遍查漏补缺。

## 本章小节

1. `01-rag-role-and-boundary.md`：RAG 的职责和安全边界
2. `02-knowledge-document-model.md`：知识文档模型设计
3. `03-retrieval-flow-and-ranking.md`：检索流程与排序
4. `04-rag-prompt-injection-risk.md`：RAG Prompt Injection 风险
5. `05-rag-audit-and-observability.md`：RAG 审计与可观测性
6. `06-implementation-plan.md`：工程实现分阶段计划

## 本章记忆点

```text
1. RAG 是知识增强，不是权限增强。
2. 检索结果不能直接变成 ToolCall。
3. Runbook 只能帮助 planner 理解，不允许绕过 LlmPlanValidator。
4. RAG 上下文必须脱敏、截断、标注来源。
5. RAG 命中、未命中、失败都要审计。
6. 知识库内容本身也可能被 prompt injection 污染。
7. RAG 默认关闭，只在 planner.mode=llm 且 ops-agent.rag.enabled=true 时接入主链路。
8. RAG 检索失败采用 fail-closed：不调用 LLM provider，不生成 plan。
```
