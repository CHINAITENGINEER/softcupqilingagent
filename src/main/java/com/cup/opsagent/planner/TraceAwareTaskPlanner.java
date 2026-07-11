package com.cup.opsagent.planner;

import com.cup.opsagent.agent.model.TaskPlan;

public interface TraceAwareTaskPlanner extends TaskPlanner {

    TaskPlan plan(String traceId, String userInput);
}
