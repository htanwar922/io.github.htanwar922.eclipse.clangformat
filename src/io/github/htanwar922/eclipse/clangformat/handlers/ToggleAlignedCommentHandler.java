package io.github.htanwar922.eclipse.clangformat.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

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

        IDocumentProvider provider = editor.getDocumentProvider();
        IDocument document = provider.getDocument(editor.getEditorInput());
        if (document == null) {
            return null;
        }

        ISelectionProvider selProvider = editor.getSite().getSelectionProvider();
        ISelection sel = (selProvider != null) ? selProvider.getSelection() : null;
        if (!(sel instanceof ITextSelection)) {
            return null;
        }
        ITextSelection selection = (ITextSelection) sel;

        try {
            Result result = CommentToggler.toggle(document, selection);
            if (result == null) {
                return null;
            }
            document.replace(result.offset, result.length, result.text);
            selProvider.setSelection(new TextSelection(document, result.offset, result.text.length()));
        } catch (BadLocationException e) {
            throw new ExecutionException("Failed to toggle line comment", e);
        }
        return null;
    }
}
