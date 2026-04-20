package Interfaces;

/**
 * Centralized, shared log-processing status display.
 * All parsers report here so we get one clean status line.
 */
public class ParseStatus {

    private static int bsdCount = 0;
    private static int rfc5424Count = 0;
    private static int jsonCount = 0;
    private static int universalCount = 0;

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

    /**
     * Increments the count for logs parsed using the universal heuristic parser.
     */
    public static synchronized void incrementUniversal() {
        universalCount++;
    }

    /**
     * Prints the current processing status to the console.
     */
    private static void printStatus() {
        String status = String.format(
                "\rBSD: %d | RFC5424: %d | JSON: %d | Universal: %d" ,
                bsdCount, rfc5424Count, jsonCount, universalCount
        );
        System.out.print(status);
        System.out.flush();
    }
}