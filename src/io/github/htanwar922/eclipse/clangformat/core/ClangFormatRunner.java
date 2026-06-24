package io.github.htanwar922.eclipse.clangformat.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the configured clang-format executable as an external process,
 * feeding it the document text on stdin and reading the formatted result
 * back from stdout.
 */
public final class ClangFormatRunner {

    private ClangFormatRunner() {
    }

    public static class FormatException extends Exception {
        private static final long serialVersionUID = 1L;

        public FormatException(String message) {
            super(message);
        }
    }

    /**
     * @param executablePath   path to (or bare name of) the clang-format binary
     * @param extraArgs        extra space-separated CLI arguments, e.g. "-style=file"
     * @param fileNameHint     a file name (just needs the right extension) so clang-format
     *                         can infer the language via -assume-filename
     * @param sourceText       the full current document text
     * @param startLine1Based  1-based start line to restrict formatting to (nullable)
     * @param endLine1Based    1-based end line to restrict formatting to (nullable)
     * @param workingDirectory directory the process should run in, e.g. so any
     *                         relative paths clang-format resolves on its own
     *                         behave the same way they would from the command
     *                         line in that directory (nullable)
     * @return the full, reformatted document text
     */
    public static String format(String executablePath, String extraArgs, String fileNameHint,
            String sourceText, Integer startLine1Based, Integer endLine1Based, File workingDirectory)
            throws FormatException {

        List<String> command = new ArrayList<>();
        command.add(executablePath);

        if (extraArgs != null && !extraArgs.trim().isEmpty()) {
            for (String arg : extraArgs.trim().split("\\s+")) {
                command.add(arg);
            }
        }
        if (fileNameHint != null && !fileNameHint.isEmpty()) {
            command.add("-assume-filename=" + fileNameHint);
        }
        if (startLine1Based != null && endLine1Based != null) {
            command.add("-lines=" + startLine1Based + ":" + endLine1Based);
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        if (workingDirectory != null && workingDirectory.isDirectory()) {
            pb.directory(workingDirectory);
        }
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new FormatException("Could not launch clang-format at '" + executablePath
                    + "'. Check the path in Window > Preferences > Clang-Format.\n" + e.getMessage());
        }

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread stdoutReader = new Thread(() -> readStream(process.getInputStream(), stdout), "clang-format-stdout");
        Thread stderrReader = new Thread(() -> readStream(process.getErrorStream(), stderr), "clang-format-stderr");
        stdoutReader.start();
        stderrReader.start();

        try (OutputStream os = process.getOutputStream()) {
            os.write(sourceText.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // If clang-format exits early (e.g. bad args) the pipe may already be closed;
            // the exit code / stderr captured below will explain the failure.
        }

        int exitCode;
        try {
            stdoutReader.join();
            stderrReader.join();
            exitCode = process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FormatException("Formatting was interrupted.");
        }

        if (exitCode != 0) {
            throw new FormatException("clang-format exited with code " + exitCode
                    + (stderr.length() > 0 ? ":\n" + stderr : ""));
        }
        if (stdout.length() == 0) {
            throw new FormatException("clang-format produced no output."
                    + (stderr.length() > 0 ? "\n" + stderr : ""));
        }
        return stdout.toString();
    }

    private static void readStream(InputStream in, StringBuilder out) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) != -1) {
                out.append(buf, 0, n);
            }
        } catch (IOException ignored) {
            // process pipe closed; whatever was read so far is kept
        }
    }
}
