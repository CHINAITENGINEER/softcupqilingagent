# QilingOS SafeOps Agent 中文演示脚本

本脚本用于比赛答辩、项目路演和作品集录屏。建议演示时打开三个窗口：浏览器/前端控制台、终端、GitHub Actions 页面。

## 1. 演示定位

一句话介绍：

> QilingOS SafeOps Agent 是一个面向国产操作系统运维场景的安全可控 AIOps Agent。它不是让大模型直接操作服务器，而是通过 Planner、Policy、Approval、Executor、Verifier、Audit 和 RAG 形成完整安全闭环。

核心亮点：

1. RAG 增强：使用 Milvus 检索运维知识。
2. 安全策略：高风险操作不会直接执行。
3. 审批闭环：重启服务等动作必须审批。
4. 执行验证：执行后必须做只读验证。
5. 审计追踪：每次请求都有 traceId 和审计链路。
6. 工程质量：Java CI 与真实 Milvus CI 都在 GitHub Actions 上通过。

## 2. 启动本地 Milvus

```powershell
docker compose -f docker-compose.milvus.yml up -d
docker compose -f docker-compose.milvus.yml ps
Invoke-WebRequest "http://localhost:9091/healthz" -UseBasicParsing
Test-NetConnection localhost -Port 19530
```

如果 `TcpTestSucceeded` 为 `True`，说明 Milvus gRPC 端口可用。

## 3. 启动后端

普通模式：

```powershell
mvn spring-boot:run
```

RAG + Milvus 模式：

```powershell
$env:SAFEOPS_RAG_MILVUS_URI="http://localhost:19530"
$env:SAFEOPS_RAG_MILVUS_COLLECTION="safeops_knowledge"
$env:SAFEOPS_RAG_MILVUS_INDEX_TYPE="hnsw"
$env:SAFEOPS_RAG_MILVUS_AUTO_CREATE_COLLECTION="true"
$env:SAFEOPS_RAG_MILVUS_AUTO_LOAD_COLLECTION="true"
mvn spring-boot:run "-Dspring-boot.run.profiles=rag-milvus"
```

健康检查：

```powershell
Invoke-RestMethod "http://localhost:8088/actuator/health"
```

## 4. 导入演示知识

将 `demo/knowledge/` 中的知识片段导入 RAG。以下是示例请求：

```powershell
$body = @{
  documents = @(
    @{
      knowledgeId = "qiling-nginx-ops"
      sourceType = "demo"
      title = "QilingOS Nginx 运维处置手册"
      content = Get-Content "demo/knowledge/qiling-nginx-ops.md" -Raw
      metadata = @{ domain = "nginx"; os = "qiling"; risk = "high" }
    },
    @{
      knowledgeId = "qiling-system-load"
      sourceType = "demo"
      title = "QilingOS 系统负载巡检知识"
      content = Get-Content "demo/knowledge/qiling-system-load.md" -Raw
      metadata = @{ domain = "system"; os = "qiling"; risk = "low" }
    },
    @{
      knowledgeId = "qiling-milvus-troubleshooting"
      sourceType = "demo"
      title = "Milvus RAG 故障排查手册"
      content = Get-Content "demo/knowledge/qiling-milvus-troubleshooting.md" -Raw
      metadata = @{ domain = "rag"; component = "milvus"; risk = "medium" }
    }
  )
} | ConvertTo-Json -Depth 8

Invoke-RestMethod -Method Post "http://localhost:8088/api/rag/knowledge/ingest" `
  -ContentType "application/json" `
  -Body $body
```

## 5. 展示 RAG 搜索

```powershell
Invoke-RestMethod "http://localhost:8088/api/rag/knowledge/search?q=nginx%20restart%20approval&maxResults=5"
```

讲解点：

> RAG 返回的是上下文证据，不是执行授权。即使知识库建议重启 nginx，最终也必须经过 Policy 和 Approval。

## 6. 展示只读 Agent 请求

```powershell
$body = @{
  message = "health check"
  sessionId = "demo-session-1"
  userId = "operator-1"
} | ConvertTo-Json

Invoke-RestMethod -Method Post "http://localhost:8088/api/agent/chat" `
  -ContentType "application/json" `
  -Body $body
```

讲解点：

- 只读巡检可以直接执行。
- 返回 traceId。
- steps 中包含 toolName、decision、executed、verificationResult。

## 7. 展示高风险审批

```powershell
$body = @{
  message = "restart nginx"
  sessionId = "demo-session-2"
  userId = "operator-1"
} | ConvertTo-Json

$response = Invoke-RestMethod -Method Post "http://localhost:8088/api/agent/chat" `
  -ContentType "application/json" `
  -Body $body

$response
```

预期：

```text
status = WAITING_APPROVAL
answer 中包含 approvalId
steps[0].executed = false
```

讲解点：

> 高风险命令不会因为用户一句话就执行，Agent 只会生成审批请求，并把 actionHash 写入审批记录。

## 8. 审批和执行

从上一步响应中复制 `approvalId`。

```powershell
$approvalBody = @{ approvalId = "替换为实际 approvalId" } | ConvertTo-Json

$approval = Invoke-RestMethod -Method Post "http://localhost:8088/api/approvals/approve" `
  -ContentType "application/json" `
  -Body $approvalBody

$approval
```

审批通过后得到 `leaseId`。执行：

```powershell
$executeBody = @{
  leaseId = "替换为实际 leaseId"
  toolName = "restart_service"
  arguments = @{ serviceName = "nginx" }
} | ConvertTo-Json -Depth 5

Invoke-RestMethod -Method Post "http://localhost:8088/api/approvals/execute" `
  -ContentType "application/json" `
  -Body $executeBody
```

讲解点：

- 审批只生成有限期 lease。
- 执行时必须匹配 toolName 和 arguments。
- 执行后仍需 verifier 校验。

## 9. 审计查询

```powershell
Invoke-RestMethod "http://localhost:8088/api/audit/traces"
```

检查单个 trace 的完整性：

```powershell
Invoke-RestMethod "http://localhost:8088/api/audit/traces/{traceId}/integrity"
```

讲解点：

> 审计不是普通日志，而是围绕 traceId 组织的操作链路，并保留事件、状态和完整性校验。

## 10. GitHub Actions 展示

打开：

- Java CI：`https://github.com/CHINAITENGINEER/softcupqilingagent/actions/workflows/java-ci.yml`
- Milvus RAG integration：`https://github.com/CHINAITENGINEER/softcupqilingagent/actions/workflows/milvus-integration.yml`

讲解点：

- 默认 Java 测试在 push/PR 自动运行。
- Milvus 集成测试会启动真实 etcd、MinIO、Milvus。
- HNSW 和 IVF_FLAT 都有真实 CI 验证。

## 11. 结束语

> 这个项目的核心不是“让 AI 直接执行命令”，而是把 AI 放进一个安全、可审批、可验证、可审计的运维闭环里。RAG 提供知识，LLM/Planner 提供计划，但最终控制权仍然在策略、审批和审计系统中。
