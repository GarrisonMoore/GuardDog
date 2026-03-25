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
        if (m.find()) {
            try {
                long epochTime = java.time.OffsetDateTime.parse(m.group(1)).toEpochSecond();
                String host = m.group(2);
                String msg = m.group(3);

                String lowerMsg = msg.toLowerCase();
                // throw away windows noise
                if (lowerMsg.contains("The locale specific resource for the desired message is not present")) {
                    return null;
                }

                // --- CATEGORIZATION ---
                String severity = "INFO";
                String category = "UNCATEGORIZED";
                // categorize severities
                if (lowerMsg.contains("fail") || lowerMsg.contains("error")) severity = "CRIT";
                if (lowerMsg.contains("warn") || lowerMsg.contains("timeout")) severity = "WARN";
                if (severity == "INFO") category = "UNCATEGORIZED";
                // categorize logs
                if (lowerMsg.contains("logon") || lowerMsg.contains("auth") || lowerMsg.contains("access") || lowerMsg.contains("request")) category = "AUTH EVENTS";
                if (lowerMsg.contains("audit") || lowerMsg.contains("auditd")) category = "AUDIT";
                if (lowerMsg.contains("group") || lowerMsg.contains("group policy")) category = "GROUP POLICY";

                // create a new log object for any logs that fit the above categories
                LogObject logObject = new LogObject(epochTime, host, severity, category, msg);
                // compute time index and host index
                IndexingEngine.TimeIndex.computeIfAbsent(epochTime, k -> new ArrayList<>()).add(logObject);
                IndexingEngine.HostIndex.computeIfAbsent(host, k -> new ArrayList<>()).add(logObject);
            } catch (Exception ignored) {
                // ignore parse errors
            }
        }
        return null;
    }
}