import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyslogParser implements LogParser {

    // RFC-5424 format Regex Tokenizer
    private static final Pattern LOG_PATTERN = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[^\\s]*)\\s+(\\S+)\\s+.*:\\s+(.*)$");

    @Override
    public boolean canParse(String rawline) {
        return rawline.matches("^\\d{4}-\\d{2}.*");
    }

    @Override
    public LogObject parse(String rawline) {
        Matcher m = LOG_PATTERN.matcher(rawline);
        LogObject logObject = null;
        if (m.find()) {
            try {
                long epochTime = java.time.OffsetDateTime.parse(m.group(1)).toEpochSecond();
                String host = m.group(2);
                String msg = m.group(3);

                String lowerMsg = msg.toLowerCase();

                // throw away windows noise first
                if (lowerMsg.contains("the locale specific resource for the desired message is not present")) {
                    msg = "computer fart noises";
                }

                // --- CATEGORIZATION ---
                String severity;
                String category;

                // Severities
                if (lowerMsg.contains("fail") || lowerMsg.contains("error") || lowerMsg.contains("exception") || lowerMsg.contains("failed")) {
                    severity = "CRIT";
                    category = "ERRORS";
                } else if (lowerMsg.contains("warn") || lowerMsg.contains("timeout") || lowerMsg.contains("warning") || lowerMsg.contains("blocked") || lowerMsg.contains("denied")) {
                    severity = "WARN";
                    category = "WARNINGS";
                } else severity = "INFO";

                // Categories
                if (lowerMsg.contains("failed") || lowerMsg.contains("failed to")) {
                    category = "ERRORS";
                } else if (lowerMsg.contains("logon") || lowerMsg.contains("auth") || lowerMsg.contains("access") || lowerMsg.contains("request")) {
                    category = "AUTH EVENTS";
                } else if (lowerMsg.contains("audit") || lowerMsg.contains("auditd")) {
                    category = "AUDIT";
                } else if (lowerMsg.contains("group") || lowerMsg.contains("policy") || lowerMsg.contains(".local") || lowerMsg.contains("10.202.69.") || lowerMsg.contains("{")) {
                    category = "GROUP POLICY";
                } else if (lowerMsg.contains("kbps") || lowerMsg.contains("wallpaper") || lowerMsg.contains("wallpapers") || lowerMsg.contains("none")) {
                    category = "GROUP POLICY";
                } else category = "UNCATEGORIZED";

                // create a new log object for any logs that fit the above categories
                logObject = new LogObject(epochTime, host, severity, category, msg);

                // compute time index and host index
                IndexingEngine.TimeIndex.computeIfAbsent(epochTime, k -> new ArrayList<>()).add(logObject);
                IndexingEngine.HostIndex.computeIfAbsent(host, k -> new ArrayList<>()).add(logObject);

            } catch (Exception e) {
                // Don't ignore parsing errors bruh
                System.err.println("Error parsing log: " + e.getMessage());
            }
        }
        return logObject;
    }
}