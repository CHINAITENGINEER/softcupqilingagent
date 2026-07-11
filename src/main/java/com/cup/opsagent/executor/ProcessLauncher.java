package com.cup.opsagent.executor;

import java.io.IOException;
import java.util.List;

interface ProcessLauncher {
    Process start(List<String> command) throws IOException;
}
