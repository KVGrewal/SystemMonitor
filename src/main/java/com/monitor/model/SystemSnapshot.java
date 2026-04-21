package com.monitor.model;

import java.util.List;

/**
 * Immutable snapshot of every metric gathered during one poll tick.
 * Passed around by reference — nothing mutates it after construction.
 */
public class SystemSnapshot {

    // ── CPU ───────────────────────────────────────────────────────────────
    public final double   cpuUsagePercent;
    public final double[] perCorePercents;
    public final double   cpuTempCelsius;
    public final long     currentFreqHz;
    public final long     maxFreqHz;

    // ── Memory ────────────────────────────────────────────────────────────
    public final long   usedRamBytes;
    public final long   totalRamBytes;
    public final double ramUsedPercent;
    public final long   swapUsedBytes;
    public final long   swapTotalBytes;
    public final long   cachedBytes;

    // ── Network ───────────────────────────────────────────────────────────
    public final long downloadKbps;
    public final long uploadKbps;

    // ── Storage & Processes ───────────────────────────────────────────────
    public final List<DiskInfo>    drives;
    public final List<ProcessInfo> topProcesses;

    public SystemSnapshot(
        double cpuUsagePercent, double[] perCorePercents,
        double cpuTempCelsius,  long currentFreqHz, long maxFreqHz,
        long usedRamBytes,      long totalRamBytes,
        long swapUsedBytes,     long swapTotalBytes, long cachedBytes,
        long downloadKbps,      long uploadKbps,
        List<DiskInfo> drives,  List<ProcessInfo> topProcesses) {

        this.cpuUsagePercent = cpuUsagePercent;
        this.perCorePercents = perCorePercents;
        this.cpuTempCelsius  = cpuTempCelsius;
        this.currentFreqHz   = currentFreqHz;
        this.maxFreqHz       = maxFreqHz;
        this.usedRamBytes    = usedRamBytes;
        this.totalRamBytes   = totalRamBytes;
        this.ramUsedPercent  = usedRamBytes * 100.0 / totalRamBytes;
        this.swapUsedBytes   = swapUsedBytes;
        this.swapTotalBytes  = swapTotalBytes;
        this.cachedBytes     = cachedBytes;
        this.downloadKbps    = downloadKbps;
        this.uploadKbps      = uploadKbps;
        this.drives          = drives;
        this.topProcesses    = topProcesses;
    }
}
