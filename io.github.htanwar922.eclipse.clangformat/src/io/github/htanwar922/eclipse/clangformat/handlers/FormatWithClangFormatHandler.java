package io.github.htanwar922.eclipse.clangformat.handlers;

import java.io.File;
import java.net.URI;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentRewriteSession;
import org.eclipse.jface.text.DocumentRewriteSessionType;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentExtension4;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import io.github.htanwar922.eclipse.clangformat.Activator;
import io.github.htanwar922.eclipse.clangformat.core.ClangFormatRunner;
import io.github.htanwar922.eclipse.clangformat.core.ClangFormatRunner.FormatException;
import io.github.htanwar922.eclipse.clangformat.core.ClangFormatRunner.FormatResult;
import io.github.htanwar922.eclipse.clangformat.core.ClangFormatStyleResolver;
import io.github.htanwar922.eclipse.clangformat.preferences.PreferenceConstants;

/**
 * Bound to the "Format with clang-format" command. Works against any
 * ITextEditor (the CDT C/C++ editor included, but no CDT dependency is
 * required), formatting either the current selection or the whole file.
 */
public class FormatWithClangFormatHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IEditorPart editorPart = HandlerUtil.getActiveEditor(event);
        if (!(editorPart instanceof ITextEditor)) {
            return null;
        }
        ITextEditor editor = (ITextEditor) editorPart;

        IDocumentProvider provider = editor.getDocumentProvider();
        IEditorInput input = editor.getEditorInput();
        IDocument document = provider.getDocument(input);
        if (document == null) {
            return null;
        }

        ISelectionProvider selProvider = editor.getSite().getSelectionProvider();
        ISelection sel = (selProvider != null) ? selProvider.getSelection() : null;
        ITextSelection selection = (sel instanceof ITextSelection) ? (ITextSelection) sel : null;

        IPreferenceStore store = Activator.getDefault().getPreferenceStore();
        boolean wholeFileOnNoSelection = store.getBoolean(PreferenceConstants.FORMAT_WHOLE_FILE_WHEN_NO_SELECTION);

        Integer startLine = null;
        Integer endLine = null;
        if (selection != null && selection.getLength() > 0) {
            startLine = selection.getStartLine() + 1;
            endLine = selection.getEndLine() + 1;
        } else if (selection != null && !wholeFileOnNoSelection) {
            int line = selection.getStartLine() + 1;
            startLine = line;
            endLine = line;
        }

        // Save the cursor's current line (fallback) and exact character offset
        final int savedLine = (selection != null) ? selection.getStartLine() : 0;
        final Integer savedOffset = (selection != null) ? selection.getOffset() : null;
        final int savedLength = (selection != null) ? selection.getLength() : 0;

        // The exact number of characters AFTER our highlight range
        final int suffixLength = document.getLength() - (savedOffset + savedLength);

        // IEditorInput.getName() already returns just the file name (e.g. "Foo.c"),
        // which is all clang-format needs from -assume-filename for language detection.
        String fileNameHint = "source.c";
        String inputName = input.getName();
        if (inputName != null && !inputName.isEmpty()) {
            fileNameHint = inputName;
        }

        String execPath = store.getString(PreferenceConstants.CLANG_FORMAT_PATH);
        String formatStyle = store.getString(PreferenceConstants.CLANG_FORMAT_STYLE);
        String extraArgs = formatStyle + " " + store.getString(PreferenceConstants.CLANG_FORMAT_ARGS);

        if (execPath == null || execPath.trim().isEmpty()) {
            MessageDialog.openWarning(Display.getDefault().getActiveShell(),
                    "Clang-Format Not Configured",
                    "Please set the clang-format executable path in Window > Preferences > C/C++ >"
                            + " Code Style > Formatters > Clang-Format.");
            return null;
        }

        // Resolve the file's real on-disk directory (if it's a workspace resource)
        // and the workspace root, so we can search for a governing .clang-format
        // file ourselves rather than relying on clang-format's own search, which
        // is based on the spawned process's working directory, not the file's.
        IWorkspaceRoot wsRoot = ResourcesPlugin.getWorkspace().getRoot();
        File workspaceRootDir = (wsRoot.getLocation() != null) ? wsRoot.getLocation().toFile() : null;

        File fileDir = null;
        File projectDir = null;

        IFile workspaceFile = input.getAdapter(IFile.class);
        if (workspaceFile != null) {
            IPath location = workspaceFile.getLocation();
            if (location != null) {
                fileDir = location.toFile().getParentFile();
            }
            IProject project = workspaceFile.getProject();
            if (project != null && project.getLocation() != null) {
                projectDir = project.getLocation().toFile();
            }
        } else {
            // Fallback for external files (like FileStoreEditorInput) without needing extra dependencies
            try {
                URI uri = input.getAdapter(URI.class);
                if (uri == null) {
                    // Safely invoke getURI() dynamically if the adapter isn't registered
                    java.lang.reflect.Method method = input.getClass().getMethod("getURI");
                    uri = (URI) method.invoke(input);
                }

                if (uri != null && "file".equalsIgnoreCase(uri.getScheme())) {
                    fileDir = new File(uri).getParentFile();
                }
            } catch (Exception e) {
                // Ignore: This is a virtual in-memory file with no physical disk location
            }
        }

        File startDir = (fileDir != null) ? fileDir : workspaceRootDir;

        if (ClangFormatStyleResolver.usesFileStyle(extraArgs)) {
            File clangFormatFile = ClangFormatStyleResolver.findClangFormatFile(startDir, projectDir, workspaceRootDir);
            if (clangFormatFile == null) {
                MessageDialog.openWarning(Display.getDefault().getActiveShell(),
                        "No .clang-format File Found",
                        "No .clang-format file was found in this file's directory, any parent "
                                + "directory, the project's base directory, or the workspace root.\n\n"
                                + "Formatting was skipped to avoid applying an unintended default "
                                + "style. Add a .clang-format file, or set a different style "
                                + "(e.g. -style=LLVM) under Window > Preferences > C/C++ > Code Style >"
                                + " Formatters > Clang-Format > Formatting Style.");
                return null;
            }
            extraArgs = ClangFormatStyleResolver.withExplicitStyleFile(extraArgs, clangFormatFile);
            extraArgs += " -fallback-style=None";
        }

        String sourceText = document.get();
        Integer fStartLine = startLine;
        Integer fEndLine = endLine;
        String fFileNameHint = fileNameHint;
        String fExecPath = execPath;
        String fExtraArgs = extraArgs;
        File fWorkingDir = startDir;

        Job job = new Job("Running clang-format") {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    FormatResult result = ClangFormatRunner.format(
                            fExecPath, fExtraArgs, fFileNameHint, sourceText, fStartLine, fEndLine, fWorkingDir, savedOffset);

                    Display.getDefault().asyncExec(() -> {
                        if (!result.formattedText.equals(sourceText)) {
                            DocumentRewriteSession session = null;
                            IDocumentExtension4 doc4 = (document instanceof IDocumentExtension4)
                                    ? (IDocumentExtension4) document : null;

                            try {
                                if (doc4 != null) {
                                    // Group changes into 1 Undo step and prevent IDE UI freezes
                                    session = doc4.startRewriteSession(DocumentRewriteSessionType.SEQUENTIAL);
                                }

                                // =====================================================================
                                // SMART REPLACE: Find the exact diff so Ctrl+Z only highlights the change
                                // =====================================================================
                                String oldText = sourceText;
                                String newText = result.formattedText;

                                int prefix = 0;
                                int maxPrefix = Math.min(oldText.length(), newText.length());
                                while (prefix < maxPrefix && oldText.charAt(prefix) == newText.charAt(prefix)) {
                                    prefix++;
                                }

                                int suffix = 0;
                                int maxSuffix = Math.min(oldText.length() - prefix, newText.length() - prefix);
                                while (suffix < maxSuffix &&
                                       oldText.charAt(oldText.length() - 1 - suffix) == newText.charAt(newText.length() - 1 - suffix)) {
                                    suffix++;
                                }

                                int replaceOffset = prefix;
                                int replaceLength = oldText.length() - prefix - suffix;
                                String replacementText = newText.substring(prefix, newText.length() - suffix);

                                // Only replace the specific block that actually changed!
                                document.replace(replaceOffset, replaceLength, replacementText);
                                // =====================================================================

                            } catch (Exception e) {
                                // Fallback just in case the math fails
                                document.set(result.formattedText);
                            } finally {
                                if (doc4 != null && session != null) {
                                    doc4.stopRewriteSession(session);
                                }
                            }

                            // Restore the cursor position
                            if (selProvider != null) {
                                if (result.newCursorOffset >= 0) {
                                    int finalOffset = result.newCursorOffset;
                                    int finalLength = 0;

                                    if (savedLength > 0) {
                                        // Because the suffix wasn't formatted, its length is identical.
                                        // We just subtract it from the new doc length to find the new end!
                                        int newEndOffset = document.getLength() - suffixLength;
                                        finalLength = Math.max(0, newEndOffset - finalOffset);
                                    }

                                    // Final safety bounds to prevent any BadLocationExceptions
                                    finalOffset = Math.min(finalOffset, document.getLength());
                                    finalLength = Math.min(finalLength, document.getLength() - finalOffset);

                                    selProvider.setSelection(new TextSelection(finalOffset, finalLength));

                                } else {
                                    // Fallback to the old line-based approach if cursor tracking failed
                                    try {
                                        int safeLine = Math.min(savedLine, document.getNumberOfLines() - 1);
                                        int lineOffset = document.getLineOffset(safeLine);
                                        selProvider.setSelection(new TextSelection(lineOffset, 0));
                                    } catch (BadLocationException ignored) {}
                                }
                            }
                        }
                    });
                    return Status.OK_STATUS;
                } catch (FormatException e) {
                    Display.getDefault().asyncExec(() ->
                            MessageDialog.openError(Display.getDefault().getActiveShell(),
                                    "Clang-Format Error", e.getMessage()));
                    return Status.CANCEL_STATUS;
                }
            }
        };
        job.setUser(true);
        job.schedule();

        return null;
    }
}
