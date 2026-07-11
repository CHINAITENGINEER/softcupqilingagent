# 16.5 RAG 审计与可观测性

这一节讲 RAG 为什么必须进入审计。

RAG 接入后，LLM 的输入不再只来自用户，还来自知识库。

所以复盘时必须能回答：

```text
这次规划有没有检索知识库？
检索到了几条？
命中了哪些知识？
检索失败了吗？
有没有因为 RAG 失败导致 planning failed？
```

---

# 1. 为什么 RAG 必须审计

如果没有 RAG 审计，出问题时会很难排查。

例如模型生成了一个奇怪计划，你需要知道：

```text
是用户输入导致的？
是 prompt factory 导致的？
是检索到的 runbook 影响的？
是知识库污染导致的？
还是根本没命中知识？
```

所以 RAG 不是后台黑盒。

它必须留下证据。

---

# 2. 当前 RAG 审计事件

当前事件：

```text
RAG_RETRIEVAL_STARTED
RAG_RETRIEVAL_COMPLETED
RAG_RETRIEVAL_FAILED
```

它们都挂在同一条 `traceId` 上。

这很重要：

```text
RAG 不是单独日志，而是一次 Agent 请求的审计链节点。
```

---

# 3. RAG_RETRIEVAL_STARTED

表示开始检索。

payload 可以记录：

```text
queryHash
maxResults
filters
```

不要记录：

```text
完整 userInput
完整 query 原文
敏感 filter 值
```

为什么用 queryHash？

```text
既能关联排查，又不直接泄露用户输入。
```

---

# 4. RAG_RETRIEVAL_COMPLETED

表示检索完成。

payload 可以记录：

```text
queryHash
resultCount
topKnowledgeIds
topScores
durationMs
contextLength
```

未命中也要记录：

```text
resultCount=0
```

因为未命中本身就是重要信号。

例如：

```text
用户问 nginx 502，但 resultCount=0
```

说明知识库覆盖可能不足。

---

# 5. RAG_RETRIEVAL_FAILED

表示检索失败。

payload 可以记录：

```text
queryHash
errorType
errorMessage
retrieverName
durationMs
```

不要记录：

```text
完整异常堆栈
完整文档内容
embedding 向量
数据库连接串
API key
```

当前策略是：

```text
fail-closed
```

也就是：

```text
RAG 检索失败
  -> RAG_RETRIEVAL_FAILED
  -> 不调用 LLM provider
  -> PLANNING_FAILED
  -> FINAL_RESPONSE
```

---

# 6. 为什么不记录完整文档内容

审计系统不是知识库备份系统。

如果把完整文档写进审计，会有问题：

```text
泄露敏感 runbook
泄露日志内容
审计数据膨胀
难以脱敏
可能把 prompt injection 文本永久保存
```

所以只记录：

```text
knowledgeId
score
count
hash
duration
```

需要查原文时，通过 `knowledgeId` 回知识库查。

---

# 7. RAG 可观测性指标

除了审计事件，后续还可以做指标：

```text
retrieval_count
retrieval_failed_count
retrieval_empty_count
retrieval_duration_ms
retrieved_result_count
context_length
```

这些指标用于观察：

```text
知识库是否覆盖足够
检索服务是否稳定
RAG 是否经常失败
context 是否经常超长
```

---

# 8. 成功路径审计顺序

RAG 成功时：

```text
RECEIVE_REQUEST
LLM_PLANNING_STARTED
RAG_RETRIEVAL_STARTED
RAG_RETRIEVAL_COMPLETED
LLM_PLANNING_COMPLETED
PLAN_GENERATED
...
FINAL_RESPONSE
```

注意：

```text
RAG 是 LLM planning 的一部分，但有自己的审计事件。
```

---

# 9. 失败路径审计顺序

RAG 失败时：

```text
RECEIVE_REQUEST
LLM_PLANNING_STARTED
RAG_RETRIEVAL_STARTED
RAG_RETRIEVAL_FAILED
LLM_PLANNING_FAILED
PLANNING_FAILED
FINAL_RESPONSE
```

这条链说明：

```text
不是 LLM 输出坏了，而是 RAG 检索阶段失败导致规划失败。
```

---

# 10. 本节总结

RAG 审计要记住：

```text
1. 命中、未命中、失败都要审计。
2. 记录 ID、count、score、duration，不记录完整原文。
3. RAG 失败 fail-closed，不能偷偷 fallback。
4. RAG 事件必须挂在同一条 traceId 上。
```
