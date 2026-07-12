# Milvus RAG 故障排查手册

## 适用场景

- RAG 检索为空。
- Milvus 连接失败。
- 集合未创建或未加载。
- HNSW / IVF_FLAT 参数配置不合理。

## 检查顺序

1. 确认 Milvus 健康接口：`http://localhost:9091/healthz`。
2. 确认 gRPC 端口：`19530` 可访问。
3. 确认集合名称、维度和 metric type 与 embedding 一致。
4. 确认 `SAFEOPS_RAG_MILVUS_AUTO_CREATE_COLLECTION` 是否开启。
5. 确认 collection 已加载。
6. 检查索引类型：`hnsw`、`ivf-flat` 或 `autoindex`。

## HNSW 建议

- `M=16` 适合作为默认值。
- `efConstruction=200` 兼顾构建质量和成本。
- `ef=64` 适合演示和中小规模检索。

## IVF_FLAT 建议

- `nlist=128` 适合演示集合。
- `nprobe=16` 可提升召回。
- 数据量较小时 HNSW 通常更易展示稳定结果。

## 安全注意

- 不要把 Milvus token 写入代码或 README。
- `.env.example` 只能包含占位值。
- CI 使用临时无鉴权 Milvus 容器。
