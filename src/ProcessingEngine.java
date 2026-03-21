import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProcessingEngine {

    private static HashMap<String, List<LogObject>> HostIndex = new HashMap<>();
    private static TreeMap<Long, LogObject> TimeIndex = new TreeMap<>();

    private static final Pattern LOG_PATTERN = Pattern.compile("^(\\S+\\s+\\d+\\s+\\d+:\\d+:\\d+)\\s+(\\S+)\\s+(\\S+):\\s+(.*)$");

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d HH:mm:ss", Locale.ENGLISH);

    static Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader("src/test_logs.txt"))) {
        //try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            System.out.println("Watch Dog Engine cranking over...");

            while ((line = reader.readLine()) != null) {
                parseAndProcess(line);
                System.out.println("Processed: " + line);
            }
        } catch (Exception e) {
            System.out.println("Stream interrupted: " + e.getMessage());
        }

        System.out.println("Dump Logs? (y/n):");
        try {
            String input = scanner.nextLine();
            if (input.equals("y")) {
                displayStats();
            } else {
                System.out.println("boobenfarden");
            }
        } catch (Exception e) {
            System.out.println("Invalid input: " + e.getMessage());
        }
    }

    private static void parseAndProcess(String rawLine) {
        Matcher matcher = LOG_PATTERN.matcher(rawLine);
        if (matcher.find()) {
            try {
                String timeStr = matcher.group(1).trim();
                // Use a temporary object to hold the Month/Day/Time
                DateTimeFormatter tempFormatter = DateTimeFormatter.ofPattern("MMM d HH:mm:ss", Locale.ENGLISH);

                // We have to parse it as a TemporalAccessor first because it lacks a year
                java.time.temporal.TemporalAccessor accessor = tempFormatter.parse(timeStr);
                int month = accessor.get(java.time.temporal.ChronoField.MONTH_OF_YEAR);
                int day = accessor.get(java.time.temporal.ChronoField.DAY_OF_MONTH);
                int hour = accessor.get(java.time.temporal.ChronoField.HOUR_OF_DAY);
                int minute = accessor.get(java.time.temporal.ChronoField.MINUTE_OF_HOUR);
                int second = accessor.get(java.time.temporal.ChronoField.SECOND_OF_MINUTE);

                // Now build the full LocalDateTime with the current year
                LocalDateTime ldt = LocalDateTime.of(LocalDateTime.now().getYear(), month, day, hour, minute, second);
                long epochTime = ldt.toEpochSecond(ZoneOffset.UTC);

                String host = matcher.group(2);
                String msg = matcher.group(4);

                LogObject logObject = new LogObject(epochTime, host, "INFO", msg);

                // ... indexing logic ...
                TimeIndex.put(epochTime, logObject);
                HostIndex.computeIfAbsent(host, k -> new ArrayList<>()).add(logObject);

                System.out.println("Indexed log from: " + host + " [" + epochTime + "]");

            } catch (Exception e) {
                // Print the error so you can see WHY it's skipping
                System.err.println("Skipping malformed log: " + rawLine + " | Error: " + e.getMessage());
            }
        }
    }

    // Dumps Current Processed data
    public static void displayStats() {
        System.out.println("\n--- Watch Dog Statistics ---");
        System.out.println("Total Unique Hosts: " + HostIndex.size());
        System.out.println("Total Logs in Memory: " + TimeIndex.size());

        HostIndex.forEach((host, logs) -> {
            System.out.println(" > " + host + ": " + logs.size() + " entries");
        });
    }
}
