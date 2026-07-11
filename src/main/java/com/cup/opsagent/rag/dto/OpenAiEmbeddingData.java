package com.cup.opsagent.rag.dto;

import java.util.List;

public record OpenAiEmbeddingData(
        String object,
        int index,
        List<Double> embedding
) {
}
