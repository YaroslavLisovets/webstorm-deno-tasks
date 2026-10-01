package local.denotasks;

import com.intellij.execution.lineMarker.ExecutorAction;
import com.intellij.execution.lineMarker.RunLineMarkerContributor;
import com.intellij.icons.AllIcons;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class DenoTaskRunLineMarkerContributor extends RunLineMarkerContributor {
  @Override
  public @Nullable Info getInfo(@NotNull PsiElement element) {
    // Only the leaf token of a task key, e.g. the "dev" in  "dev": "deno run main.ts"
    if (element.getFirstChild() != null) return null;
    if (!(element.getParent() instanceof JsonStringLiteral key)) return null;
    if (!(key.getParent() instanceof JsonProperty property) || property.getNameElement() != key) return null;
    if (!DenoTaskUtil.isTaskProperty(property)) return null;

    String taskName = property.getName();
    return new Info(AllIcons.RunConfigurations.TestState.Run, ExecutorAction.getActions(),
                    e -> "Run 'deno task " + taskName + "'");
  }
}
