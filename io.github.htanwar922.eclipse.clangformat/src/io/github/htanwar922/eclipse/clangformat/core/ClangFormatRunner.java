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

    public static class FormatResult {
	public final String formattedText;
	public final int newCursorOffset;

	public FormatResult(String formattedText, int newCursorOffset) {
	    this.formattedText = formattedText;
	    this.newCursorOffset = newCursorOffset;
	}
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
     * @param cursorOffset     Format range starting at this byte offset (nullable)
     * @return the full, reformatted document text
     */
    public static FormatResult format(String executablePath, String extraArgs, String fileNameHint, String sourceText,
	    Integer startLine1Based, Integer endLine1Based, File workingDirectory, Integer cursorOffset)
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
        if (cursorOffset != null) {
            command.add("-cursor=" + cursorOffset);
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
                    + "'. Check the path in Window > Preferences > C/C++ >"
                    + " Code Style > Formatters > Clang-Format.\n" + e.getMessage());
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
            // Wait a maximum of 10 seconds for clang-format to finish
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                throw new FormatException("clang-format timed out after 10 seconds and was terminated.");
            }

            stdoutReader.join(1000);
            stderrReader.join(1000);
            exitCode = process.exitValue();
        } catch (InterruptedException e) {
            process.destroyForcibly();
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

        String stdoutStr = stdout.toString();
        int newCursor = -1;
        String finalFormattedText = stdoutStr;

        if (cursorOffset != null) {
            // Clang-format puts the JSON object on the very first line
            int firstNewline = stdoutStr.indexOf('\n');
            if (firstNewline != -1) {
            String firstLine = stdoutStr.substring(0, firstNewline).trim();

            if (firstLine.startsWith("{") && firstLine.contains("\"Cursor\"")) {
                // Extract the integer from {"Cursor": 123, "IncompleteFormat": false}
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"Cursor\"\\s*:\\s*(\\d+)")
                    .matcher(firstLine);
                if (m.find()) {
                newCursor = Integer.parseInt(m.group(1));
                }
                // The actual code is everything after that first newline
                finalFormattedText = stdoutStr.substring(firstNewline + 1);
            }
            }
        }

        return new FormatResult(finalFormattedText, newCursor);
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
