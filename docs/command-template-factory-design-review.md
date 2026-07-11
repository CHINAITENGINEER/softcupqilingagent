# CommandTemplateRegistry / CommandSpec 硬化复审与模板工厂化方案

检查日期：2026-06-28

检查范围：
- `src/main/java/com/cup/opsagent/executor/CommandSpec.java`
- `src/main/java/com/cup/opsagent/executor/CommandRunner.java`
- `src/main/java/com/cup/opsagent/executor/CommandTemplateRegistry.java`
- `src/main/java/com/cup/opsagent/executor/CommandTemplateId.java`
- `src/main/java/com/cup/opsagent/tool/builtin/*.java`
- `src/main/java/com/cup/opsagent/verifier/ServiceRestartVerifier.java`
- `src/test/java/com/cup/opsagent/executor/CommandRunnerTest.java`
- `src/test/java/com/cup/opsagent/executor/CommandTemplateRegistryTest.java`
- `src/test/java/com/cup/opsagent/verifier/ServiceRestartVerifierTest.java`

本轮只做静态复审和方案设计，未运行测试，未修改业务代码。

## 1. 总体结论

### 是否发现 P0/P1

未发现 P0，也未发现 P1。

本轮硬化有效：

- `CommandSpec` 已通过 `List.copyOf(command)` 做防御性复制，结合 String 不可变特性，已解决当前 command list 被外部修改导致的 TOCTOU 风险。
- `CommandSpec` 已拒绝 null / blank command element，避免 `ProcessBuilder` 入口收到异常命令片段。
- `CommandRunner` 已移除无参构造，Spring 注入和测试替身都需要显式提供 `CommandTemplateRegistry`。
- `CommandRunner.run(null)` 已返回失败 `ToolExecutionResult`，符合当前执行失败语义。
- `CHECK_PORT_USAGE` 已收紧为 canonical port 形态，拒绝 `:00022`、`:+22`、`:22;whoami` 等非规范或注入式输入。
- 生产代码中唯一 `ProcessBuilder` 仍在 `CommandRunner` 内部，未发现 `Runtime.exec` 绕过。

### 当前边界是否可以进入模板工厂化

可以进入。

当前边界已足够支撑 MVP，下一阶段的主要目标不再是补救明显漏洞，而是把“工具层手写命令数组”收口为“模板注册表生成命令数组”。

### 是否建议先补工具一致性测试还是直接重构

建议直接进入模板工厂化。

原因：

- 工具一致性测试只能证明当前手写 `List<String>` 和模板匹配一致。
- 模板工厂化可以从根上消除工具层手写命令数组的问题。
- 如果模板工厂化会延后超过一个迭代，再补一组临时的“工具实际生成命令与模板一致性”测试；否则优先重构。

## 2. 问题列表

### P0

无。

### P1

无。

### P2-1：工具层仍直接 `new CommandSpec(...)`，命令数组仍由工具手写

文件/类/方法：

- `SystemLoadTool.execute`
- `TopProcessesTool.execute`
- `OpenPortsTool.execute`
- `PortUsageTool.execute`
- `ServiceStatusTool.execute`
- `ServiceRestartVerifier.verify`

问题描述：

当前所有生产调用点都带了正确的 `CommandTemplateId`，也会被 `CommandTemplateRegistry.matches(...)` 二次校验。但工具层仍然直接构造 `List<String>` 和 `new CommandSpec(...)`。

为什么是问题：

- 这仍是“事后匹配”模型，不是“源头生成”模型。
- 未来新增工具或修改命令时，可能出现工具手写命令与模板规则漂移。
- 安全边界更理想的形态是工具层只传 `templateId + typed args`，命令数组只由 `CommandTemplateRegistry` 生成。

最小修复建议：

- 新增 `CommandTemplateRegistry.build(templateId, args, timeoutMs, outputLimitBytes)`。
- 迁移 6 个工具和 `ServiceRestartVerifier` 使用 build。
- `CommandRunner` 保留 `matches(spec)` 二次防线。

建议新增测试：

- 静态测试：生产工具包和 verifier 包禁止 `new CommandSpec(`。
- build 输出测试：每个 templateId 的 build 输出应与 canonical command 完全一致。

### P2-2：`CommandSpec` 不拒绝 executable 中的路径分隔符，但应由模板层负责

文件/类/方法：

- `CommandSpec`
- `CommandTemplateRegistry.matches(...)`

问题描述：

`CommandSpec` 当前只校验 command 非空、元素非 null/blank、timeout 和 output limit 有效，没有拒绝 `command[0]` 中包含 `/` 或 `\`。

为什么不是 P1：

- 当前模板层对 6 个模板都要求固定 executable 名称，例如 `uptime`、`ps`、`ss`、`systemctl`。
- `/usr/bin/uptime`、`C:\...\cmd.exe` 等路径形式不会匹配模板。
- `CommandRunner` 仍先执行 shell wrapper denylist，能先拒绝 `/bin/sh`、`cmd.exe`、`powershell.exe` 这类 shell wrapper。

最小修复建议：

- 不建议在 `CommandSpec` 层做通用路径分隔符拒绝。
- 保持 `CommandSpec` 作为结构容器，把命令形态、executable 是否允许路径交给 `CommandTemplateRegistry`。
- 进入模板工厂化后，外部调用方不再能自由传 executable。

建议新增测试：

- `GET_SYSTEM_LOAD` 拒绝 `/usr/bin/uptime`。
- `GET_OPEN_PORTS` 拒绝 `/usr/sbin/ss`。
- `GET_SERVICE_STATUS` 拒绝 `/bin/systemctl --no-pager status -- nginx`。

### P2-3：`CommandRunner.run(null)` 语义可接受，但建议后续引入更明确的 execution failure reason

文件/类/方法：

- `CommandRunner.run`
- `ToolExecutionResult`

问题描述：

`CommandRunner.run(null)` 现在返回 `success=false`、`stderr="command spec is required"`、`exitCode=null`。这与当前命令执行失败结果风格一致，但 `ToolExecutionResult` 还没有结构化 failure reason。

为什么是问题：

- 当前只能通过 stderr 文本区分 template mismatch、shell wrapper denied、null spec、timeout、IOException。
- 对审计和 UI 来说，结构化错误码会更清楚。

最小修复建议：

- MVP 可保持现状。
- 下一阶段如果增加 `CommandExecutionOptions` 或执行审计，可考虑给 `ToolExecutionResult` 增加 `failureCode`，例如：
  - `COMMAND_SPEC_MISSING`
  - `SHELL_WRAPPER_DENIED`
  - `TEMPLATE_MISMATCH`
  - `COMMAND_TIMEOUT`
  - `PROCESS_START_FAILED`

建议新增测试：

- 当前测试已覆盖 null spec 失败返回；后续如增加 failureCode，再补 failureCode 断言。

### P2-4：`CommandRunner` 构造器未显式拒绝 null `CommandTemplateRegistry`

文件/类/方法：

- `CommandRunner(CommandTemplateRegistry commandTemplateRegistry)`

问题描述：

无参构造已移除，这是好事。但当前构造器没有显式 `Objects.requireNonNull(commandTemplateRegistry)`。

为什么是问题：

- Spring 正常注入不会传 null。
- 但测试或手动构造可能传 null，导致运行时 NPE。
- 这是健壮性问题，不是当前安全绕过。

最小修复建议：

- 构造器中增加 `Objects.requireNonNull(commandTemplateRegistry, "commandTemplateRegistry must not be null")`。

建议新增测试：

- `shouldRejectNullCommandTemplateRegistryInConstructor`。

## 3. 模板工厂化设计

### 3.1 最小侵入方案

保留现有三件套：

- `CommandTemplateId`
- `CommandSpec`
- `CommandTemplateRegistry.matches(spec)`

新增：

```java
public CommandSpec build(
        CommandTemplateId templateId,
        Map<String, Object> args,
        long timeoutMs,
        int outputLimitBytes
)
```

最小方案原则：

- 工具层不再手写 `List<String> command`。
- 工具层仍可传 `definition().timeoutMs()` 和 `definition().outputLimitBytes()`。
- `build(...)` 内部复用现有 validator：
  - `NumericArgumentValidator.integerInRange(...)`
  - `ServiceNameValidator.extract(...)`
- `build(...)` 构造出 `CommandSpec` 后，最好内部调用 `matches(spec)` 自检；若不匹配，抛 `IllegalStateException`，说明模板定义自身有 bug。
- `CommandRunner.run(...)` 仍调用 `matches(spec)`，作为二次防线。

建议失败语义：

- 参数非法时抛 `IllegalArgumentException`，由工具层捕获并返回 `ToolExecutionResult.skipped(...)`；或直接让工具层在调用 build 前保持现有 extract 校验。
- 为最小改动，第一步可以继续保留工具层现有参数校验，build 作为第二道参数校验。

### 3.2 理想长期方案

长期不要让工具层传 `Map<String, Object>`，而是改为 typed args。

推荐演进目标：

```java
sealed interface CommandTemplateArgs permits NoArgs, PortArgs, ServiceNameArgs {}

record NoArgs() implements CommandTemplateArgs {}
record PortArgs(int port) implements CommandTemplateArgs {}
record ServiceNameArgs(String serviceName) implements CommandTemplateArgs {}

CommandSpec build(CommandTemplateId templateId, CommandTemplateArgs args, CommandExecutionOptions options)
```

最终 `CommandRunner` 对外可进一步收口为：

```java
ToolExecutionResult run(
        String toolName,
        CommandTemplateId templateId,
        CommandTemplateArgs args,
        CommandExecutionOptions options
)
```

此时：

- `CommandSpec` 变为 executor 包内部对象。
- 工具层无法构造自由命令数组。
- `CommandRunner` 内部通过 registry build，再 matches，再执行。

### 3.3 推荐的 build API

短期推荐 API：

```java
public CommandSpec build(
        CommandTemplateId templateId,
        Map<String, Object> args,
        long timeoutMs,
        int outputLimitBytes
)
```

原因：

- 与现有 `ToolCall.arguments()` 类型一致。
- 改动最小。
- 可以快速迁移 6 个工具和 verifier。
- 不需要引入新的 args record。

同时建议提供 typed convenience methods，降低工具层误传 key 的概率：

```java
public CommandSpec getSystemLoad(long timeoutMs, int outputLimitBytes)
public CommandSpec getTopProcesses(long timeoutMs, int outputLimitBytes)
public CommandSpec getOpenPorts(long timeoutMs, int outputLimitBytes)
public CommandSpec checkPortUsage(int port, long timeoutMs, int outputLimitBytes)
public CommandSpec getServiceStatus(String serviceName, long timeoutMs, int outputLimitBytes)
public CommandSpec verifyServiceActive(String serviceName, long timeoutMs, int outputLimitBytes)
```

推荐实际迁移时优先让工具调用 typed method，而不是直接传 Map。Map 版本保留给通用调度层或后续审批执行层。

### 3.4 每个 templateId 的 args schema

#### GET_SYSTEM_LOAD

args：

- 无参数。

build 输出：

```text
["uptime"]
```

校验：

- args 为 null 或空均可。
- 若 args 包含额外字段，建议短期忽略，长期拒绝。

#### GET_TOP_PROCESSES

args：

- 当前命令无参数。
- `limit` 当前不进入命令数组。

build 输出：

```text
["ps", "-eo", "pid,user,comm,%cpu,%mem", "--sort=-%cpu"]
```

校验：

- 当前可忽略 `limit`，因为工具本身也未将 limit 用到命令。
- 长期如果支持 limit，建议在 Java 层截断输出，不要引入 shell pipe 或 `head`。

#### GET_OPEN_PORTS

args：

- 无参数。

build 输出：

```text
["ss", "-tulpn"]
```

#### CHECK_PORT_USAGE

args：

- `port`: required integer, range `1..65535`。

build 输出：

```text
["ss", "-tulpn", "sport", "=", ":" + port]
```

校验：

- 复用 `NumericArgumentValidator.integerInRange(rawPort, 1, 65535)`。
- 输出必须是 canonical decimal，不允许 `:00022`、`:+22`。

#### GET_SERVICE_STATUS

args：

- `serviceName`: required string, valid by `ServiceNameValidator`。

build 输出：

```text
["systemctl", "--no-pager", "status", "--", serviceName]
```

校验：

- 复用 `ServiceNameValidator.extract(rawServiceName)`。
- 强制 `--`。

#### VERIFY_SERVICE_ACTIVE

args：

- `serviceName`: required string, valid by `ServiceNameValidator`。

build 输出：

```text
["systemctl", "is-active", "--", serviceName]
```

校验：

- 复用 `ServiceNameValidator.extract(rawServiceName)`。
- 强制 `--`。

### 3.5 对 6 个现有工具和 ServiceRestartVerifier 的迁移步骤

#### Step 1：注入 CommandTemplateRegistry

当前工具继承 `AbstractBuiltinTool`，里面只有 `CommandRunner`。最小迁移有两种：

方案 A：

- `AbstractBuiltinTool` 增加 `CommandTemplateRegistry commandTemplateRegistry`。
- 所有工具构造器传入 registry。

方案 B：

- 不动 `AbstractBuiltinTool`。
- 每个需要执行命令的工具单独注入 `CommandTemplateRegistry`。

推荐方案 A，统一些。

#### Step 2：替换工具层 new CommandSpec

迁移目标：

- `SystemLoadTool` 使用 `commandTemplateRegistry.getSystemLoad(...)`。
- `TopProcessesTool` 使用 `commandTemplateRegistry.getTopProcesses(...)`。
- `OpenPortsTool` 使用 `commandTemplateRegistry.getOpenPorts(...)`。
- `PortUsageTool` 使用 `commandTemplateRegistry.checkPortUsage(port, ...)`。
- `ServiceStatusTool` 使用 `commandTemplateRegistry.getServiceStatus(serviceName, ...)`。
- `ServiceRestartVerifier` 使用 `commandTemplateRegistry.verifyServiceActive(serviceName, ...)`。

#### Step 3：保留 CommandRunner matches 二次防线

不要因为 build 已经保证合法就删除 `CommandRunner` 中的：

```java
if (!commandTemplateRegistry.matches(spec)) { ... }
```

理由：

- 防止未来手动构造 `CommandSpec`。
- 防止 build 自身 bug。
- 防止测试替身或其他内部调用绕过。

#### Step 4：静态禁止工具层 new CommandSpec

新增静态测试：

- `src/main/java/com/cup/opsagent/tool` 下不允许出现 `new CommandSpec(`。
- `src/main/java/com/cup/opsagent/verifier` 下不允许出现 `new CommandSpec(`，除非显式白名单。

#### Step 5：后续收口 CommandSpec 可见性

长期可以将 `CommandSpec` 从 public record 改成 executor 包内部类型，或者保留 public 但标记为 internal，并由静态测试保证工具层不直接构造。

## 4. 测试方案

### 4.1 build 输出测试

新增 `CommandTemplateRegistryBuildTest` 或扩展现有 `CommandTemplateRegistryTest`：

- `buildGetSystemLoadShouldReturnCanonicalCommand`
- `buildGetTopProcessesShouldReturnCanonicalCommand`
- `buildGetOpenPortsShouldReturnCanonicalCommand`
- `buildCheckPortUsageShouldReturnCanonicalCommand`
- `buildGetServiceStatusShouldReturnCanonicalCommand`
- `buildVerifyServiceActiveShouldReturnCanonicalCommand`

断言：

- `templateId` 正确。
- `command` 完全等于 canonical command。
- `timeoutMs` 和 `outputLimitBytes` 保留。
- `registry.matches(spec)` 为 true。

参数非法测试：

- `buildCheckPortUsageShouldRejectMissingPort`
- `buildCheckPortUsageShouldRejectDecimalPort`
- `buildCheckPortUsageShouldRejectOutOfRangePort`
- `buildGetServiceStatusShouldRejectInjectedServiceName`
- `buildVerifyServiceActiveShouldRejectDashServiceName`
- `buildShouldRejectUnexpectedArgsForNoArgTemplate`，如果决定短期拒绝多余字段。

### 4.2 matches 二次防线测试

现有 matches 测试已经较足，模板工厂化后仍应保留。

新增重点：

- build 生成的 spec 被手动篡改后，`matches` 应拒绝。
- `GET_SERVICE_STATUS` 与 `VERIFY_SERVICE_ACTIVE` 子命令互换仍拒绝。
- `CHECK_PORT_USAGE` 仍拒绝 `:00022`、`:+22`、`:22;whoami`。

### 4.3 工具层不再 new CommandSpec 的静态测试

建议新增：

```text
shouldForbidNewCommandSpecInToolPackage
shouldForbidNewCommandSpecInVerifierPackage
```

扫描范围：

- `src/main/java/com/cup/opsagent/tool`
- `src/main/java/com/cup/opsagent/verifier`

允许范围：

- `src/main/java/com/cup/opsagent/executor`
- 测试代码可以继续构造 `CommandSpec` 测边界。

### 4.4 CommandRunner 仍不允许绕过模板的测试

保留并增强：

- `shouldDenyCommandThatDoesNotMatchTemplate`
- `shouldDenyDirectShellWrapper`
- `shouldDenyShellWrapperWithAbsolutePath`
- `shouldDenyWindowsShellWrapperWithAbsolutePath`
- `shouldDenyPowershellEvenWithPreOptions`

新增：

- `shouldRejectSpecBuiltWithWrongTemplateEvenAfterFactoryExists`
- `shouldRejectManualCommandSpecOutsideCanonicalShape`
- `shouldRejectAbsoluteExecutablePathEvenIfCommandSemanticallyEquivalent`

### 4.5 Spring 注入测试

建议增加轻量 Spring context 测试：

- `CommandRunner` bean 能成功创建。
- `CommandTemplateRegistry` bean 能成功注入 `CommandRunner`。
- 不存在无参构造回退。

## 5. 逐步禁止工具层直接 new CommandSpec 的路径

### 阶段 1：允许但迁移

- 新增 build API。
- 工具层逐个改为 registry build。
- `CommandRunner` 保留 `run(String, CommandSpec)`。

### 阶段 2：静态禁止

- 加静态测试禁止 `tool` 和 `verifier` 包出现 `new CommandSpec(`。
- CI 中强制执行。

### 阶段 3：API 收口

- 新增 `CommandRunner.run(toolName, templateId, args, options)`。
- 工具层只调用这个新 API。
- 旧 `run(toolName, CommandSpec)` 降级为 package-private，或只供 executor 包内部使用。

### 阶段 4：CommandSpec 内部化

- 将 `CommandSpec` 从公共边界变成 executor 内部对象。
- 外部代码无法构造自由命令数组。

## 6. 复审结论

当前 CommandTemplateRegistry / CommandSpec 硬化已经把上一轮发现的主要问题补上了。`CommandSpec` 防御性复制足以解决当前 command TOCTOU 风险；`CommandRunner` 移除无参构造后，Spring 注入模型更清楚；端口 canonical 化也符合模板白名单目标。

当前无 P0/P1。下一阶段建议直接进入模板工厂化，而不是在手写命令数组模型上继续加大量一致性测试。保留 `matches(spec)` 作为二次防线，新增 `build(...)` 作为源头生成能力，然后用静态架构测试逐步禁止工具层直接 `new CommandSpec`。
