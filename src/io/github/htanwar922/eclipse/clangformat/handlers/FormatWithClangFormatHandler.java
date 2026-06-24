package io.github.htanwar922.eclipse.clangformat.handlers;

import java.io.File;

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
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
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

        // IEditorInput.getName() already returns just the file name (e.g. "Foo.c"),
        // which is all clang-format needs from -assume-filename for language detection.
        String fileNameHint = "source.c";
        String inputName = input.getName();
        if (inputName != null && !inputName.isEmpty()) {
            fileNameHint = inputName;
        }

        String execPath = store.getString(PreferenceConstants.CLANG_FORMAT_PATH);
        String extraArgs = store.getString(PreferenceConstants.CLANG_FORMAT_ARGS);

        if (execPath == null || execPath.trim().isEmpty()) {
            MessageDialog.openWarning(Display.getDefault().getActiveShell(),
                    "Clang-Format Not Configured",
                    "Please set the clang-format executable path in Window > Preferences > Clang-Format.");
            return null;
        }

        // Resolve the file's real on-disk directory (if it's a workspace resource)
        // and the workspace root, so we can search for a governing .clang-format
        // file ourselves rather than relying on clang-format's own search, which
        // is based on the spawned process's working directory, not the file's.
        IWorkspaceRoot wsRoot = ResourcesPlugin.getWorkspace().getRoot();
        File workspaceRootDir = (wsRoot.getLocation() != null) ? wsRoot.getLocation().toFile() : null;

        File startDir = workspaceRootDir;
        File projectDir = null;
        IFile workspaceFile = input.getAdapter(IFile.class);
        if (workspaceFile != null) {
            IPath location = workspaceFile.getLocation();
            if (location != null) {
                startDir = location.toFile().getParentFile();
            }
            IProject project = workspaceFile.getProject();
            if (project != null && project.getLocation() != null) {
                projectDir = project.getLocation().toFile();
            }
        }

        if (ClangFormatStyleResolver.usesFileStyle(extraArgs)) {
            File clangFormatFile = ClangFormatStyleResolver.findClangFormatFile(startDir, projectDir, workspaceRootDir);
            if (clangFormatFile == null) {
                MessageDialog.openWarning(Display.getDefault().getActiveShell(),
                        "No .clang-format File Found",
                        "No .clang-format file was found in this file's directory, any parent "
                                + "directory, the project's base directory, or the workspace root.\n\n"
                                + "Formatting was skipped to avoid applying an unintended default "
                                + "style. Add a .clang-format file, or set an explicit style "
                                + "(e.g. -style=LLVM) under Window > Preferences > Clang-Format > "
                                + "Additional arguments.");
                return null;
            }
            extraArgs = ClangFormatStyleResolver.withExplicitStyleFile(extraArgs, clangFormatFile);
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
                    String formatted = ClangFormatRunner.format(
                            fExecPath, fExtraArgs, fFileNameHint, sourceText, fStartLine, fEndLine, fWorkingDir);
                    Display.getDefault().asyncExec(() -> {
                        if (!formatted.equals(sourceText)) {
                            document.set(formatted);
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
