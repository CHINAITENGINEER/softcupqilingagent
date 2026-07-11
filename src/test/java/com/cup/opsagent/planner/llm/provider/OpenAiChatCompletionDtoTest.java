package com.cup.opsagent.planner.llm.provider;

import com.cup.opsagent.planner.llm.LlmJsonTaskPlanner;
import com.cup.opsagent.planner.llm.provider.dto.OpenAiChatCompletionRequest;
import com.cup.opsagent.planner.llm.provider.dto.OpenAiChatCompletionResponse;
import com.cup.opsagent.planner.llm.provider.dto.OpenAiChatMessage;
import com.cup.opsagent.planner.llm.provider.dto.OpenAiChoice;
import com.cup.opsagent.planner.llm.provider.dto.OpenAiResponseFormat;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiChatCompletionDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiChatCompletionResponseExtractor extractor = new OpenAiChatCompletionResponseExtractor();

    @Test
    void shouldSerializeRequestWithOpenAiCompatibleFieldNames() throws Exception {
        OpenAiChatCompletionRequest request = new OpenAiChatCompletionRequest(
                "test-model",
                List.of(
                        new OpenAiChatMessage("system", "Return JSON only"),
                        new OpenAiChatMessage("user", "User request")
                ),
                0.0,
                1000,
                OpenAiResponseFormat.jsonObject()
        );

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(request));

        assertThat(json.get("model").asText()).isEqualTo("test-model");
        assertThat(json.get("messages")).hasSize(2);
        assertThat(json.get("messages").get(0).get("role").asText()).isEqualTo("system");
        assertThat(json.get("temperature").asDouble()).isZero();
        assertThat(json.get("max_tokens").asInt()).isEqualTo(1000);
        assertThat(json.get("response_format").get("type").asText()).isEqualTo("json_object");
        assertThat(json.has("maxTokens")).isFalse();
        assertThat(json.has("responseFormat")).isFalse();
    }

    @Test
    void shouldDeserializeAndExtractFirstChoiceMessageContent() throws Exception {
        String content = "{\"intentType\":\"DIAGNOSTIC\",\"summary\":\"ok\",\"steps\":[]}";
        String rawJson = """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": %s
                      }
                    }
                  ]
                }
                """.formatted(objectMapper.writeValueAsString(content));

        OpenAiChatCompletionResponse response = objectMapper.readValue(rawJson, OpenAiChatCompletionResponse.class);

        assertThat(extractor.extractContent(response)).contains("DIAGNOSTIC");
    }

    @Test
    void shouldRejectNullOrEmptyChoices() {
        assertThatThrownBy(() -> extractor.extractContent(null))
                .isInstanceOfSatisfying(LlmProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(LlmProviderErrorCode.LLM_PROVIDER_EMPTY_RESPONSE)
                );

        assertThatThrownBy(() -> extractor.extractContent(new OpenAiChatCompletionResponse(List.of())))
                .isInstanceOfSatisfying(LlmProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(LlmProviderErrorCode.LLM_PROVIDER_EMPTY_RESPONSE)
                );
    }

    @Test
    void shouldRejectNullOrBlankMessageContent() throws Exception {
        OpenAiChatCompletionResponse nullContent = objectMapper.readValue("""
                {"choices":[{"message":{"role":"assistant","content":null}}]}
                """, OpenAiChatCompletionResponse.class);
        OpenAiChatCompletionResponse blankContent = objectMapper.readValue("""
                {"choices":[{"message":{"role":"assistant","content":"   "}}]}
                """, OpenAiChatCompletionResponse.class);

        assertThatThrownBy(() -> extractor.extractContent(nullContent))
                .isInstanceOfSatisfying(LlmProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(LlmProviderErrorCode.LLM_PROVIDER_EMPTY_RESPONSE)
                );
        assertThatThrownBy(() -> extractor.extractContent(blankContent))
                .isInstanceOfSatisfying(LlmProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(LlmProviderErrorCode.LLM_PROVIDER_EMPTY_RESPONSE)
                );
    }

    @Test
    void shouldRejectOversizedMessageContent() {
        OpenAiChatCompletionResponse response = new OpenAiChatCompletionResponse(List.of(
                new OpenAiChoice(
                        new OpenAiChatMessage("assistant", "x".repeat(LlmJsonTaskPlanner.MAX_RAW_JSON_LENGTH + 1))
                )
        ));

        assertThatThrownBy(() -> extractor.extractContent(response))
                .isInstanceOfSatisfying(LlmProviderException.class, exception ->
                        assertThat(exception.code()).isEqualTo(LlmProviderErrorCode.LLM_PROVIDER_RESPONSE_TOO_LARGE)
                );
    }
}
