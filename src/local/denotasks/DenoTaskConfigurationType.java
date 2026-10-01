package local.denotasks;

import com.intellij.deno.DenoUtil;
import com.intellij.execution.configurations.ConfigurationTypeUtil;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.SimpleConfigurationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

public final class DenoTaskConfigurationType extends SimpleConfigurationType {
  public DenoTaskConfigurationType() {
    super("DenoTaskRunConfiguration", "Deno Task", "Runs a task from deno.json via 'deno task'",
          // DenoIcons.Deno is a 40x40 SVG; this is the 16px variant Deno's own run configuration uses
          NotNullLazyValue.createValue(() -> DenoUtil.INSTANCE.getDefaultDenoIcon()));
  }

  public static DenoTaskConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(DenoTaskConfigurationType.class);
  }

  @Override
  public @NotNull RunConfiguration createTemplateConfiguration(@NotNull Project project) {
    return new DenoTaskRunConfiguration(project, this, "Deno Task");
  }
}
