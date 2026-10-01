package local.denotasks;

import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DenoTaskUtil {
  private DenoTaskUtil() {
  }

  static boolean isDenoConfig(@Nullable PsiFile file) {
    if (!(file instanceof JsonFile)) return false;
    String name = file.getName();
    return name.equals("deno.json") || name.equals("deno.jsonc");
  }

  /** True for a direct child of the top-level "tasks" object in deno.json(c). */
  static boolean isTaskProperty(@Nullable JsonProperty property) {
    if (property == null || !(property.getParent() instanceof JsonObject tasksObject)) return false;
    if (!(tasksObject.getParent() instanceof JsonProperty tasksProperty) || !"tasks".equals(tasksProperty.getName())) return false;
    PsiElement root = tasksProperty.getParent();
    return root instanceof JsonObject && root.getParent() instanceof JsonFile && isDenoConfig(root.getContainingFile());
  }

  /** Finds the task property enclosing the element (the task key itself, or anything inside its value). */
  static @Nullable JsonProperty findTaskProperty(@Nullable PsiElement element) {
    if (element == null || !isDenoConfig(element.getContainingFile())) return null;
    JsonProperty property = PsiTreeUtil.getParentOfType(element, JsonProperty.class, false);
    while (property != null) {
      if (isTaskProperty(property)) return property;
      property = PsiTreeUtil.getParentOfType(property, JsonProperty.class, true);
    }
    return null;
  }

  /** The task's command: either the string value, or the "command" field of the object form. */
  static @Nullable String readTaskCommand(@NotNull Project project, @NotNull String configPath, @NotNull String taskName) {
    return ReadAction.computeBlocking(() -> {
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(configPath);
      PsiFile psiFile = file == null ? null : PsiManager.getInstance(project).findFile(file);
      if (!(psiFile instanceof JsonFile jsonFile) || !(jsonFile.getTopLevelValue() instanceof JsonObject root)) return null;
      JsonProperty tasks = root.findProperty("tasks");
      if (tasks == null || !(tasks.getValue() instanceof JsonObject tasksObject)) return null;
      JsonProperty task = tasksObject.findProperty(taskName);
      JsonValue value = task == null ? null : task.getValue();
      if (value instanceof JsonObject taskObject) {
        JsonProperty command = taskObject.findProperty("command");
        value = command == null ? null : command.getValue();
      }
      return value instanceof JsonStringLiteral literal ? literal.getValue() : null;
    });
  }

  private static final Pattern DENO_SUBCOMMAND = Pattern.compile("(?<![\\w.-])(?:\\S*[/\\\\])?deno(?:\\.exe)?\\s+(?:run|serve|test)(?=\\s|$)");
  private static final Pattern INSPECT_FLAG = Pattern.compile("\\s--inspect(?:-brk|-wait)?(?:=\\S+)?(?=\\s|$)");
  // --watch, --hmr (legacy --watch-hmr), --watch-exclude, and --no-clear-screen (which errors without --watch)
  private static final Pattern WATCH_FLAG =
    Pattern.compile("\\s--(?:(?:watch(?:-hmr|-exclude)?|hmr)(?:=\\S+)?|no-clear-screen)(?=\\s|$)");

  static boolean hasWatchFlag(@NotNull String command) {
    return WATCH_FLAG.matcher(command).find();
  }

  /**
   * Adds --inspect-brk (as Deno's own run configuration does) right after the first {@code deno run|serve|test}
   * and removes watch flags: a watch reload tears down the runtime and ends the inspector session, and the IDE
   * debugger doesn't reattach, so breakpoints would silently stop working after the first change.
   */
  static @Nullable String injectInspectFlag(@NotNull String command, int port) {
    String cleaned = WATCH_FLAG.matcher(INSPECT_FLAG.matcher(command).replaceAll("")).replaceAll("");
    Matcher matcher = DENO_SUBCOMMAND.matcher(cleaned);
    if (!matcher.find()) return null;
    return cleaned.substring(0, matcher.end()) + " --inspect-brk=127.0.0.1:" + port + cleaned.substring(matcher.end());
  }
}
