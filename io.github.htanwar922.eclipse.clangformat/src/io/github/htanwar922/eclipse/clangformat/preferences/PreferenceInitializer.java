package io.github.htanwar922.eclipse.clangformat.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import io.github.htanwar922.eclipse.clangformat.Activator;

public class PreferenceInitializer extends AbstractPreferenceInitializer {

    @Override
    public void initializeDefaultPreferences() {
        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        store.setDefault(PreferenceConstants.CLANG_FORMAT_PATH, "clang-format");
        store.setDefault(PreferenceConstants.CLANG_FORMAT_STYLE, "");
        store.setDefault(PreferenceConstants.CLANG_FORMAT_ARGS, "");
        store.setDefault(PreferenceConstants.FORMAT_WHOLE_FILE_WHEN_NO_SELECTION, true);
    }
}
