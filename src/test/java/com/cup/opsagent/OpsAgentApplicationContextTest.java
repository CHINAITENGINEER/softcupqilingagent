package com.cup.opsagent;

import com.cup.opsagent.executor.CommandRunner;
import com.cup.opsagent.executor.CommandTemplateRegistry;
import com.cup.opsagent.tool.builtin.OpenPortsTool;
import com.cup.opsagent.tool.builtin.PortUsageTool;
import com.cup.opsagent.tool.builtin.RestartServiceTool;
import com.cup.opsagent.tool.builtin.ServiceStatusTool;
import com.cup.opsagent.tool.builtin.SystemLoadTool;
import com.cup.opsagent.tool.builtin.TopProcessesTool;
import com.cup.opsagent.verifier.ServiceRestartVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OpsAgentApplicationContextTest {

    @Autowired
    private CommandTemplateRegistry commandTemplateRegistry;

    @Autowired
    private CommandRunner commandRunner;

    @Autowired
    private SystemLoadTool systemLoadTool;

    @Autowired
    private TopProcessesTool topProcessesTool;

    @Autowired
    private OpenPortsTool openPortsTool;

    @Autowired
    private PortUsageTool portUsageTool;

    @Autowired
    private ServiceStatusTool serviceStatusTool;

    @Autowired
    private RestartServiceTool restartServiceTool;

    @Autowired
    private ServiceRestartVerifier serviceRestartVerifier;

    @Test
    void contextLoads() {
        assertThat(commandTemplateRegistry).isNotNull();
        assertThat(commandRunner).isNotNull();
        assertThat(systemLoadTool).isNotNull();
        assertThat(topProcessesTool).isNotNull();
        assertThat(openPortsTool).isNotNull();
        assertThat(portUsageTool).isNotNull();
        assertThat(serviceStatusTool).isNotNull();
        assertThat(restartServiceTool).isNotNull();
        assertThat(serviceRestartVerifier).isNotNull();
    }
}
