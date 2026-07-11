package com.cup.opsagent.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
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
                .andExpect(jsonPath("$.status").value("VERIFICATION_FAILED"))
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
