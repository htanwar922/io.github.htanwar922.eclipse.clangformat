package io.github.htanwar922.eclipse.clangformat.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates the ".clang-format" file that governs a source file, by walking
 * up from the file's own directory through every parent, up to and
 * including the Eclipse workspace root - the same nearest-wins search
 * clang-format itself does for "-style=file", but resolved explicitly so
 * it doesn't depend on the spawned process's working directory.
 */
public final class ClangFormatStyleResolver {

    private static final String STYLE_PREFIX = "-style=";
    private static final String CLANG_FORMAT_FILE_NAME = ".clang-format";

    private ClangFormatStyleResolver() {
    }

    /**
     * @param startDir      directory to start searching from (typically the
     *                      edited file's own parent directory); may be null
     * @param workspaceRoot the Eclipse workspace root directory; the search
     *                      always also checks this directory even if
     *                      startDir lies outside it; may be null
     * @return the nearest .clang-format file found, or null if none exists
     *         anywhere between startDir and workspaceRoot
     */
    public static File findClangFormatFile(File startDir, File workspaceRoot) {
        File start = canonical(startDir);
        File boundary = canonical(workspaceRoot);

        List<File> toCheck = new ArrayList<>();
        boolean startUnderBoundary = boundary != null && start != null && isAncestor(boundary, start);

        if (startUnderBoundary) {
            File dir = start;
            while (dir != null) {
                toCheck.add(dir);
                if (dir.equals(boundary)) {
                    break;
                }
                dir = dir.getParentFile();
            }
        } else if (start != null) {
            toCheck.add(start);
        }

        if (boundary != null && !toCheck.contains(boundary)) {
            toCheck.add(boundary);
        }

        for (File dir : toCheck) {
            File candidate = new File(dir, CLANG_FORMAT_FILE_NAME);
            if (candidate.isFile()) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * @return true if, given these extra CLI arguments, clang-format will look
     *         for a .clang-format file (either because -style=file[:path] was
     *         passed explicitly, or because no -style was passed at all - in
     *         which case clang-format's own default is "file").
     */
    public static boolean usesFileStyle(String extraArgs) {
        String value = extractStyleValue(extraArgs);
        if (value == null) {
            return true;
        }
        return value.equals("file") || value.startsWith("file:");
    }

    /**
     * Returns a copy of extraArgs with the -style= token replaced (or, if
     * absent, appended) so it points explicitly at clangFormatFile.
     */
    public static String withExplicitStyleFile(String extraArgs, File clangFormatFile) {
        String replacement = STYLE_PREFIX + "file:" + clangFormatFile.getAbsolutePath();
        if (extraArgs == null || extraArgs.trim().isEmpty()) {
            return replacement;
        }
        StringBuilder out = new StringBuilder();
        boolean replaced = false;
        for (String token : extraArgs.trim().split("\\s+")) {
            if (out.length() > 0) {
                out.append(' ');
            }
            if (token.startsWith(STYLE_PREFIX)) {
                out.append(replacement);
                replaced = true;
            } else {
                out.append(token);
            }
        }
        if (!replaced) {
            out.append(' ').append(replacement);
        }
        return out.toString();
    }

    private static String extractStyleValue(String extraArgs) {
        if (extraArgs == null) {
            return null;
        }
        for (String token : extraArgs.trim().split("\\s+")) {
            if (token.startsWith(STYLE_PREFIX)) {
                return token.substring(STYLE_PREFIX.length());
            }
        }
        return null;
    }

    private static boolean isAncestor(File ancestor, File descendant) {
        Path ancestorPath = ancestor.toPath();
        Path descendantPath = descendant.toPath();
        return descendantPath.startsWith(ancestorPath);
    }

    private static File canonical(File f) {
        if (f == null) {
            return null;
        }
        try {
            return f.getCanonicalFile();
        } catch (IOException e) {
            return f.getAbsoluteFile();
        }
    }
}
