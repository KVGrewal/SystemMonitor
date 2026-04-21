package com.monitor.display;

import com.monitor.config.AppConfig;
import com.monitor.model.*;

import java.util.*;

import static com.monitor.display.ColorPalette.*;

/**
 * Knows how to draw the full terminal dashboard.
 *
 * Receives a {@link SystemSnapshot} each tick and writes directly to stdout.
 * Owns the CPU trend history because it is purely a display concern.
 *
 * All formatting helpers are private — callers only touch {@link #render}.
 */
public class DashboardRenderer {

    private static final int TOTAL_WIDTH      = 98;
    private static final int LEFT_COLUMN_WIDTH = 48;
    private static final int TREND_HISTORY     = 60;   // seconds of CPU trend

    // ── One-time static labels set in constructor ──────────────────────────
    private final AppConfig config;
    private final String    hostname;
    private final String    username;
    private final String    osLabel;
    private final String    bootTime;
    private final String    cpuModelName;
    private final String    gpuModelName;
    private final String    adapterLabel;
    private final String    ipAddress;
    private final String    macAddress;
    private final long      installedRamGb;
    private final long      maxCpuFreqHz;

    /** Rolling 60-second CPU trend — lives here because it's a display concern. */
    private final Deque<Double> cpuTrend = new ArrayDeque<>();

    public DashboardRenderer(
        AppConfig config,
        String hostname,     String username,
        String osLabel,      String bootTime,
        String cpuModelName, String gpuModelName,
        String adapterLabel, String ipAddress, String macAddress,
        long installedRamGb, long maxCpuFreqHz) {

        this.config         = config;
        this.hostname       = hostname;
        this.username       = username;
        this.osLabel        = osLabel;
        this.bootTime       = bootTime;
        this.cpuModelName   = cpuModelName;
        this.gpuModelName   = gpuModelName;
        this.adapterLabel   = adapterLabel;
        this.ipAddress      = ipAddress;
        this.macAddress     = macAddress;
        this.installedRamGb = installedRamGb;
        this.maxCpuFreqHz   = maxCpuFreqHz;
    }

    // ── Public API ─────────────────────────────────────────────────────────

    public void render(SystemSnapshot snapshot,
                       List<String>   activeWarnings,
                       Deque<String>  recentAlerts,
                       long           uptimeSeconds,
                       String         logFilePath) {
        clearScreen();
        pushCpuTrend(snapshot.cpuUsagePercent);

        drawHeader();
        drawHealthBar(activeWarnings, uptimeSeconds);
        drawSystemAndNetwork(snapshot, uptimeSeconds);
        drawCpuAndCores(snapshot);
        drawMemoryAndStorage(snapshot);
        drawTopProcesses(snapshot);
        drawRecentAlerts(recentAlerts);
        drawFooter(logFilePath);
    }

    // ── Section renderers ──────────────────────────────────────────────────

    private void drawHeader() {
        drawHorizontalLine('\u2550');
        printCentered(colorize(BOLD + CYAN, "SM DASHBOARD v2.0"));
        printCentered(colorize(DIM,
            "Platform: " + osLabel
                + "  |  Host: " + hostname
                + "  |  User: " + username
                + "  |  Refresh: " + config.refreshSeconds + "s"));
        drawHorizontalLine('\u2550');
    }

    private void drawHealthBar(List<String> warnings, long uptimeSeconds) {
        String statusBadge = warnings.isEmpty()
            ? colorize(GREEN + BOLD, "[*] HEALTHY")
            : colorize(RED   + BOLD, "[!] ATTENTION REQUIRED");
        String alertCount  = warnings.isEmpty()
            ? colorize(GREEN, "0 active")
            : colorize(RED,   warnings.size() + " active");

        printCentered("HEALTH " + statusBadge
            + "   UPTIME " + colorize(GREEN, formatUptime(uptimeSeconds))
            + "   ALERTS " + alertCount);

        for (String warning : warnings)
            printCentered(colorize(YELLOW, "  ! " + warning));

        drawHorizontalLine('\u2500');
    }

    private void drawSystemAndNetwork(SystemSnapshot snap, long uptimeSeconds) {
        printTwoColumns(colorize(CYAN, "[ SYSTEM INFO ]"), colorize(CYAN, "[ NETWORK ]"));
        printTwoColumns("OS        : " + truncate(osLabel),    "Adapter   : " + truncate(adapterLabel));
        printTwoColumns("Hostname  : " + hostname,             "IP        : " + ipAddress);
        printTwoColumns("User      : " + username,             "MAC       : " + macAddress);
        printTwoColumns("Boot time : " + colorize(DIM, bootTime),
            "v Down    : " + colorize(GREEN, formatSpeed(snap.downloadKbps)));
        printTwoColumns("Uptime    : " + colorize(GREEN, formatUptime(uptimeSeconds)),
            "^ Up      : " + colorize(GREEN, formatSpeed(snap.uploadKbps)));
        drawHorizontalLine('\u2500');
    }

    private void drawCpuAndCores(SystemSnapshot snap) {
        printTwoColumns(colorize(CYAN, "[ CPU ]"), colorize(CYAN, "[ CPU CORES ]"));

        String usageColor = snap.cpuUsagePercent >= config.cpuWarnPercent ? RED
            : snap.cpuUsagePercent > 50 ? YELLOW : GREEN;
        String tempColor  = snap.cpuTempCelsius <= 0 ? DIM
            : snap.cpuTempCelsius >= 80 ? RED
            : snap.cpuTempCelsius >= 60 ? YELLOW : GREEN;
        String tempText   = snap.cpuTempCelsius <= 0 ? "N/A"
            : Math.round(snap.cpuTempCelsius) + " C";

        List<String> leftSide = new ArrayList<>();
        leftSide.add("Model     : " + truncate(cpuModelName));
        leftSide.add("GPU       : " + truncate(gpuModelName));
        leftSide.add("Usage     : " + progressBar(snap.cpuUsagePercent)
            + " " + colorize(usageColor, Math.round(snap.cpuUsagePercent) + "%"));
        leftSide.add("Freq      : "
            + String.format("%.2f", snap.currentFreqHz / 1e9) + " / "
            + String.format("%.2f", maxCpuFreqHz        / 1e9) + " GHz");
        leftSide.add("Temp      : " + colorize(tempColor, tempText));
        leftSide.add("Trend 60s : min " + colorize(GREEN,  trendMin() + "%")
            + " | avg " + colorize(YELLOW, trendAvg() + "%")
            + " | max " + colorize(RED,    trendMax() + "%"));

        List<String> rightSide = buildCoreRows(snap.perCorePercents);

        int rows = Math.max(leftSide.size(), rightSide.size());
        for (int i = 0; i < rows; i++)
            printTwoColumns(
                i < leftSide.size()  ? leftSide.get(i)  : "",
                i < rightSide.size() ? rightSide.get(i) : "");

        drawHorizontalLine('\u2500');
    }

    private void drawMemoryAndStorage(SystemSnapshot snap) {
        printTwoColumns(colorize(CYAN, "[ MEMORY ]"), colorize(CYAN, "[ STORAGE ]"));

        String ramColor = snap.ramUsedPercent >= config.ramWarnPercent ? RED
            : snap.ramUsedPercent > 60 ? YELLOW : GREEN;

        List<String> memRows = new ArrayList<>();
        memRows.add("RAM       : " + colorize(ramColor,
            toGb(snap.usedRamBytes) + " GB / " + installedRamGb + " GB"
                + "  (" + Math.round(snap.ramUsedPercent) + "%)"));
        memRows.add(progressBar(snap.ramUsedPercent));
        memRows.add("Cached: " + toGb(snap.cachedBytes) + " GB"
            + "   Swap: " + toGb(snap.swapUsedBytes) + " GB"
            + " / " + toGb(snap.swapTotalBytes) + " GB");

        List<String> diskRows = buildDiskRows(snap.drives);

        int rows = Math.max(memRows.size(), diskRows.size());
        for (int i = 0; i < rows; i++)
            printTwoColumns(
                i < memRows.size()  ? memRows.get(i)  : "",
                i < diskRows.size() ? diskRows.get(i) : "");

        drawHorizontalLine('\u2500');
    }

    private void drawTopProcesses(SystemSnapshot snap) {
        System.out.println(colorize(CYAN, "[ TOP PROCESSES ]"));
        System.out.printf("   %-8s %-26s %-12s %-12s %-10s%n",
            colorize(DIM, "PID"),   colorize(DIM, "NAME"),
            colorize(DIM, "CPU %"), colorize(DIM, "MEMORY"),
            colorize(DIM, "STATUS"));

        for (ProcessInfo proc : snap.topProcesses) {
            String cpuColor = proc.cpuPercent > 20 ? RED
                : proc.cpuPercent > 10 ? YELLOW : GREEN;
            System.out.printf("   %-8d %-26s %s%-9.1f%s %-12s %s%n",
                proc.pid,
                clipText(proc.name, 25),
                cpuColor, proc.cpuPercent, RESET,
                proc.memoryMb + " MB",
                colorize(GREEN, proc.state));
        }
        drawHorizontalLine('\u2500');
    }

    private void drawRecentAlerts(Deque<String> recentAlerts) {
        System.out.println(colorize(CYAN, "[ RECENT ALERTS ]"));
        if (recentAlerts.isEmpty()) {
            System.out.println("   " + colorize(DIM, "No alerts yet."));
        } else {
            for (String entry : recentAlerts)
                System.out.println("   " + entry);
        }
    }

    private void drawFooter(String logFilePath) {
        drawHorizontalLine('\u2550');
        printCentered(colorize(DIM,
            "Log -> " + logFilePath + "   |   Press Q + Enter to exit"));
    }

    // ── Row builders ───────────────────────────────────────────────────────

    private List<String> buildCoreRows(double[] corePercents) {
        List<String> rows = new ArrayList<>();
        int half = (corePercents.length + 1) / 2;
        for (int i = 0; i < half; i++) {
            String row = renderCoreEntry(i, corePercents);
            if (i + half < corePercents.length)
                row += "   " + renderCoreEntry(i + half, corePercents);
            rows.add(row);
        }
        return rows;
    }

    private String renderCoreEntry(int index, double[] corePercents) {
        double pct    = corePercents[index] * 100;
        String color  = pct > 80 ? RED : pct > 40 ? YELLOW : GREEN;
        int    filled = (int) Math.min(6, pct / (100.0 / 6));
        String bar    = "[" + "#".repeat(filled) + "-".repeat(6 - filled) + "]";
        return String.format("C%-2d%s%s%3d%%%s", index, bar, color, Math.round(pct), RESET);
    }

    private List<String> buildDiskRows(List<DiskInfo> drives) {
        List<String> rows = new ArrayList<>();
        for (DiskInfo drive : drives) {
            String diskColor = drive.freeGigabytes() <= config.diskFreeWarnGb ? RED
                : drive.usedPercent > 80 ? YELLOW : GREEN;
            rows.add(colorize(DIM, drive.mountPoint + " [" + drive.driveType + "]"));
            rows.add(progressBar(drive.usedPercent)
                + " " + colorize(diskColor, Math.round(drive.usedPercent) + "%")
                + colorize(DIM, "  " + drive.usedGigabytes()
                + " / " + drive.totalGigabytes() + " GB"));
        }
        return rows;
    }

    // ── CPU trend ──────────────────────────────────────────────────────────

    private void pushCpuTrend(double value) {
        if (cpuTrend.size() >= TREND_HISTORY) cpuTrend.pollFirst();
        cpuTrend.addLast(value);
    }

    private long trendMin() {
        return cpuTrend.isEmpty() ? 0 : Math.round(Collections.min(cpuTrend));
    }

    private long trendMax() {
        return cpuTrend.isEmpty() ? 0 : Math.round(Collections.max(cpuTrend));
    }

    private long trendAvg() {
        return Math.round(cpuTrend.stream().mapToDouble(v -> v).average().orElse(0));
    }

    // ── Format helpers ─────────────────────────────────────────────────────

    private static String progressBar(double percent) {
        int filled = (int) Math.min(10, percent / 10);
        return "[" + "#".repeat(filled) + "-".repeat(10 - filled) + "]";
    }

    private static String formatUptime(long totalSeconds) {
        long days    = totalSeconds / 86400;
        long hours   = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600)  / 60;
        return days > 0
            ? days + "d " + hours + "h " + minutes + "m"
            : hours + "h " + minutes + "m";
    }

    private static String formatSpeed(long kbps) {
        return kbps >= 1024
            ? String.format("%.1f MB/s", kbps / 1024.0)
            : kbps + " KB/s";
    }

    private static String truncate(String text) {
        if (text == null) return "N/A";
        int maxLen = LEFT_COLUMN_WIDTH - 14;
        return text.length() > maxLen ? text.substring(0, maxLen - 3) + "..." : text;
    }

    private static String clipText(String text, int maxLength) {
        if (text == null) return "N/A";
        return text.length() > maxLength ? text.substring(0, maxLength - 3) + "..." : text;
    }

    private static long toGb(long bytes) {
        return bytes / (1024L * 1024 * 1024);
    }

    // ── Layout helpers ─────────────────────────────────────────────────────

    private static void printTwoColumns(String left, String right) {
        int visibleLen = removeAnsiCodes(left).length();
        System.out.println(
            left + " ".repeat(Math.max(1, LEFT_COLUMN_WIDTH - visibleLen)) + right);
    }

    private static void printCentered(String text) {
        int visibleLen = removeAnsiCodes(text).length();
        System.out.println(
            " ".repeat(Math.max(0, (TOTAL_WIDTH - visibleLen) / 2)) + text);
    }

    private static void drawHorizontalLine(char ch) {
        System.out.println(String.valueOf(ch).repeat(TOTAL_WIDTH));
    }

    private static void clearScreen() {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb = os.contains("win")
                ? new ProcessBuilder("cmd", "/c", "cls")
                : new ProcessBuilder("clear");
            pb.inheritIO().start().waitFor();
        } catch (Exception e) {
            System.out.println("\n".repeat(50));
        }
    }
}
