System Monitor v2.0
A lightweight terminal-based system monitoring dashboard for Windows.
Built in Java using OSHI for hardware data and Jansi for colored terminal output.
---
Preview
```
══════════════════════════════════════════════════════════════════════════════════════════════════════
                                      SM DASHBOARD v2.0
                     Platform: Windows 11  |  Host: mypc  |  User: alex  |  Refresh: 2s
══════════════════════════════════════════════════════════════════════════════════════════════════════
                          HEALTH [*] HEALTHY   UPTIME 2h 14m   ALERTS 0 active
──────────────────────────────────────────────────────────────────────────────────────────────────────
[ SYSTEM INFO ]                                 [ NETWORK ]
OS        : Windows 11                          Adapter   : Wi-Fi (Intel Wireless)
Hostname  : mypc                                IP        : 192.168.1.5
...
```
---
Features
CPU Monitoring — overall usage, per-core breakdown, temperature, frequency, 60s trend
Memory Tracking — RAM usage, swap, cached memory
Disk Monitoring — usage per drive, SSD/HDD/USB detection
Network Speed — real-time upload and download in KB/s or MB/s
Top Processes — top 4 processes by CPU usage
Smart Alerts — fires only on threshold transitions, not every tick
Event Logging — all alerts saved to a log file automatically
Color Coded UI — green/yellow/red based on severity
---
Download & Run
> **Requires Java 17 or higher** — [Download Java](https://www.java.com/download)
Go to Releases
Download `SystemMonitor-v2.0.zip`
Extract the zip
Double-click `run.bat`
To exit — type `q` and press Enter
---
Configuration
Edit `config.properties` to customize alert thresholds:
```properties
cpu.warn=80           # Warn when CPU goes above 80%
ram.warn=85           # Warn when RAM goes above 85%
disk.warn.gb=15       # Warn when free disk space drops below 15 GB
refresh.seconds=2     # Dashboard refresh rate in seconds
log.file=system_monitor_logs.txt
```
---
Build From Source
Requirements
Java 17+
Maven 3.8+
Steps
```bash
# Clone the repo
git clone https://github.com/KVGrewal/SystemMonitor.git
cd SystemMonitor

# Build the fat JAR
mvn package

# Run it
java -jar target/system-monitor-1.0.jar
```
Optional — disable colors
```bash
java -jar target/system-monitor-1.0.jar --no-color
```
---
Project Structure
```
src/main/java/com/monitor/
├── Main.java                     # Entry point
├── MonitorApp.java               # Main loop
├── alert/AlertManager.java       # Threshold checks and alerts
├── collector/MetricsCollector.java # Hardware data via OSHI
├── config/AppConfig.java         # Loads config.properties
├── display/
│   ├── DashboardRenderer.java    # Terminal UI
│   └── ColorPalette.java         # ANSI color codes
├── logger/EventLogger.java       # File logging
└── model/
    ├── SystemSnapshot.java       # Data bundle per tick
    ├── DiskInfo.java             # Per-drive data
    └── ProcessInfo.java          # Per-process data
```
---
Dependencies
Library	Version	Purpose
OSHI	6.4.10	Hardware & OS metrics
Jansi	2.4.1	ANSI colors on Windows
---
Versions
Version	Notes
v2.0	Full rewrite — dashboard UI, alerts, logging, network, per-core CPU
v1.1	Initial release
---
Author
KVGrewal — github.com/KVGrewal
---
License
MIT License — free to use, modify and distribute.
