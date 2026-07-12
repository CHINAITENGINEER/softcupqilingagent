# QilingOS Nginx 运维处置手册

## 适用场景

- Nginx 服务无法启动或频繁重启。
- 业务访问出现 502、504、连接拒绝。
- 配置变更后需要安全重载。

## 安全检查顺序

1. 先执行只读检查，确认服务状态、端口监听、错误日志。
2. 不要直接重启生产服务，必须先进入审批流程。
3. 审批通过后执行重启或 reload。
4. 执行后验证服务状态、端口、最近错误日志。
5. 将 traceId、approvalId、执行结果和验证结果写入审计。

## 常见诊断命令

```bash
systemctl status nginx --no-pager
ss -lntp | grep ':80\|:443'
journalctl -u nginx -n 80 --no-pager
nginx -t
```

## 处置建议

- 如果 `nginx -t` 失败，不允许重启，应先修复配置。
- 如果端口被占用，应定位占用进程并升级人工处理。
- 如果证书过期，应先更新证书，再执行 reload。
- 如果 upstream 不可达，应检查后端服务健康状态。

## 答辩亮点

本知识条目用于展示 RAG 检索不是直接执行命令，而是为 Agent 提供上下文。最终是否执行仍由 Policy、Approval、Verifier 和 Audit 共同控制。
