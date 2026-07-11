package com.cup.opsagent.planner;

import com.cup.opsagent.agent.model.TaskPlan;

public interface TaskPlanner {

    TaskPlan plan(String userInput);
}
