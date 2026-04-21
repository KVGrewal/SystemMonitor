package com.monitor;

import com.monitor.alert.AlertManager;
import com.monitor.collector.MetricsCollector;
import com.monitor.config.AppConfig;
import com.monitor.display.DashboardRenderer;
import com.monitor.logger.EventLogger;
import com.monitor.model.SystemSnapshot;
import org.fusesource.jansi.AnsiConsole;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.OperatingSystem;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Owns the main polling loop.
 * Wires together: collector → alert manager → renderer.
 * Static-free by design — everything is injected or constructed here.
 */
public class MonitorApp {

    private final AppConfig config;

    public MonitorApp(AppConfig config) {
        this.config = config;
    }

    public void run() throws Exception {
        AnsiConsole.systemInstall();

        // ── OSHI init ──────────────────────────────────────────────────────
        SystemInfo               sysInfo  = new SystemInfo();
        HardwareAbstractionLayer hardware = sysInfo.getHardware();
        OperatingSystem          os       = sysInfo.getOperatingSystem();
        CentralProcessor         cpu      = hardware.getProcessor();
        GlobalMemory             memory   = hardware.getMemory();

        // ── One-time static facts ──────────────────────────────────────────
        String hostname     = InetAddress.getLocalHost().getHostName();
        String username     = System.getProperty("user.name");
        String osLabel      = os.getFamily() + " " + os.getVersionInfo().getVersion();
        String bootTime     = LocalDateTime.now()
            .minusSeconds(os.getSystemUptime())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String cpuModelName = cpu.getProcessorIdentifier().getName().trim();

        List<GraphicsCard> gpuList    = hardware.getGraphicsCards();
        String             gpuName   = gpuList.isEmpty() ? "N/A" : gpuList.get(0).getName();
        long               ramGb     = Math.round(memory.getTotal() / 1_073_741_824.0 / 2) * 2;

        // ── First non-loopback active network adapter ──────────────────────
        String adapterLabel = "N/A", ipAddress = "N/A", macAddress = "N/A";
        for (NetworkIF adapter : hardware.getNetworkIFs()) {
            adapter.updateAttributes();
            if (adapter.getIfOperStatus() != NetworkIF.IfOperStatus.UP) continue;
            if (adapter.getName().toLowerCase().contains("loopback"))    continue;
            String dn = adapter.getDisplayName();
            adapterLabel = (dn.toLowerCase().contains("wi") ? "Wi-Fi" : "Ethernet")
                + " (" + dn + ")";
            if (adapter.getIPv4addr().length > 0) ipAddress = adapter.getIPv4addr()[0];
            macAddress = adapter.getMacaddr();
            break;
        }

        // ── Wire subsystems ────────────────────────────────────────────────
        EventLogger       eventLogger  = new EventLogger(config.logFile);
        MetricsCollector  collector    = new MetricsCollector(hardware, os);
        AlertManager      alertManager = new AlertManager(config, eventLogger);
        DashboardRenderer renderer     = new DashboardRenderer(
            config,
            hostname, username, osLabel, bootTime,
            cpuModelName, gpuName,
            adapterLabel, ipAddress, macAddress,
            ramGb, cpu.getMaxFreq()
        );

        eventLogger.write("INFO", hostname, username,
            "System Monitor v2.0 started"
                + " | Log: "       + config.logFile.getAbsolutePath()
                + " | CPU warn: "  + (int) config.cpuWarnPercent + "%"
                + "  RAM warn: "   + (int) config.ramWarnPercent + "%"
                + "  Disk warn: "  + config.diskFreeWarnGb + " GB");

        // ── Main loop ──────────────────────────────────────────────────────
        while (true) {
            SystemSnapshot snapshot       = collector.poll();
            List<String>   activeWarnings = alertManager.checkAndUpdate(
                snapshot, hostname, username);

            renderer.render(
                snapshot,
                activeWarnings,
                alertManager.getRecentAlerts(),
                os.getSystemUptime(),
                config.logFile.getAbsolutePath()
            );

            Thread.sleep(config.refreshSeconds * 1000L);

            if (System.in.available() > 0 && System.in.read() == 'q') break;
        }

        eventLogger.write("INFO", hostname, username,
            "System Monitor v2.0 stopped gracefully.");
        AnsiConsole.systemUninstall();
    }
}
