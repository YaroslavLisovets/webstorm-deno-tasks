package local.denotasks;

import com.intellij.execution.DefaultExecutionResult;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.ExecutionResult;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.javascript.debugger.CommandLineDebugConfigurator;
import com.intellij.javascript.debugger.DebugPortConfigurator;
import com.intellij.javascript.nodejs.NodeCommandLineUtil;
import com.intellij.javascript.nodejs.debug.NodeDebuggableRunProfileState;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

/**
 * Run: NodeRunProgramRunner calls {@link #execute(CommandLineDebugConfigurator)} with null
 * (or a generic runner calls {@link #execute(Executor, ProgramRunner)}).
 * Debug: NodeDebugProgramRunner calls {@link #execute(CommandLineDebugConfigurator)} with a port configurator.
 * The executor, not the configurator, decides which command line is built.
 */
final class DenoTaskRunState implements NodeDebuggableRunProfileState {
  static final Key<Boolean> WATCH_REMOVED = Key.create("deno.task.debug.watch.removed");

  private final ExecutionEnvironment environment;
  private final DenoTaskRunConfiguration configuration;

  DenoTaskRunState(@NotNull ExecutionEnvironment environment, @NotNull DenoTaskRunConfiguration configuration) {
    this.environment = environment;
    this.configuration = configuration;
  }

  @Override
  public @NotNull ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
    return start(null);
  }

  /** Also used for plain Run: NodeRunProgramRunner calls this with a null configurator. */
  @Override
  public @NotNull Promise<ExecutionResult> execute(@Nullable CommandLineDebugConfigurator configurator) {
    try {
      return Promises.resolvedPromise(start(configurator));
    }
    catch (ExecutionException e) {
      return Promises.rejectedPromise(e);
    }
  }

  private @NotNull ExecutionResult start(@Nullable CommandLineDebugConfigurator configurator) throws ExecutionException {
    GeneralCommandLine commandLine;
    if (DefaultDebugExecutor.EXECUTOR_ID.equals(environment.getExecutor().getId())) {
      // Never silently fall back to a plain run when debugging: the debugger would wait for a connection forever
      if (!(configurator instanceof DebugPortConfigurator portConfigurator)) {
        throw new ExecutionException("Unsupported debugger setup for Deno tasks: " +
                                     (configurator == null ? "no configurator" : configurator.getClass().getName()));
      }
      commandLine = configuration.createDebugCommandLine(portConfigurator.getDebugPort());
    }
    else {
      commandLine = configuration.createCommandLine();
    }
    OSProcessHandler handler = NodeCommandLineUtil.createProcessHandler(commandLine, true, configurator);
    ProcessTerminatedListener.attach(handler, environment.getProject());

    ConsoleView console = TextConsoleBuilderFactory.getInstance().createBuilder(environment.getProject()).getConsole();
    if (Boolean.TRUE.equals(commandLine.getUserData(WATCH_REMOVED))) {
      console.print("Watch mode is disabled while debugging: a reload restarts the Deno runtime and the debugger " +
                    "can't reattach. Rerun to pick up changes.\n", ConsoleViewContentType.SYSTEM_OUTPUT);
    }
    console.attachToProcess(handler);
    return new DefaultExecutionResult(console, handler);
  }
}
