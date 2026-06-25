package io.github.htanwar922.eclipse.clangformat.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Locates the ".clang-format" file that governs a source file. Search
 * order: the file's own directory and every parent directory above it,
 * then (if not already covered by that walk) the file's project base
 * directory, then (if still not covered) the Eclipse workspace root -
 * the nearest-wins search clang-format itself does for "-style=file",
 * but resolved explicitly so it doesn't depend on the spawned process's
 * working directory, and resilient to linked resources or projects that
 * live outside the workspace root.
 */
public final class ClangFormatStyleResolver {

    private static final String STYLE_PREFIX = "-style=";
    private static final String CLANG_FORMAT_FILE_NAME = ".clang-format";

    private ClangFormatStyleResolver() {
    }

    /**
     * @param startDir      directory to start searching from (typically the
     *                      edited file's own parent directory); may be null
     * @param projectDir    the base directory of the file's enclosing
     *                      project; always checked explicitly even if
     *                      startDir doesn't happen to be nested under it
     *                      (e.g. linked resources); may be null
     * @param workspaceRoot the Eclipse workspace root directory; always
     *                      checked explicitly too, even if the project
     *                      lives outside it; may be null
     * @return the nearest .clang-format file found, searching in order:
     *         startDir and its ancestors (up to whichever of projectDir /
     *         workspaceRoot contains it), then projectDir itself, then
     *         workspaceRoot itself - or null if none exists anywhere in
     *         that chain
     */
    public static File findClangFormatFile(File startDir, File projectDir, File workspaceRoot) {
        File start = canonical(startDir);
        File project = canonical(projectDir);
        File workspace = canonical(workspaceRoot);

        List<File> toCheck = new ArrayList<>();

        File boundary = null;
        if (workspace != null && start != null && isAncestor(workspace, start)) {
            boundary = workspace;
        } else if (project != null && start != null && isAncestor(project, start)) {
            boundary = project;
        }

        if (start != null) {
            if (boundary != null) {
                File dir = start;
                while (dir != null) {
                    toCheck.add(dir);
                    if (dir.equals(boundary)) {
                        break;
                    }
                    dir = dir.getParentFile();
                }
            } else {
                toCheck.add(start);
            }
        }

        if (project != null && !toCheck.contains(project)) {
            toCheck.add(project);
        }
        if (workspace != null && !toCheck.contains(workspace)) {
            toCheck.add(workspace);
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
