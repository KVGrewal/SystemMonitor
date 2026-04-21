package com.monitor.config;

import java.io.*;
import java.util.Properties;

/**
 * Immutable snapshot of every configurable value.
 * Built once at startup via {@link #load()}.
 */
public class AppConfig {

    public final double cpuWarnPercent;
    public final double ramWarnPercent;
    public final long   diskFreeWarnGb;
    public final int    refreshSeconds;
    public final File   logFile;

    private AppConfig(double cpuWarnPercent, double ramWarnPercent,
                      long diskFreeWarnGb, int refreshSeconds, File logFile) {
        this.cpuWarnPercent = cpuWarnPercent;
        this.ramWarnPercent = ramWarnPercent;
        this.diskFreeWarnGb = diskFreeWarnGb;
        this.refreshSeconds = refreshSeconds;
        this.logFile        = logFile;
    }

    /** Reads config.properties next to the JAR, or falls back to built-in defaults. */
    public static AppConfig load() {
        Properties props = new Properties();
        File configFile  = new File("config.properties");

        if (configFile.exists()) {
            try (FileInputStream in = new FileInputStream(configFile)) {
                props.load(in);
            } catch (IOException e) {
                System.err.println("[WARN] config.properties unreadable — using defaults.");
            }
        }

        double cpu     = Double.parseDouble(props.getProperty("cpu.warn",        "80"));
        double ram     = Double.parseDouble(props.getProperty("ram.warn",        "85"));
        long   disk    = Long.parseLong    (props.getProperty("disk.warn.gb",    "15"));
        int    refresh = Integer.parseInt  (props.getProperty("refresh.seconds", "2"));
        String logName = props.getProperty("log.file", "system_monitor_logs.txt");

        return new AppConfig(cpu, ram, disk, refresh, resolveLogPath(logName));
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static File resolveLogPath(String filename) {
        try {
            String jarFolder = new File(AppConfig.class
                .getProtectionDomain().getCodeSource()
                .getLocation().toURI()).getParent();
            return new File(jarFolder, filename);
        } catch (Exception e) {
            return new File(filename);
        }
    }
}
