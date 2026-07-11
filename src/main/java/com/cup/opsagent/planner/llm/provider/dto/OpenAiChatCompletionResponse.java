package com.cup.opsagent.planner.llm.provider.dto;

import java.util.List;

public record OpenAiChatCompletionResponse(
        List<OpenAiChoice> choices
) {
}
