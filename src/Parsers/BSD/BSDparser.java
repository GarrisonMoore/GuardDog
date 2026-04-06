package Parsers.BSD;

import Interfaces.CategorizationMaster;
import SentryStack.LogObject;
import Interfaces.ParserMaster;
import Interfaces.ParseStatus;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BSDparser implements ParserMaster {

    // BSD syslog: "Apr  1 12:34:56 hostname message"
    private static final Pattern BSD_PATTERN = Pattern.compile(
            "^([A-Z][a-z]{2}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(\\S+)\\s+(.*)$"
    );

    private static final java.time.format.DateTimeFormatter BSD_FORMATTER = new java.time.format.DateTimeFormatterBuilder()
            .appendPattern("MMM d HH:mm:ss")
            .parseDefaulting(java.time.temporal.ChronoField.YEAR, java.time.LocalDate.now().getYear())
            .toFormatter(java.util.Locale.ENGLISH);

    @Override
    public boolean canParse(String rawline) {
        return BSD_PATTERN.matcher(rawline).matches();
    }

    @Override
    public LogObject parse(String rawline) {

        if (rawline == null || rawline.isBlank()) {
            System.out.println("DEBUG DROP [BSD] - Blank or Null line received.");
            return null;
        }

        Matcher m = BSD_PATTERN.matcher(rawline);

        if (!m.matches()) {
            // DEBUG: The regex failed completely
            System.out.println("DEBUG DROP [BSD] - Regex Mismatch | Raw: " + rawline);
            return null;
        }

        long epochTime = 0;
        String host = "";
        String pid = "";
        String msg = "";

        try {
            String timestampStr = m.group(1);
            // Handle single digit day spacing if present (e.g., "Mar  1")
            timestampStr = timestampStr.replaceAll("\\s+", " ");
            epochTime = LocalDateTime.parse(timestampStr, BSD_FORMATTER)
                    .atZone(ZoneId.systemDefault()).toEpochSecond();

            host = m.group(2);
            pid = "N/A";       // Fixed: BSD regex only has 3 groups. PID is not cleanly extracted here.
            msg = m.group(3);  // Fixed: Grab the rest of the message from group 3.

            if (!isValidHost(host)) { // Fixed: Using whitelist validation
                // DEBUG: The host validation failed
                System.out.println("DEBUG DROP [BSD] - Invalid Host (" + host + ") | Raw: " + rawline);
                return null;
            }

            ParseStatus.incrementBSD();
        } catch (Exception e) {
            // DEBUG: Something threw a hard error
            System.out.println("DEBUG DROP [BSD] - Exception: " + e.getMessage() + " | Raw: " + rawline);
            return null;
        }

        String severity = "INFO";
        String category = "UNCATEGORIZED";

        // Raw log object
        LogObject logObject = new LogObject(epochTime, host, severity, category, pid, msg);

        // Categorize the log object
        return CategorizationMaster.categorize(logObject);
    }

    private boolean isValidHost(String host) {
        if (host == null) return false;
        String h = host.trim();
        if (h.isEmpty()) return false;

        String lower = h.toLowerCase(Locale.ROOT);
        String[] stop = {"overall","remaining","logged","registration","stage","enqueue","dequeue","evaluation","flushing","bundle","post","data","machine","check"};
        for (String s : stop) {
            if (lower.equals(s)) return false;
        }

        // IPv4
        if (h.matches("^(25[0-5]|2[0-4]\\d|[01]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[01]?\\d?\\d)){3}$")) return true;
        // IPv6 (permissive)
        if (h.matches("^[0-9A-Fa-f:]{2,}$") && h.contains(":")) return true;
        // FQDN with a dot
        if (h.matches("^[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$")) return true;
        // NetBIOS-like name, require digit or dash
        if (h.matches("^[A-Za-z0-9-]{3,63}$")) {
            boolean hasDigit = h.matches(".*[0-9].*");
            boolean hasDash = h.contains("-");
            if (hasDigit || hasDash) return true;
            return false;
        }
        return false;
    }
}