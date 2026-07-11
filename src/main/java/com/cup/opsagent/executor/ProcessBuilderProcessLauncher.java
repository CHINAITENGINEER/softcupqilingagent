package com.cup.opsagent.executor;

import java.io.IOException;
import java.util.List;

final class ProcessBuilderProcessLauncher implements ProcessLauncher {

    @Override
    public Process start(List<String> command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectInput(ProcessBuilder.Redirect.PIPE);
        return builder.start();
    }
}
