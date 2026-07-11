# 16.2 知识文档模型设计

这一节解决一个问题：

```text
RAG 检索出来的东西，到底应该长什么样？
```

在 SafeOps Agent 里，知识文档不能随便用一个字符串表示。因为我们需要知道：

```text
它来自哪里
它是什么类型
它什么时候更新
它得分多少
它能不能进入 prompt
它是否需要截断
它是否可能包含敏感内容
```

---

# 1. 原始知识和检索结果要分开

当前代码里有两个核心模型：

```text
KnowledgeDocument
RetrievedKnowledge
```

它们不是一回事。

## KnowledgeDocument

表示知识库里的原始知识。

字段：

```text
knowledgeId
sourceType
title
content
lastUpdatedAt
metadata
```

它可以理解为：

```text
被存储的知识原文或片段
```

例如：

```text
knowledgeId=runbook-nginx-502
sourceType=runbook
title=Nginx 502 troubleshooting
content=完整 runbook 内容
lastUpdatedAt=2026-07-01T00:00:00Z
metadata={service=nginx, env=prod}
```

## RetrievedKnowledge

表示检索后允许交给 RAG 主链路的安全视图。

字段：

```text
knowledgeId
sourceType
title
snippet
score
lastUpdatedAt
metadata
```

注意它没有完整 `content`，只有 `snippet`。

这很关键。

```text
RAG prompt 不应该默认拿完整文档，只应该拿截断后的 snippet。
```

---

# 2. 为什么要 snippet，不直接用 content

完整文档可能很长，也可能包含：

```text
过期步骤
危险命令
敏感日志
内部 token
prompt injection 文本
大段无关内容
```

所以进入 prompt 前必须降级成：

```text
snippet
```

snippet 的要求：

```text
短
可溯源
可截断
不保证完全可信
```

当前配置里有：

```properties
ops-agent.rag.snippet-max-length=500
ops-agent.rag.context-max-length=2000
```

意思是：

```text
单条 snippet 有长度限制
整个 RAG context 也有总长度限制
```

---

# 3. KnowledgeQuery：检索请求

`KnowledgeQuery` 表示一次检索请求。

字段：

```text
text
maxResults
filters
```

例如：

```text
text=nginx 502 upstream timeout
maxResults=5
filters={service=nginx, sourceType=runbook}
```

当前默认：

```text
maxResults=5
filters={}
```

为什么要有 `filters`？

因为运维场景经常需要限制范围：

```text
只查 prod 文档
只查 nginx 文档
只查 runbook
只查最近版本
只查某个 team 维护的知识
```

---

# 4. metadata 的作用

`metadata` 是扩展字段。

它不应该承载核心安全语义，但可以帮助检索和过滤。

常见 metadata：

```text
service=nginx
environment=prod
team=sre
severity=medium
source=runbook
version=v3
```

注意：

```text
metadata 可以进入审计摘要，但不要把敏感值放进去。
```

例如不要放：

```text
password
apiKey
token
完整连接串
```

---

# 5. score 是参考，不是裁判

`RetrievedKnowledge.score` 表示检索相关性。

但 score 不能直接决定执行。

错误理解：

```text
score 高，所以可以执行文档里的命令。
```

正确理解：

```text
score 高，只说明它更适合作为上下文给 LLM 参考。
```

最终工具调用仍然要经过：

```text
LlmPlanValidator
PolicyEngine
Approval
Verifier
```

---

# 6. Vector Store 模型

后续为了接向量库，当前代码又新增了一组底层模型：

```text
EmbeddingVector
VectorStoreRecord
VectorStoreQuery
VectorSearchResult
VectorStoreClient
```

它们属于向量检索底层边界。

上层 RAG 主链路仍然只认：

```text
KnowledgeRetriever
RetrievedKnowledge
```

关系是：

```text
VectorStoreRecord
  -> VectorSearchResult
  -> RetrievedKnowledge
```

这样未来接 Milvus 时，不需要让 `RagAugmentor` 或 `LlmJsonTaskPlanner` 直接依赖 Milvus SDK。

---

# 7. 本节总结

RAG 模型设计的核心是分层：

```text
KnowledgeDocument：原始知识
RetrievedKnowledge：安全检索结果视图
KnowledgeQuery：检索请求
VectorStoreRecord：向量库底层记录
VectorSearchResult：向量搜索结果
```

最终记忆：

```text
进入 prompt 的不是完整文档，而是带来源、带分数、被截断的 RetrievedKnowledge snippet。
```
