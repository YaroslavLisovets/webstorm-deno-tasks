package local.denotasks;

import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

final class DenoTaskSettingsEditor extends SettingsEditor<DenoTaskRunConfiguration> {
  private final TextFieldWithBrowseButton configField = new TextFieldWithBrowseButton();
  private final JTextField taskField = new JTextField();
  private final RawCommandLineEditor argumentsField = new RawCommandLineEditor();
  private final EnvironmentVariablesComponent envField;
  private final JPanel panel;

  DenoTaskSettingsEditor(@NotNull Project project) {
    envField = new EnvironmentVariablesComponent(project);
    configField.addBrowseFolderListener(project, FileChooserDescriptorFactory.singleFile().withTitle("Select deno.json"));
    panel = FormBuilder.createFormBuilder()
      .addLabeledComponent("deno.json:", configField)
      .addLabeledComponent("Task:", taskField)
      .addLabeledComponent("Arguments:", argumentsField)
      .addComponent(envField)
      .addComponentFillVertically(new JPanel(), 0)
      .getPanel();
  }

  @Override
  protected void resetEditorFrom(@NotNull DenoTaskRunConfiguration configuration) {
    configField.setText(FileUtil.toSystemDependentName(configuration.getConfigPath()));
    taskField.setText(configuration.getTaskName());
    argumentsField.setText(configuration.getArguments());
    envField.setEnvData(configuration.getEnvs());
  }

  @Override
  protected void applyEditorTo(@NotNull DenoTaskRunConfiguration configuration) {
    configuration.setConfigPath(FileUtil.toSystemIndependentName(configField.getText().trim()));
    configuration.setTaskName(taskField.getText().trim());
    configuration.setArguments(argumentsField.getText());
    configuration.setEnvs(envField.getEnvData());
  }

  @Override
  protected @NotNull JComponent createEditor() {
    return panel;
  }
}
