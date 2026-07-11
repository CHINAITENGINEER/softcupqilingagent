package com.cup.opsagent.rag;

import java.util.List;

public record OpenAiEmbeddingData(
        List<Float> embedding
) {
}
