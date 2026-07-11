package com.cup.opsagent.api;

import com.cup.opsagent.tool.core.ToolDefinition;
import com.cup.opsagent.tool.core.ToolRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;

@RestController
@RequestMapping("/api/tools")
public class ToolController {

    private final ToolRegistry toolRegistry;

    public ToolController(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @GetMapping
    public Collection<ToolDefinition> listTools() {
        return toolRegistry.listDefinitions();
    }
}
