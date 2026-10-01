package local.denotasks;

import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.LazyRunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class DenoTaskConfigurationProducer extends LazyRunConfigurationProducer<DenoTaskRunConfiguration> {
  @Override
  public @NotNull ConfigurationFactory getConfigurationFactory() {
    return DenoTaskConfigurationType.getInstance();
  }

  @Override
  protected boolean setupConfigurationFromContext(@NotNull DenoTaskRunConfiguration configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    JsonProperty task = DenoTaskUtil.findTaskProperty(context.getPsiLocation());
    VirtualFile file = task == null ? null : task.getContainingFile().getVirtualFile();
    if (file == null) return false;

    configuration.setConfigPath(file.getPath());
    configuration.setTaskName(task.getName());
    configuration.setGeneratedName();
    sourceElement.set(task);
    return true;
  }

  @Override
  public boolean isConfigurationFromContext(@NotNull DenoTaskRunConfiguration configuration,
                                            @NotNull ConfigurationContext context) {
    JsonProperty task = DenoTaskUtil.findTaskProperty(context.getPsiLocation());
    VirtualFile file = task == null ? null : task.getContainingFile().getVirtualFile();
    return file != null
           && Objects.equals(configuration.getTaskName(), task.getName())
           && Objects.equals(configuration.getConfigPath(), file.getPath());
  }
}
