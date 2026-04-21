package com.monitor.alert;

import com.monitor.config.AppConfig;
import com.monitor.logger.EventLogger;
import com.monitor.model.*;

import java.util.*;

/**
 * Evaluates every snapshot against configured thresholds.
 * Fires a log entry only on the transition (normal → alert) or (alert → normal),
 * not on every tick — keeps logs clean and meaningful.
 *
 * Maintains a small ring buffer of recent on-screen alert messages for the renderer.
 */
public class AlertManager {

    private static final int MAX_VISIBLE_ALERTS = 2;

    private final AppConfig   config;
    private final EventLogger logger;

    // ── Per-resource alert state ───────────────────────────────────────────
    private boolean cpuAlertFired = false;
    private boolean ramAlertFired = false;
    private final Map<String, Boolean> diskAlertFiredByMount = new HashMap<>();

    /** Ring buffer — last {@value MAX_VISIBLE_ALERTS} alert messages. */
    private final Deque<String> recentAlerts = new ArrayDeque<>();

    public AlertManager(AppConfig config, EventLogger logger) {
        this.config = config;
        this.logger = logger;
    }

    /**
     * Runs all threshold checks against the snapshot.
     *
     * @return list of human-readable warning strings to show on screen right now
     */
    public List<String> checkAndUpdate(SystemSnapshot snapshot,
                                       String hostname, String username) {
        List<String> activeWarnings = new ArrayList<>();
        checkCpu  (snapshot, hostname, username, activeWarnings);
        checkRam  (snapshot, hostname, username, activeWarnings);
        checkDisks(snapshot, hostname, username, activeWarnings);
        return activeWarnings;
    }

    public Deque<String> getRecentAlerts() {
        return recentAlerts;
    }

    // ── Individual checks ──────────────────────────────────────────────────

    private void checkCpu(SystemSnapshot snap, String host, String user,
                          List<String> warnings) {
        boolean overThreshold = snap.cpuUsagePercent >= config.cpuWarnPercent;

        if (overThreshold) {
            warnings.add("High CPU usage (" + Math.round(snap.cpuUsagePercent) + "%)");
            if (!cpuAlertFired) {
                String tempText = snap.cpuTempCelsius > 0
                    ? Math.round(snap.cpuTempCelsius) + "C" : "N/A";
                logger.write("WARN", host, user,
                    "CPU spiked to " + Math.round(snap.cpuUsagePercent) + "%"
                        + " -- threshold " + (int) config.cpuWarnPercent + "%"
                        + " | Temp: " + tempText
                        + " | Freq: " + String.format("%.2f", snap.currentFreqHz / 1e9) + " GHz");
                addToRing("WARN", "CPU spiked to " + Math.round(snap.cpuUsagePercent) + "%");
                cpuAlertFired = true;
            }
        } else if (cpuAlertFired) {
            logger.write("OK", host, user,
                "CPU back to normal: " + Math.round(snap.cpuUsagePercent) + "%");
            addToRing("OK", "CPU back to normal: " + Math.round(snap.cpuUsagePercent) + "%");
            cpuAlertFired = false;
        }
    }

    private void checkRam(SystemSnapshot snap, String host, String user,
                          List<String> warnings) {
        boolean overThreshold = snap.ramUsedPercent >= config.ramWarnPercent;

        if (overThreshold) {
            warnings.add("High RAM usage (" + Math.round(snap.ramUsedPercent) + "%)");
            if (!ramAlertFired) {
                logger.write("WARN", host, user,
                    "RAM spiked to "  + Math.round(snap.ramUsedPercent) + "%"
                        + " (" + toGb(snap.usedRamBytes) + " / " + toGb(snap.totalRamBytes) + " GB)"
                        + " -- threshold " + (int) config.ramWarnPercent + "%"
                        + " | Swap: " + toGb(snap.swapUsedBytes)
                        + " / "       + toGb(snap.swapTotalBytes) + " GB");
                addToRing("WARN", "RAM spiked to " + Math.round(snap.ramUsedPercent) + "%");
                ramAlertFired = true;
            }
        } else if (ramAlertFired) {
            logger.write("OK", host, user,
                "RAM back to normal: " + Math.round(snap.ramUsedPercent) + "%"
                    + " (" + toGb(snap.usedRamBytes) + " / " + toGb(snap.totalRamBytes) + " GB)");
            addToRing("OK", "RAM back to normal: " + Math.round(snap.ramUsedPercent) + "%");
            ramAlertFired = false;
        }
    }

    private void checkDisks(SystemSnapshot snap, String host, String user,
                            List<String> warnings) {
        for (DiskInfo drive : snap.drives) {
            boolean tooFull     = drive.freeGigabytes() <= config.diskFreeWarnGb;
            boolean alertFired  = diskAlertFiredByMount.getOrDefault(drive.mountPoint, false);

            if (tooFull)
                warnings.add("Disk low on " + drive.mountPoint
                    + " (" + drive.freeGigabytes() + " GB free)");

            if (tooFull && !alertFired) {
                logger.write("WARN", host, user,
                    "Disk critical on " + drive.mountPoint
                        + " [" + drive.driveType + "]"
                        + ": " + drive.freeGigabytes() + " GB free"
                        + " of " + drive.totalGigabytes() + " GB"
                        + " (" + Math.round(drive.usedPercent) + "% used)"
                        + " -- threshold " + config.diskFreeWarnGb + " GB");
                addToRing("WARN", "Disk low: " + drive.mountPoint
                    + " (" + drive.freeGigabytes() + " GB)");
                diskAlertFiredByMount.put(drive.mountPoint, true);

            } else if (!tooFull && alertFired) {
                logger.write("OK", host, user,
                    "Disk recovered on " + drive.mountPoint
                        + ": " + drive.freeGigabytes() + " GB free");
                addToRing("OK", "Disk recovered: " + drive.mountPoint);
                diskAlertFiredByMount.put(drive.mountPoint, false);
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private void addToRing(String level, String message) {
        if (recentAlerts.size() >= MAX_VISIBLE_ALERTS) recentAlerts.pollFirst();
        recentAlerts.addLast("[" + level + "] " + message);
    }

    private static long toGb(long bytes) {
        return bytes / (1024L * 1024 * 1024);
    }
}
