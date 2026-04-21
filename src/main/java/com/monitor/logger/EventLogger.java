package com.monitor.logger;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Append-only logger that writes structured lines to a single flat file.
 *
 * Format: [2025-04-20 14:33:01] [WARN] [HOST=mypc USER=alice] CPU spiked to 91%
 */
public class EventLogger {

    private final File                logFile;
    private final DateTimeFormatter   timestampFormat;

    public EventLogger(File logFile) {
        this.logFile         = logFile;
        this.timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        createFileIfMissing();
    }

    /**
     * Appends one line to the log file.
     *
     * @param level    "INFO", "WARN", or "OK"
     * @param hostname machine hostname
     * @param username current OS user
     * @param message  free-form description of the event
     */
    public void write(String level, String hostname, String username, String message) {
        String timestamp = LocalDateTime.now().format(timestampFormat);
        String entry = "[" + timestamp + "] ["
            + String.format("%-4s", level) + "] "
            + "[HOST=" + hostname + " USER=" + username + "] "
            + message;

        try (PrintWriter writer = new PrintWriter(
            new BufferedWriter(new FileWriter(logFile, true)))) {
            writer.println(entry);
        } catch (IOException ignored) {
            // best-effort; a monitoring tool should never crash over logging
        }
    }

    public File getLogFile() {
        return logFile;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void createFileIfMissing() {
        try {
            if (!logFile.exists()) logFile.createNewFile();
        } catch (IOException ignored) {}
    }
}
