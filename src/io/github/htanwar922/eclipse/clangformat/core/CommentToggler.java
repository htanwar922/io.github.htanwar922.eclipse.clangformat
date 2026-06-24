package io.github.htanwar922.eclipse.clangformat.core;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;

/**
 * Toggles "//" line comments on the lines touched by a selection (or just
 * the current line, if there is no selection) - but unlike Eclipse's
 * built-in "Toggle Comment", the marker is never dropped at column 0.
 *
 * Rules:
 *
 * 1. Single line: "// " is inserted right after the leading whitespace,
 *    i.e. at the first non-whitespace character of that line.
 *
 * 2. Multiple lines: every non-blank selected line is commented at the
 *    SAME column - the minimum indentation (in characters) found among
 *    the selected non-blank lines. Lines indented deeper than that
 *    minimum keep their extra indentation, which now simply appears
 *    after the "// " marker - so the comment markers line up vertically
 *    at the block's outermost indentation level while the code's
 *    relative nesting is still visible.
 *
 * 3. Toggling back off strips exactly "//" plus one following space (if
 *    present) from each previously-commented line, which round-trips
 *    back to the original text exactly.
 *
 * 4. Blank (whitespace-only) lines are always left untouched, and are
 *    ignored when deciding direction (comment vs. uncomment) and when
 *    computing the minimum indentation.
 *
 * 5. If a selection mixes commented and uncommented lines, the action is
 *    "comment": every non-blank line gets a fresh "// " prepended at the
 *    shared minimum-indent column, INCLUDING lines that already start
 *    with "//" (so they end up double-marked, e.g. "// // foo();") -
 *    matching VS Code's behavior. Only when EVERY non-blank line is
 *    already commented does the action become "uncomment" instead.
 *    Because uncomment always strips exactly one "//" layer, toggling
 *    on a double-marked line restores its original single "//" rather
 *    than losing it.
 *
 * Indentation is measured in raw characters (spaces and tabs count as one
 * column each). This lines up correctly as long as a block uses a
 * consistent indentation style, which is the normal case for any single
 * file/selection.
 */
public final class CommentToggler {

    public static final String MARKER = "//";

    private CommentToggler() {
    }

    public static final class Result {
        public final int offset;
        public final int length;
        public final String text;

        public Result(int offset, int length, String text) {
            this.offset = offset;
            this.length = length;
            this.text = text;
        }
    }

    public static Result toggle(IDocument document, ITextSelection selection) throws BadLocationException {
        int numLines = document.getNumberOfLines();
        if (numLines == 0) {
            return null;
        }

        int startLine = clamp(selection.getStartLine(), 0, numLines - 1);
        int endLine = clamp(selection.getEndLine(), 0, numLines - 1);
        if (endLine < startLine) {
            endLine = startLine;
        }

        // JFace text selections that end exactly at the start of the next line
        // (e.g. Shift+Down selecting whole lines) report that next line as
        // endLine even though none of its characters are actually selected.
        // Exclude it so a trailing empty selection doesn't drag in one extra line.
        if (endLine > startLine) {
            int endLineOffset = document.getLineOffset(endLine);
            if (selection.getOffset() + selection.getLength() == endLineOffset) {
                endLine--;
            }
        }

        List<String> lines = new ArrayList<>();
        for (int l = startLine; l <= endLine; l++) {
            IRegion info = document.getLineInformation(l);
            lines.add(document.get(info.getOffset(), info.getLength()));
        }

        boolean hasNonBlank = false;
        boolean allCommented = true;
        int minIndent = Integer.MAX_VALUE;

        for (String line : lines) {
            int indent = leadingWhitespaceLength(line);
            if (indent == line.length()) {
                continue; // blank line - excluded from direction/alignment decisions
            }
            hasNonBlank = true;
            minIndent = Math.min(minIndent, indent);
            if (!isCommented(line, indent)) {
                allCommented = false;
            }
        }

        if (!hasNonBlank) {
            return null; // selection covers only blank lines; nothing to do
        }

        boolean uncomment = allCommented;

        StringBuilder rebuilt = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int indent = leadingWhitespaceLength(line);
            boolean blank = indent == line.length();

            String newLine;
            if (blank) {
                newLine = line;
            } else if (uncomment) {
                newLine = removeComment(line, indent);
            } else {
                newLine = insertCommentAt(line, minIndent);
            }
            rebuilt.append(newLine);

            if (i < lines.size() - 1) {
                String delim = document.getLineDelimiter(startLine + i);
                rebuilt.append(delim != null ? delim : "\n");
            }
        }

        IRegion firstInfo = document.getLineInformation(startLine);
        IRegion lastInfo = document.getLineInformation(endLine);
        int offset = firstInfo.getOffset();
        int length = (lastInfo.getOffset() + lastInfo.getLength()) - offset;

        return new Result(offset, length, rebuilt.toString());
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static int leadingWhitespaceLength(String line) {
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c != ' ' && c != '\t') {
                break;
            }
            i++;
        }
        return i;
    }

    private static boolean isCommented(String line, int indent) {
        return line.regionMatches(indent, MARKER, 0, MARKER.length());
    }

    private static String removeComment(String line, int indent) {
        String prefix = line.substring(0, indent);
        int afterMarker = indent + MARKER.length();
        String rest = line.substring(afterMarker);
        if (rest.startsWith(" ")) {
            rest = rest.substring(1);
        }
        return prefix + rest;
    }

    private static String insertCommentAt(String line, int column) {
        String before = line.substring(0, column);
        String after = line.substring(column);
        return before + MARKER + " " + after;
    }
}
