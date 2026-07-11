package com.cup.opsagent.executor;

import com.cup.opsagent.safety.validation.ServiceNameValidator;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommandTemplateRegistryTest {

    private final CommandTemplateRegistry registry = new CommandTemplateRegistry();

    @Test
    void shouldBuildAllCanonicalTemplateCommands() {
        assertBuilt(registry.getSystemLoad(1000, 1024), CommandTemplateId.GET_SYSTEM_LOAD, List.of("uptime"));
        assertBuilt(registry.getTopProcesses(1000, 1024), CommandTemplateId.GET_TOP_PROCESSES, List.of("ps", "-eo", "pid,user,comm,%cpu,%mem", "--sort=-%cpu"));
        assertBuilt(registry.getOpenPorts(1000, 1024), CommandTemplateId.GET_OPEN_PORTS, List.of("ss", "-tulpn"));
        assertBuilt(registry.checkPortUsage(22, 1000, 1024), CommandTemplateId.CHECK_PORT_USAGE, List.of("ss", "-tulpn", "sport", "=", ":22"));
        assertBuilt(registry.getServiceStatus("nginx", 1000, 1024), CommandTemplateId.GET_SERVICE_STATUS, List.of("systemctl", "--no-pager", "status", "--", "nginx"));
        assertBuilt(registry.restartService("nginx", 1000, 1024), CommandTemplateId.RESTART_SERVICE, List.of("systemctl", "restart", "--", "nginx"));
        assertBuilt(registry.verifyServiceActive("nginx", 1000, 1024), CommandTemplateId.VERIFY_SERVICE_ACTIVE, List.of("systemctl", "is-active", "--", "nginx"));
    }

    @Test
    void shouldBuildFromGenericTemplateIdAndArgs() {
        assertBuilt(registry.build(CommandTemplateId.CHECK_PORT_USAGE, Map.of("port", 22), 1000, 1024), CommandTemplateId.CHECK_PORT_USAGE, List.of("ss", "-tulpn", "sport", "=", ":22"));
        assertBuilt(registry.build(CommandTemplateId.GET_SERVICE_STATUS, Map.of("serviceName", "nginx"), 1000, 1024), CommandTemplateId.GET_SERVICE_STATUS, List.of("systemctl", "--no-pager", "status", "--", "nginx"));
        assertBuilt(registry.build(CommandTemplateId.RESTART_SERVICE, Map.of("serviceName", "nginx"), 1000, 1024), CommandTemplateId.RESTART_SERVICE, List.of("systemctl", "restart", "--", "nginx"));
    }

    @Test
    void shouldRejectInvalidArgsWhenBuildingTemplates() {
        assertThatThrownBy(() -> registry.build(CommandTemplateId.CHECK_PORT_USAGE, Map.<String, Object>of(), 1000, 1024))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port");
        assertThatThrownBy(() -> registry.checkPortUsage(65536, 1000, 1024))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port");
        assertThatThrownBy(() -> registry.getServiceStatus("nginx;whoami", 1000, 1024))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serviceName");
    }

    @Test
    void shouldRejectExtraArgsWhenBuildingTemplates() {
        assertThatThrownBy(() -> registry.build(CommandTemplateId.GET_SYSTEM_LOAD, Map.of("unexpected", true), 1000, 1024))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unexpected command template args");
        assertThatThrownBy(() -> registry.build(CommandTemplateId.CHECK_PORT_USAGE, Map.of("port", 22, "unexpected", true), 1000, 1024))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unexpected command template args");
        assertThatThrownBy(() -> registry.build(CommandTemplateId.GET_SERVICE_STATUS, Map.of("serviceName", "nginx", "unexpected", true), 1000, 1024))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unexpected command template args");
    }

    @Test
    void shouldAllowAllCanonicalTemplateCommands() {
        assertThat(matches(CommandTemplateId.GET_SYSTEM_LOAD, List.of("uptime"))).isTrue();
        assertThat(matches(CommandTemplateId.GET_TOP_PROCESSES, List.of("ps", "-eo", "pid,user,comm,%cpu,%mem", "--sort=-%cpu"))).isTrue();
        assertThat(matches(CommandTemplateId.GET_OPEN_PORTS, List.of("ss", "-tulpn"))).isTrue();
        assertThat(matches(CommandTemplateId.CHECK_PORT_USAGE, List.of("ss", "-tulpn", "sport", "=", ":22"))).isTrue();
        assertThat(matches(CommandTemplateId.GET_SERVICE_STATUS, List.of("systemctl", "--no-pager", "status", "--", "nginx"))).isTrue();
        assertThat(matches(CommandTemplateId.RESTART_SERVICE, List.of("systemctl", "restart", "--", "nginx"))).isTrue();
        assertThat(matches(CommandTemplateId.VERIFY_SERVICE_ACTIVE, List.of("systemctl", "is-active", "--", "nginx"))).isTrue();
    }

    @Test
    void shouldRejectKnownCommandWithWrongTemplateId() {
        assertThat(matches(CommandTemplateId.GET_SYSTEM_LOAD, List.of("ss", "-tulpn"))).isFalse();
    }

    @Test
    void shouldRejectExtraArgumentsForGetSystemLoad() {
        assertThat(matches(CommandTemplateId.GET_SYSTEM_LOAD, List.of("uptime", "--help"))).isFalse();
    }

    @Test
    void shouldRejectTopProcessesWithExtraShellLikeArgument() {
        assertThat(matches(CommandTemplateId.GET_TOP_PROCESSES, List.of("ps", "-eo", "pid,user,comm,%cpu,%mem", "--sort=-%cpu", "|", "cat"))).isFalse();
    }

    @Test
    void shouldRejectOpenPortsWithUnexpectedFlag() {
        assertThat(matches(CommandTemplateId.GET_OPEN_PORTS, List.of("ss", "-tulpn", "-a"))).isFalse();
    }

    @Test
    void shouldRejectPortUsageWithoutColon() {
        assertThat(matches(CommandTemplateId.CHECK_PORT_USAGE, List.of("ss", "-tulpn", "sport", "=", "22"))).isFalse();
    }

    @Test
    void shouldRejectPortUsageOutOfRange() {
        assertThat(matches(CommandTemplateId.CHECK_PORT_USAGE, List.of("ss", "-tulpn", "sport", "=", ":65536"))).isFalse();
        assertThat(matches(CommandTemplateId.CHECK_PORT_USAGE, List.of("ss", "-tulpn", "sport", "=", ":0"))).isFalse();
    }

    @Test
    void shouldRejectPortUsageInjectedValue() {
        assertThat(matches(CommandTemplateId.CHECK_PORT_USAGE, List.of("ss", "-tulpn", "sport", "=", ":22;whoami"))).isFalse();
    }

    @Test
    void shouldRejectPlusSignedPortAtTemplateLayer() {
        assertThat(matches(CommandTemplateId.CHECK_PORT_USAGE, List.of("ss", "-tulpn", "sport", "=", ":+22"))).isFalse();
    }

    @Test
    void shouldRejectPortUsageWithLeadingZero() {
        assertThat(matches(CommandTemplateId.CHECK_PORT_USAGE, List.of("ss", "-tulpn", "sport", "=", ":00022"))).isFalse();
    }

    @Test
    void shouldRejectServiceStatusWithoutDoubleDash() {
        assertThat(matches(CommandTemplateId.GET_SERVICE_STATUS, List.of("systemctl", "--no-pager", "status", "nginx"))).isFalse();
    }

    @Test
    void shouldRejectServiceStatusWithRestartSubcommand() {
        assertThat(matches(CommandTemplateId.GET_SERVICE_STATUS, List.of("systemctl", "restart", "--", "nginx"))).isFalse();
    }

    @Test
    void shouldRejectRestartServiceWithoutDoubleDash() {
        assertThat(matches(CommandTemplateId.RESTART_SERVICE, List.of("systemctl", "restart", "nginx"))).isFalse();
    }

    @Test
    void shouldRejectRestartServiceInjectedServiceName() {
        assertThat(matches(CommandTemplateId.RESTART_SERVICE, List.of("systemctl", "restart", "--", "nginx;whoami"))).isFalse();
    }

    @Test
    void shouldRejectServiceStatusInjectedServiceName() {
        assertThat(matches(CommandTemplateId.GET_SERVICE_STATUS, List.of("systemctl", "--no-pager", "status", "--", "nginx;whoami"))).isFalse();
    }

    @Test
    void shouldRejectVerifyServiceActiveWithoutDoubleDash() {
        assertThat(matches(CommandTemplateId.VERIFY_SERVICE_ACTIVE, List.of("systemctl", "is-active", "nginx"))).isFalse();
    }

    @Test
    void shouldRejectVerifyServiceActiveInjectedServiceName() {
        assertThat(matches(CommandTemplateId.VERIFY_SERVICE_ACTIVE, List.of("systemctl", "is-active", "--", "nginx$(whoami)"))).isFalse();
    }

    @Test
    void shouldKeepServiceNameValidatorAndTemplateValidatorConsistent() {
        List<String> serviceNames = List.of("nginx", "nginx.service", "foo@bar.service", "-Htcp", "../nginx", "nginx/name", "nginx;whoami", "nginx$(whoami)");

        for (String serviceName : serviceNames) {
            boolean expected = ServiceNameValidator.validate(serviceName).isEmpty();
            assertThat(matches(CommandTemplateId.GET_SERVICE_STATUS, List.of("systemctl", "--no-pager", "status", "--", serviceName))).isEqualTo(expected);
            assertThat(matches(CommandTemplateId.RESTART_SERVICE, List.of("systemctl", "restart", "--", serviceName))).isEqualTo(expected);
            assertThat(matches(CommandTemplateId.VERIFY_SERVICE_ACTIVE, List.of("systemctl", "is-active", "--", serviceName))).isEqualTo(expected);
        }
    }

    @Test
    void shouldKeepCommandSpecAndRegistryFactoryMethodsPackagePrivate() throws NoSuchMethodException {
        assertThat(Modifier.isPublic(CommandSpec.class.getModifiers())).isFalse();
        assertThat(Modifier.isPublic(CommandTemplateRegistry.class.getDeclaredMethod("build", CommandTemplateId.class, Map.class, long.class, int.class).getModifiers())).isFalse();
        assertThat(Modifier.isPublic(CommandTemplateRegistry.class.getDeclaredMethod("getSystemLoad", long.class, int.class).getModifiers())).isFalse();
        assertThat(Modifier.isPublic(CommandTemplateRegistry.class.getDeclaredMethod("getTopProcesses", long.class, int.class).getModifiers())).isFalse();
        assertThat(Modifier.isPublic(CommandTemplateRegistry.class.getDeclaredMethod("getOpenPorts", long.class, int.class).getModifiers())).isFalse();
        assertThat(Modifier.isPublic(CommandTemplateRegistry.class.getDeclaredMethod("checkPortUsage", int.class, long.class, int.class).getModifiers())).isFalse();
        assertThat(Modifier.isPublic(CommandTemplateRegistry.class.getDeclaredMethod("getServiceStatus", String.class, long.class, int.class).getModifiers())).isFalse();
        assertThat(Modifier.isPublic(CommandTemplateRegistry.class.getDeclaredMethod("restartService", String.class, long.class, int.class).getModifiers())).isFalse();
        assertThat(Modifier.isPublic(CommandTemplateRegistry.class.getDeclaredMethod("verifyServiceActive", String.class, long.class, int.class).getModifiers())).isFalse();
    }

    @Test
    void shouldForbidCommandTemplateRegistryAndCommandSpecUsageOutsideExecutorProductionCode() throws IOException {
        List<Path> offenders = productionJavaFiles().stream()
                .filter(path -> !path.toAbsolutePath().normalize().startsWith(Path.of("src", "main", "java", "com", "cup", "opsagent", "executor").toAbsolutePath().normalize()))
                .filter(path -> contains(path, "CommandTemplateRegistry") || contains(path, "CommandSpec"))
                .toList();

        assertThat(offenders).isEmpty();
    }

    @Test
    void shouldForbidOldCommandRunnerSpecApiOutsideExecutorProductionCode() throws IOException {
        List<Path> offenders = productionJavaFiles().stream()
                .filter(path -> !path.toAbsolutePath().normalize().startsWith(Path.of("src", "main", "java", "com", "cup", "opsagent", "executor").toAbsolutePath().normalize()))
                .filter(path -> contains(path, "run(") && contains(path, "CommandSpec"))
                .toList();

        assertThat(offenders).isEmpty();
    }

    @Test
    void shouldForbidNewCommandSpecOutsideCommandTemplateRegistryInProductionCode() throws IOException {
        Path allowedPath = Path.of("src", "main", "java", "com", "cup", "opsagent", "executor", "CommandTemplateRegistry.java").toAbsolutePath().normalize();

        List<Path> offenders = productionJavaFiles().stream()
                .filter(path -> !path.toAbsolutePath().normalize().equals(allowedPath))
                .filter(path -> contains(path, "new CommandSpec("))
                .toList();

        assertThat(offenders).isEmpty();
    }

    @Test
    void shouldKeepProcessBuilderInsideProcessLauncherOnly() throws IOException {
        Path allowedPath = Path.of("src", "main", "java", "com", "cup", "opsagent", "executor", "ProcessBuilderProcessLauncher.java").toAbsolutePath().normalize();

        List<Path> offenders = productionJavaFiles().stream()
                .filter(path -> !path.toAbsolutePath().normalize().equals(allowedPath))
                .filter(path -> contains(path, "new ProcessBuilder("))
                .toList();

        assertThat(offenders).isEmpty();
    }

    @Test
    void shouldRejectRuntimeExecInProductionCode() throws IOException {
        List<Path> offenders = productionJavaFiles().stream()
                .filter(path -> contains(path, "Runtime.getRuntime().exec") || contains(path, ".exec("))
                .toList();

        assertThat(offenders).isEmpty();
    }

    private void assertBuilt(CommandSpec spec, CommandTemplateId templateId, List<String> command) {
        assertThat(spec.templateId()).isEqualTo(templateId);
        assertThat(spec.command()).containsExactlyElementsOf(command);
        assertThat(spec.timeoutMs()).isEqualTo(1000);
        assertThat(spec.outputLimitBytes()).isEqualTo(1024);
        assertThat(registry.matches(spec)).isTrue();
    }

    private boolean matches(CommandTemplateId templateId, List<String> command) {
        return registry.matches(new CommandSpec(templateId, command, 1000, 1024));
    }

    private List<Path> productionJavaFiles() throws IOException {
        return productionJavaFiles(Path.of("src", "main", "java"));
    }

    private List<Path> productionJavaFiles(Path sourceRoot) throws IOException {
        try (var stream = Files.walk(sourceRoot)) {
            return stream
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private boolean contains(Path path, String text) {
        try {
            return Files.readString(path).contains(text);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read " + path, exception);
        }
    }
}
