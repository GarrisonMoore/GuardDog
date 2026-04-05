package Parsers.Heuristic;

import Interfaces.CategorizationMaster;
import Interfaces.ParserMaster;
import SentryStack.LogObject;

import java.time.LocalDateTime;
import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HeuristicParser implements ParserMaster {

    Long timestamp;
    String source;
    String category;
    String severity;
    String pid;
    String message;


    @Override
    public boolean canParse(String rawline) {
        return true;
    }

    @Override
    public LogObject parse(String rawline) {

        Pattern universalPattern = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}|[A-Z][a-z]{2}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(\\S+)\\s+(.*)$");

        Matcher m = universalPattern.matcher(rawline);
        if (!m.find()) {
            return null;
        }else {
            String timestampSTR = m.group(1);
            source = m.group(2);
            pid = "N/A";          // Default to N/A or empty string
            message = m.group(3); // Grab the rest of the line as the message


            timestamp = parseTimestamp(timestampSTR);
            if (timestamp == null) {
                return null;
            }
        }
        LogObject rawLog = new LogObject(timestamp, source, "INFO", "UNCATEGORIZED", pid, message);
        return CategorizationMaster.categorize(rawLog);
    }

    private Long parseTimestamp(String timestampSTR) {
        try {
            if (timestampSTR.matches("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}")) {
                String normalized = timestampSTR.replace(' ', 'T');
                LocalDateTime dt = LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                return dt.atZone(ZoneId.systemDefault()).toEpochSecond();
            }

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM d HH:mm:ss");
            LocalDateTime dt = LocalDateTime.parse(timestampSTR, formatter);

            // Since the log has no year, use the current year
            dt = dt.withYear(Year.now().getValue());

            return dt.atZone(ZoneId.systemDefault()).toEpochSecond();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
