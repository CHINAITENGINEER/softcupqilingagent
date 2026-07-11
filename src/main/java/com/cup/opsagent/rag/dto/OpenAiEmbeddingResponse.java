package com.cup.opsagent.rag.dto;

import java.util.List;

public record OpenAiEmbeddingResponse(
        String object,
        List<OpenAiEmbeddingData> data,
        String model
) {
}
