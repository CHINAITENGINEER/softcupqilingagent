package com.cup.opsagent.planner.llm.provider.dto;

public record OpenAiResponseFormat(
        String type
) {
    public static OpenAiResponseFormat jsonObject() {
        return new OpenAiResponseFormat("json_object");
    }
}
