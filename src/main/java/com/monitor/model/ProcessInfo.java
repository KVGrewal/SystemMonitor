package com.monitor.model;

/**
 * Lightweight read-only snapshot of one OS process.
 */
public class ProcessInfo {

    public final int    pid;
    public final String name;
    public final double cpuPercent;
    public final long   memoryMb;
    public final String state;

    public ProcessInfo(int pid, String name,
                       double cpuPercent, long memoryMb, String state) {
        this.pid        = pid;
        this.name       = name;
        this.cpuPercent = cpuPercent;
        this.memoryMb   = memoryMb;
        this.state      = state;
    }
}
