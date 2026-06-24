package io.github.htanwar922.eclipse.clangformat.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRewriteTarget;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.ITextEditorExtension2;

import io.github.htanwar922.eclipse.clangformat.core.CommentToggler;
import io.github.htanwar922.eclipse.clangformat.core.CommentToggler.Result;

/**
 * Bound to the "Toggle Aligned Line Comment" command. See
 * {@link CommentToggler} for the actual alignment rules.
 */
public class ToggleAlignedCommentHandler extends AbstractHandler {

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException {
        IEditorPart editorPart = HandlerUtil.getActiveEditor(event);
        if (!(editorPart instanceof ITextEditor)) {
            return null;
        }
        ITextEditor editor = (ITextEditor) editorPart;

        // 1. Validate that the editor is editable and not out-of-sync
        if (!editor.isEditable()) {
            return null;
        }

        if (editor instanceof ITextEditorExtension2) {
            if (!((ITextEditorExtension2) editor).validateEditorInputState()) {
                return null;
            }
        }

        IDocumentProvider provider = editor.getDocumentProvider();        IDocument document = provider.getDocument(editor.getEditorInput());
        if (document == null) {
            return null;
        }

        ISelectionProvider selProvider = editor.getSelectionProvider();
        ISelection sel = (selProvider != null) ? selProvider.getSelection() : null;
        if (!(sel instanceof ITextSelection)) {
            return null;
        }
        ITextSelection selection = (ITextSelection) sel;

        // 2. Fetch the rewrite target to manage UI redraws and Undo history
        IRewriteTarget rewriteTarget = editor.getAdapter(IRewriteTarget.class);

        try {
            Result result = CommentToggler.toggle(document, selection);
            if (result == null) {
                return null;
            }

            // Begin compound change: tells Eclipse to group the upcoming edits
            if (rewriteTarget != null) {
                rewriteTarget.beginCompoundChange();
            }

            // Modifying the document
            document.replace(result.offset, result.length, result.text);

            // 3. Use the editor's native selectAndReveal instead of forcing a TextSelection
            editor.selectAndReveal(result.offset, result.text.length());

        } catch (BadLocationException e) {
            throw new ExecutionException("Failed to toggle line comment", e);
        } finally {
            // Close compound change: ensures the UI refreshes and Undo is recorded cleanly
            if (rewriteTarget != null) {
                rewriteTarget.endCompoundChange();
            }
        }

        return null;
    }
}
