# 16.3 检索流程与排序

这一节讲 RAG 的第二个核心问题：

```text
用户输入如何变成检索结果？检索结果如何排序？
```

在 SafeOps Agent 里，检索流程必须保持安全边界：

```text
检索只是找资料，不是生成执行动作。
```

---

# 1. 当前检索主线

当前上层接口是：

```text
KnowledgeRetriever
```

输入：

```text
KnowledgeQuery
```

输出：

```text
List<RetrievedKnowledge>
```

主线：

```text
UserInput
  -> KnowledgeQuery
  -> KnowledgeRetriever.retrieve
  -> List<RetrievedKnowledge>
  -> RagContextFactory
  -> LlmPromptFactory
```

---

# 2. InMemoryKnowledgeRetriever

当前内存实现适合测试和演示。

它不依赖外部服务。

作用：

```text
先把 RAG 安全边界和调用链跑通，避免一开始就被 Milvus、embedding、索引参数拖复杂。
```

内存检索通常做：

```text
遍历文档
根据 query text 匹配 title/content/metadata
计算简单 score
排序
截断 snippet
返回 topK
```

---

# 3. maxResults 为什么重要

RAG 不是检索越多越好。

如果返回太多内容：

```text
prompt 变长
成本上升
模型注意力分散
更容易引入 prompt injection
更容易泄露知识库内容
```

当前默认：

```properties
ops-agent.rag.max-results=5
```

含义：

```text
最多返回 5 条知识结果
```

---

# 4. snippetMaxLength 和 contextMaxLength

两个限制要一起看：

```properties
ops-agent.rag.snippet-max-length=500
ops-agent.rag.context-max-length=2000
```

区别：

```text
snippet-max-length：单条结果最大长度
context-max-length：整个 RAG context 最大长度
```

为什么都需要？

因为只限制单条不够。

例如：

```text
5 条结果，每条 500 字，总共 2500 字
```

仍然可能超过 prompt 预算。

所以需要双层限制：

```text
单条截断
整体截断
```

---

# 5. 排序原则

检索结果排序通常看：

```text
score 高低
source 可信度
更新时间
业务过滤条件
```

但当前最关键的是：

```text
score 只是相关性，不是安全性。
```

高 score 不代表：

```text
可以执行
可以跳过审批
可以绕过 policy
```

高 score 只代表：

```text
更适合作为上下文
```

---

# 6. 向量检索流程

现在代码已经有向量抽象：

```text
EmbeddingClient
VectorStoreClient
VectorKnowledgeRetriever
```

向量检索链路：

```text
KnowledgeQuery.text
  -> EmbeddingClient.embed
  -> VectorStoreClient.search
  -> VectorSearchResult
  -> RetrievedKnowledge
```

当前测试实现：

```text
DeterministicEmbeddingClient
InMemoryVectorStoreClient
```

它们用于稳定测试，不是生产语义 embedding。

未来 Milvus 应该实现：

```text
VectorStoreClient
```

而不是侵入业务层。

---

# 7. 过滤条件 filters

`KnowledgeQuery.filters` 用于缩小检索范围。

例如：

```text
service=nginx
sourceType=runbook
environment=prod
team=sre
```

过滤条件的意义是：

```text
先缩小候选集，再做相关性排序。
```

但 filters 也不能当权限系统使用。

错误理解：

```text
filter 了 prod，所以可以执行 prod 操作。
```

正确理解：

```text
filter 只控制检索范围，执行权限仍然由 Policy/Approval 控制。
```

---

# 8. 检索失败怎么办

当前策略是：

```text
fail-closed
```

如果 RAG 已启用，检索失败时：

```text
RAG_RETRIEVAL_FAILED
  -> 不调用 LLM provider
  -> PLANNING_FAILED
  -> FINAL_RESPONSE
```

为什么不 fallback？

因为启用 RAG 表示本次规划依赖检索上下文。

如果检索失败还偷偷无上下文规划，会让行为不透明。

---

# 9. 本节总结

检索流程记住四句话：

```text
1. KnowledgeRetriever 只返回 RetrievedKnowledge。
2. score 是相关性，不是授权。
3. maxResults/snippet/context 都要限制。
4. RAG 启用后检索失败要 fail-closed。
```
