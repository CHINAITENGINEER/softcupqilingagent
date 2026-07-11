package com.cup.opsagent.rag;

import java.util.List;

public record OpenAiEmbeddingResponse(
        List<OpenAiEmbeddingData> data
) {
}
