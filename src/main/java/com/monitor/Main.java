package com.monitor;

import com.monitor.config.AppConfig;
import com.monitor.display.ColorPalette;

/**
 * Entry point — parse CLI flags, load config, hand off to MonitorApp.
 * Nothing else lives here.
 */
public class Main {

    public static void main(String[] args) throws Exception {
        for (String arg : args)
            if (arg.equalsIgnoreCase("--no-color"))
                ColorPalette.disableColors();

        AppConfig config = AppConfig.load();
        new MonitorApp(config).run();
    }
}
