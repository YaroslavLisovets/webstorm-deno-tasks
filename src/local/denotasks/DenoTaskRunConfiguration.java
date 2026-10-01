package local.denotasks;

import com.intellij.deno.DenoSettings;
import com.intellij.deno.run.DenoInterpreter;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.LocatableConfigurationBase;
import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RuntimeConfigurationError;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreter;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizerUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.execution.ParametersListUtil;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.jetbrains.nodeJs.NodeDebugProgramRunnerKt;
import com.jetbrains.nodeJs.NodeJSDebuggableConfiguration;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Debugging goes through the Node.js debug runner, the same way Deno's own run configuration does. */
public final class DenoTaskRunConfiguration extends LocatableConfigurationBase<Object> implements NodeJSDebuggableConfiguration {
  private String configPath = "";
  private String taskName = "";
  private String arguments = "";
  private EnvironmentVariablesData envs = EnvironmentVariablesData.DEFAULT;

  DenoTaskRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory, @Nullable String name) {
    super(project, factory, name);
  }

  public @NotNull String getConfigPath() { return configPath; }
  public void setConfigPath(@Nullable String value) { configPath = StringUtil.notNullize(value); }
  public @NotNull String getTaskName() { return taskName; }
  public void setTaskName(@Nullable String value) { taskName = StringUtil.notNullize(value); }
  public @NotNull String getArguments() { return arguments; }
  public void setArguments(@Nullable String value) { arguments = StringUtil.notNullize(value); }
  public @NotNull EnvironmentVariablesData getEnvs() { return envs; }
  public void setEnvs(@NotNull EnvironmentVariablesData value) { envs = value; }

  @Override
  public @Nullable String suggestedName() {
    if (taskName.isEmpty()) return null;
    String name = "deno task " + taskName;
    // Disambiguate tasks from nested deno.json files (workspaces)
    File dir = new File(configPath).getParentFile();
    String base = getProject().getBasePath();
    if (dir != null && base != null && !FileUtil.pathsEqual(dir.getPath(), base)) {
      name += " (" + dir.getName() + ")";
    }
    return name;
  }

  @Override
  public @NotNull SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
    return new DenoTaskSettingsEditor(getProject());
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    if (StringUtil.isEmptyOrSpaces(taskName)) throw new RuntimeConfigurationError("Task name is not specified");
    if (StringUtil.isEmptyOrSpaces(configPath) || !new File(configPath).isFile()) {
      throw new RuntimeConfigurationError("deno.json not found: " + configPath);
    }
  }

  @Override
  public @NotNull RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) {
    return new DenoTaskRunState(environment, this);
  }

  /** {@code deno task --config <file> <task> [args]} */
  @NotNull GeneralCommandLine createCommandLine() {
    GeneralCommandLine commandLine = createBaseCommandLine();
    commandLine.addParameter(taskName);
    commandLine.addParameters(ParametersListUtil.parse(arguments));
    return commandLine;
  }

  /**
   * {@code deno task --config <file> --eval "<task command with --inspect-brk>"}. Deno can't enable the inspector
   * from outside (no env var for it), so the flag has to go into the task's own deno command.
   */
  @NotNull GeneralCommandLine createDebugCommandLine(int debugPort) throws ExecutionException {
    String command = DenoTaskUtil.readTaskCommand(getProject(), configPath, taskName);
    if (command == null) throw new ExecutionException("Task '" + taskName + "' not found in " + configPath);
    String debugCommand = DenoTaskUtil.injectInspectFlag(command, debugPort);
    if (debugCommand == null) {
      throw new ExecutionException("Can't debug task '" + taskName + "': its command doesn't start Deno via " +
                                   "'deno run', 'deno serve' or 'deno test':\n" + command);
    }
    if (!StringUtil.isEmptyOrSpaces(arguments)) debugCommand += " " + arguments.trim();

    GeneralCommandLine commandLine = createBaseCommandLine();
    commandLine.addParameters("--eval", debugCommand);
    if (DenoTaskUtil.hasWatchFlag(command)) commandLine.putUserData(DenoTaskRunState.WATCH_REMOVED, Boolean.TRUE);
    return commandLine;
  }

  private @NotNull GeneralCommandLine createBaseCommandLine() {
    GeneralCommandLine commandLine = new GeneralCommandLine(findDeno(getProject()), "task", "--config", configPath);
    commandLine.setWorkDirectory(new File(configPath).getParentFile());
    commandLine.setCharset(StandardCharsets.UTF_8);
    envs.configureCommandLine(commandLine, true);
    return commandLine;
  }

  /** Deno path from Settings | Languages & Frameworks | Deno, else PATH, else ~/.deno/bin. */
  private static @NotNull String findDeno(@NotNull Project project) {
    String configured = DenoSettings.Companion.getService(project).getDenoPath();
    if (!StringUtil.isEmptyOrSpaces(configured)) return configured;
    String exe = SystemInfo.isWindows ? "deno.exe" : "deno";
    File onPath = PathEnvironmentVariableUtil.findInPath(exe);
    if (onPath != null) return onPath.getPath();
    File inHome = new File(System.getProperty("user.home"), ".deno/bin/" + exe);
    return inHome.isFile() ? inHome.getPath() : "deno";
  }

  @Override
  public @NotNull NodeJsInterpreter getInterpreter() {
    return new DenoInterpreter(findDeno(getProject()));
  }

  /**
   * Must be true (as in Deno's own run configuration): otherwise the Node.js debug runner attaches via
   * NODE_OPTIONS + debugConnector.js, which Deno ignores, and the state never receives a debug port.
   */
  @Override
  public boolean hasConfiguredDebugAddress() {
    return true;
  }

  @Override
  public @NotNull InetSocketAddress computeDebugAddress(RunProfileState state) throws ExecutionException {
    return NodeDebugProgramRunnerKt.computeDebugAddress(this);
  }

  @Override
  public @NotNull XDebugProcess createDebugProcess(@NotNull InetSocketAddress socketAddress,
                                                   @NotNull XDebugSession session,
                                                   @Nullable ExecutionResult executionResult,
                                                   @NotNull ExecutionEnvironment environment) {
    return NodeDebugProgramRunnerKt.createDebugProcess(this, socketAddress, session, executionResult);
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    super.readExternal(element);
    configPath = StringUtil.notNullize(JDOMExternalizerUtil.readCustomField(element, "configPath"));
    taskName = StringUtil.notNullize(JDOMExternalizerUtil.readCustomField(element, "taskName"));
    arguments = StringUtil.notNullize(JDOMExternalizerUtil.readCustomField(element, "arguments"));
    envs = EnvironmentVariablesData.readExternal(element);
  }

  @Override
  public void writeExternal(@NotNull Element element) {
    super.writeExternal(element);
    JDOMExternalizerUtil.writeCustomField(element, "configPath", configPath);
    JDOMExternalizerUtil.writeCustomField(element, "taskName", taskName);
    if (!arguments.isEmpty()) JDOMExternalizerUtil.writeCustomField(element, "arguments", arguments);
    envs.writeExternal(element);
  }
}
