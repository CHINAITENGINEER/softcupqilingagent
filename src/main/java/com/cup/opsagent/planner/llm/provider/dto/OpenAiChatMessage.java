package com.cup.opsagent.planner.llm.provider.dto;

public record OpenAiChatMessage(
        String role,
        String content
) {
}
