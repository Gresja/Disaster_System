package utils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Thread-safe logger that timestamps and color-codes console output.
 * Uses synchronized to prevent interleaved output from multiple threads.
 */
public class Logger {

    // ANSI color codes
    public static final String RESET   = "\u001B[0m";
    public static final String RED     = "\u001B[31m";
    public static final String GREEN   = "\u001B[32m";
    public static final String YELLOW  = "\u001B[33m";
    public static final String BLUE    = "\u001B[34m";
    public static final String MAGENTA = "\u001B[35m";
    public static final String CYAN    = "\u001B[36m";
    public static final String WHITE   = "\u001B[37m";
    public static final String BOLD    = "\u001B[1m";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static String timestamp() {
        return LocalTime.now().format(FMT);
    }

    public static synchronized void info(String source, String message) {
        System.out.printf("%s[%s]%s %s%-20s%s %s%n",
                CYAN, timestamp(), RESET, BOLD, "[" + source + "]", RESET, message);
    }

    public static synchronized void success(String source, String message) {
        System.out.printf("%s[%s]%s %s%-20s%s %s%s%s%n",
                CYAN, timestamp(), RESET, BOLD, "[" + source + "]", RESET, GREEN, message, RESET);
    }

    public static synchronized void warning(String source, String message) {
        System.out.printf("%s[%s]%s %s%-20s%s %s%s%s%n",
                CYAN, timestamp(), RESET, BOLD, "[" + source + "]", RESET, YELLOW, message, RESET);
    }

    public static synchronized void error(String source, String message) {
        System.out.printf("%s[%s]%s %s%-20s%s %s%s%s%n",
                CYAN, timestamp(), RESET, BOLD, "[" + source + "]", RESET, RED, message, RESET);
    }

    public static synchronized void emergency(String source, String message) {
        System.out.printf("%s[%s]%s %s%-20s%s %s%s%s%n",
                CYAN, timestamp(), RESET, BOLD, "[" + source + "]", RESET, RED + BOLD, message, RESET);
    }

    public static synchronized void monitor(String message) {
        System.out.println(MAGENTA + BOLD + message + RESET);
    }

    public static synchronized void divider() {
        System.out.println(BLUE + "─".repeat(90) + RESET);
    }

    public static synchronized void header(String title) {
        System.out.println();
        System.out.println(BOLD + BLUE + "═".repeat(90) + RESET);
        System.out.printf(BOLD + BLUE + "  %-86s%n" + RESET, title);
        System.out.println(BOLD + BLUE + "═".repeat(90) + RESET);
    }
}
