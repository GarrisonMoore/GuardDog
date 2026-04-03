package Interfaces;

/**
 * Centralized, shared log-processing status display.
 * All parsers report here so we get one clean status line.
 */
public class ParseStatus {

    private static int bsdCount = 0;
    private static int rfc5424Count = 0;
    private static int jsonCount = 0;

    public static synchronized void incrementBSD() {
        bsdCount++;
        printStatus();
    }

    public static synchronized void incrementRFC5424() {
        rfc5424Count++;
        printStatus();
    }

    public static synchronized void incrementJSON() {
        jsonCount++;
        printStatus();
    }

    private static void printStatus() {
        String status = String.format(
                "\rBSD: %d | RFC5424: %d | JSON: %d",
                bsdCount, rfc5424Count, jsonCount
        );
        System.out.print(status);
        System.out.flush();
    }
}