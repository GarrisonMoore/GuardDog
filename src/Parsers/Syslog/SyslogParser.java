package Parsers.Syslog;

import Interfaces.CategorizationMaster;
import SentryStack.LogObject;
import Interfaces.ParserMaster;
import Interfaces.ParseStatus;


import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SyslogParser implements ParserMaster {

    int BSD_LOG_COUNT = 0;
    int RFC5424_LOG_COUNT = 0;

    // RFC-5424: <PRI>VERSION TIMESTAMP HOST APP PROCID MSGID STRUCTURED-DATA MESSAGE
    private static final Pattern RFC5424_PATTERN = Pattern.compile(
            "^(\\S+)\\s+(\\S+)\\s+(\\S+\\[\\d+\\])\\s+(.*)$"
    );

    // BSD syslog: "Apr  1 12:34:56 hostname message"
    private static final Pattern BSD_PATTERN = Pattern.compile(
            "^([A-Z][a-z]{2}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(\\S+)\\s+(.*)$"
    );
    private static final java.time.format.DateTimeFormatter BSD_FORMATTER = new java.time.format.DateTimeFormatterBuilder()
            .appendPattern("MMM d HH:mm:ss")
            .parseDefaulting(java.time.temporal.ChronoField.YEAR, java.time.LocalDate.now().getYear())
            .toFormatter(java.util.Locale.ENGLISH);

    // override the canParse method in the Interfaces.LogParser interface (do this for any new parser)
    @Override
    public boolean canParse(String rawline) {

        // make sure the line matches the RFC-5424 format
        return RFC5424_PATTERN.matcher(rawline).matches() || BSD_PATTERN.matcher(rawline).matches();
    }

    // override the parse method in the Interfaces.LogParser interface (do this for any new parser)
    // parse the line using the RFC-5424 and BSD format
    @Override
    public LogObject parse(String rawline) {
        Matcher m = RFC5424_PATTERN.matcher(rawline);

        long epochTime = 0;
        String host = "";
        String pid = "";
        String msg = "";

        if (m.matches()) {
            try {
                epochTime = java.time.OffsetDateTime.parse(m.group(1)).toEpochSecond();
                host = m.group(2);
                pid = m.group(3);
                msg = m.group(4);

                if (looksLikeHost(host)) {
                    return null;
                }

                ParseStatus.incrementRFC5424();
            } catch (Exception e) {
                System.err.print("Error parsing RFC5424 log: " + e.getMessage());
                return null;
            }

        } else {
            m = BSD_PATTERN.matcher(rawline);
            if (!m.matches()) {
                return null;
            }

            try {
                String timestampStr = m.group(1);
                // Handle single digit day spacing if present (e.g., "Mar  1")
                timestampStr = timestampStr.replaceAll("\\s+", " ");
                epochTime = LocalDateTime.parse(timestampStr, BSD_FORMATTER)
                        .atZone(ZoneId.systemDefault()).toEpochSecond();

                host = m.group(2);
                pid = m.group(3);
                msg = m.group(4);

                if (looksLikeHost(host)) {
                    return null;
                }

                ParseStatus.incrementBSD();
            } catch (Exception e) {
                System.err.print("Error parsing BSD log: " + e.getMessage());
                return null;
            }
        }


        String severity = "INFO";
        String category = "UNCATEGORIZED";

        // Raw log object
        LogObject logObject = new LogObject(epochTime, host, severity, category, pid,  msg);

        // Categorize the log object
        LogObject categorizedLogObject = CategorizationMaster.categorize(logObject);
        return categorizedLogObject;
    }

    private boolean looksLikeHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }

        String h = host.trim();

        // Accept normal hostnames / IP-ish values
        if (!h.matches("[A-Za-z0-9._-]+") || h.contains("10.202.69.")) {
            return true;
        }

        // Reject obvious false positives
        String lower = h.toLowerCase(Locale.ROOT);
        return lower.equals("default")
                || lower.equals("operation")
                || lower.equals("service");
    }
}