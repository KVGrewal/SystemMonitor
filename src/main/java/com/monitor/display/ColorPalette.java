package com.monitor.display;

/**
 * Central place for every ANSI escape code.
 * Call {@link #disableColors()} once at startup when --no-color is passed;
 * {@link #colorize} becomes a no-op from that point on.
 */
public class ColorPalette {

    public static final String GREEN  = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String RED    = "\u001B[31m";
    public static final String CYAN   = "\u001B[36m";
    public static final String DIM    = "\u001B[2m";
    public static final String BOLD   = "\u001B[1m";
    public static final String RESET  = "\u001B[0m";

    private static boolean colorDisabled = false;

    /** Turn off all color output (e.g. when writing to a file or piping). */
    public static void disableColors() {
        colorDisabled = true;
    }

    /**
     * Wraps {@code text} in the given ANSI code + reset.
     * Returns plain text when colors are disabled.
     */
    public static String colorize(String ansiCode, String text) {
        return colorDisabled ? text : ansiCode + text + RESET;
    }

    /** Strips all ANSI escape sequences — used for visible-length calculations. */
    public static String removeAnsiCodes(String input) {
        return input.replaceAll("\u001B\\[[;\\d]*m", "");
    }
}
