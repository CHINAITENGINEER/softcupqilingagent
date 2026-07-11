package com.cup.opsagent.api;

import com.cup.opsagent.executor.CommandRunner;
import com.cup.opsagent.tool.core.ToolExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AgentControllerWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CommandRunner commandRunner;

    private String originalOsName;

    @BeforeEach
    void stubCommandExecution() {
        originalOsName = System.getProperty("os.name");
        System.setProperty("os.name", "Linux");
        when(commandRunner.run(anyString(), any(), anyMap(), any()))
                .thenAnswer(invocation -> new ToolExecutionResult(
                        invocation.getArgument(0), true, "load average: 0.10, 0.20, 0.30", "",
                        0, 1, Instant.parse("2026-07-11T00:00:00Z")));
    }

    @AfterEach
    void restoreOperatingSystemName() {
        if (originalOsName == null) {
            System.clearProperty("os.name");
        } else {
            System.setProperty("os.name", originalOsName);
        }
    }

    @Test
    void shouldHandleReadOnlyAgentRequest() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "message", "health check",
                                "sessionId", "session-web-1",
                                "userId", "operator-1"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.steps[0].toolName").value("get_system_load"));
    }

    @Test
    void shouldReturnWaitingApprovalForServiceRestart() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "message", "restart nginx",
                                "sessionId", "session-web-2",
                                "userId", "operator-2"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId", not(blankOrNullString())))
                .andExpect(jsonPath("$.status").value("WAITING_APPROVAL"))
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("approvalId=")))
                .andExpect(jsonPath("$.steps[0].toolName").value("restart_service"))
                .andExpect(jsonPath("$.steps[0].executed").value(false));
    }

    @Test
    void shouldRejectBlankMessage() throws Exception {
        mockMvc.perform(post("/api/agent/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "message", " ",
                                "sessionId", "session-web-3",
                                "userId", "operator-3"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.message").value("message must not be blank"));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
