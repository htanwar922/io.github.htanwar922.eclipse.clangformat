package io.github.htanwar922.eclipse.clangformat.preferences;

public final class PreferenceConstants {

    /** Path to the clang-format executable. */
    public static final String CLANG_FORMAT_PATH = "clangFormatPath";

    /** Format style CLI argument passed to clang-format. */
    public static final String CLANG_FORMAT_STYLE = "clangFormatStyle";

    /** Extra space-separated CLI arguments passed to clang-format. */
    public static final String CLANG_FORMAT_ARGS = "clangFormatArgs";

    /** If true and there is no selection, format the whole file; if false, format only the current line. */
    public static final String FORMAT_WHOLE_FILE_WHEN_NO_SELECTION = "formatWholeFileWhenNoSelection";

    private PreferenceConstants() {
    }
}
