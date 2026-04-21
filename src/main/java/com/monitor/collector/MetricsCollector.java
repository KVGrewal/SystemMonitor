package com.monitor.collector;

import com.monitor.model.*;
import oshi.hardware.*;
import oshi.software.os.*;

import java.util.*;

/**
 * Holds tick-over-tick state (CPU ticks, network byte counters) and
 * produces a fresh {@link SystemSnapshot} on each call to {@link #poll()}.
 *
 * One instance should live for the lifetime of the app.
 */
public class MetricsCollector {

    private final HardwareAbstractionLayer hardware;
    private final OperatingSystem          operatingSystem;
    private final CentralProcessor         processor;
    private final GlobalMemory             memory;
    private final VirtualMemory            virtualMemory;

    /** Cached drive-type lookup built once from physical disk models. */
    private final Map<String, String> driveTypeByMount;

    // ── Tick state — updated after every poll ─────────────────────────────
    private long[]   lastCpuTicks;
    private long[][] lastCoreTicks;
    private long     lastBytesReceived;
    private long     lastBytesSent;

    public MetricsCollector(HardwareAbstractionLayer hardware, OperatingSystem os) {
        this.hardware        = hardware;
        this.operatingSystem = os;
        this.processor       = hardware.getProcessor();
        this.memory          = hardware.getMemory();
        this.virtualMemory   = memory.getVirtualMemory();

        // Seed baselines so the first real poll gives meaningful deltas
        this.lastCpuTicks      = processor.getSystemCpuLoadTicks();
        this.lastCoreTicks     = processor.getProcessorCpuLoadTicks();
        this.lastBytesReceived = sumNetworkBytes(hardware.getNetworkIFs(), true);
        this.lastBytesSent     = sumNetworkBytes(hardware.getNetworkIFs(), false);

        this.driveTypeByMount  = buildDriveTypeMap();
    }

    /** Gather one complete snapshot. Call once per refresh cycle. */
    public SystemSnapshot poll() {

        // ── CPU ──────────────────────────────────────────────────────────
        double cpuPercent   = processor.getSystemCpuLoadBetweenTicks(lastCpuTicks) * 100;
        lastCpuTicks        = processor.getSystemCpuLoadTicks();

        double[] corePercents = processor.getProcessorCpuLoadBetweenTicks(lastCoreTicks);
        lastCoreTicks         = processor.getProcessorCpuLoadTicks();

        double cpuTemp    = hardware.getSensors().getCpuTemperature();
        long[] coreFreqs  = processor.getCurrentFreq();
        long   coreFreq   = coreFreqs.length > 0 ? coreFreqs[0] : 0;

        // ── Memory ────────────────────────────────────────────────────────
        long usedRam = memory.getTotal() - memory.getAvailable();

        // ── Network ───────────────────────────────────────────────────────
        long nowReceived = sumNetworkBytes(hardware.getNetworkIFs(), true);
        long nowSent     = sumNetworkBytes(hardware.getNetworkIFs(), false);
        long downKbps    = (nowReceived - lastBytesReceived) / 1024;
        long upKbps      = (nowSent     - lastBytesSent)     / 1024;
        lastBytesReceived = nowReceived;
        lastBytesSent     = nowSent;

        // ── Disks ─────────────────────────────────────────────────────────
        List<DiskInfo> drives = new ArrayList<>();
        for (OSFileStore store : operatingSystem.getFileSystem().getFileStores()) {
            if (store.getTotalSpace() <= 0) continue;
            String driveType = driveTypeByMount.getOrDefault(store.getMount(), "Unknown");
            drives.add(new DiskInfo(
                store.getMount(), driveType,
                store.getTotalSpace(), store.getUsableSpace()));
        }

        // ── Top processes ─────────────────────────────────────────────────
        List<ProcessInfo> topProcesses = new ArrayList<>();
        for (OSProcess proc : operatingSystem.getProcesses(
            null, OperatingSystem.ProcessSorting.CPU_DESC, 12)) {

            String pName = proc.getName();
            if (pName.equalsIgnoreCase("Idle")
                || pName.equalsIgnoreCase("System Idle Process")) continue;

            double pCpu  = proc.getProcessCpuLoadCumulative() * 100;
            long   ramMb = proc.getResidentSetSize() / (1024 * 1024);
            String state = proc.getState() != null
                ? proc.getState().toString().toLowerCase() : "running";

            topProcesses.add(new ProcessInfo(proc.getProcessID(), pName, pCpu, ramMb, state));
            if (topProcesses.size() >= 4) break;
        }

        return new SystemSnapshot(
            cpuPercent, corePercents,
            cpuTemp, coreFreq, processor.getMaxFreq(),
            usedRam, memory.getTotal(),
            virtualMemory.getSwapUsed(), virtualMemory.getSwapTotal(), memory.getAvailable(),
            downKbps, upKbps,
            drives, topProcesses
        );
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private long sumNetworkBytes(List<NetworkIF> adapters, boolean received) {
        long total = 0;
        for (NetworkIF adapter : adapters) {
            adapter.updateAttributes();
            total += received ? adapter.getBytesRecv() : adapter.getBytesSent();
        }
        return total;
    }

    /**
     * Builds a mount-point → drive-type map from physical disk models.
     * NVMe drives often lack the string "ssd", so we also check for
     * "nvme", "solid state", and "flash".
     */
    private Map<String, String> buildDriveTypeMap() {
        Map<String, String> result = new HashMap<>();
        for (HWDiskStore disk : hardware.getDiskStores()) {
            String model = disk.getModel().toLowerCase();
            String type;
            if (model.contains("nvme")  || model.contains("nvm e") ||
                model.contains("ssd")   || model.contains("solid state") ||
                model.contains("flash")) {
                type = "SSD";
            } else if (model.contains("usb")) {
                type = "USB";
            } else {
                type = "HDD";
            }
            for (HWPartition partition : disk.getPartitions())
                result.put(partition.getMountPoint(), type);
        }
        return result;
    }
}
