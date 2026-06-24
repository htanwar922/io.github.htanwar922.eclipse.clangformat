package io.github.htanwar922.eclipse.clangformat.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import io.github.htanwar922.eclipse.clangformat.Activator;

/**
 * Window > Preferences > C/C++ > Code Style > Formatters > Clang-Format
 */
public class ClangFormatPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    public ClangFormatPreferencePage() {
        super(GRID);
        setPreferenceStore(Activator.getDefault().getPreferenceStore());
        setDescription("Settings for clang-format integration.");
    }

    @Override
    protected void createFieldEditors() {
        addField(new FileFieldEditor(
                PreferenceConstants.CLANG_FORMAT_PATH,
                "Clang-format executable path:",
                true,
                getFieldEditorParent()));

        addField(new StringFieldEditor(
                PreferenceConstants.CLANG_FORMAT_ARGS,
                "Additional arguments:",
                getFieldEditorParent()));

        addField(new BooleanFieldEditor(
                PreferenceConstants.FORMAT_WHOLE_FILE_WHEN_NO_SELECTION,
                "Format entire file when there is no text selection",
                getFieldEditorParent()));
    }

    @Override
    public void init(IWorkbench workbench) {
        // nothing to do
    }
}
