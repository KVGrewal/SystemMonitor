package com.monitor.model;

/**
 * Read-only snapshot of a single mounted file system.
 */
public class DiskInfo {

    public final String mountPoint;
    public final String driveType;    // "SSD", "HDD", or "USB"
    public final long   totalBytes;
    public final long   freeBytes;
    public final long   usedBytes;
    public final double usedPercent;

    public DiskInfo(String mountPoint, String driveType,
                    long totalBytes, long freeBytes) {
        this.mountPoint  = mountPoint;
        this.driveType   = driveType;
        this.totalBytes  = totalBytes;
        this.freeBytes   = freeBytes;
        this.usedBytes   = totalBytes - freeBytes;
        this.usedPercent = usedBytes * 100.0 / totalBytes;
    }

    public long freeGigabytes() {
        return freeBytes / (1024L * 1024 * 1024);
    }

    public long totalGigabytes() {
        return totalBytes / (1024L * 1024 * 1024);
    }

    public long usedGigabytes() {
        return usedBytes / (1024L * 1024 * 1024);
    }
}
