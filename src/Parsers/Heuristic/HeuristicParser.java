package Parsers.Heuristic;

import Interfaces.CategorizationMaster;
import Interfaces.ParseStatus;
import SentryStack.LogObject;
import Interfaces.ParserMaster;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HeuristicParser implements ParserMaster {

    // The ^ forces it to match ONLY at the beginning of the string.
    // (?:<\\d+>)? ignores syslog priority tags like <13> if they exist.
    // Safely eats Syslog priorities (<14>), version numbers (1), and timezone/milliseconds (.123Z)
    private static final Pattern DATE_HUNTER = Pattern.compile(
            "^(?:<\\d+>)?(?:\\s*\\d+)?\\s*(\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}[\\.\\d+A-Za-z:-]*|[A-Z][a-z]{2}\\s+\\d{1,2}\\s+\\d{2}:\\d{2}:\\d{2})\\s+(?:\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}[\\.\\d+A-Za-z:-]*\\s+)?"
    );

    // List of severities to check for
    private static final String SEVERITIES_REGEX = "(?:INFO|WARN|ERROR|DEBUG|CRITICAL|NOTICE|EMERG|ALERT|ERR|WARNING|FATAL)";

    @Override
    public boolean canParse(String rawline) {
        if (rawline == null || rawline.isBlank()) return false;
        
        // Only reject logs that start with a timestamp followed IMMEDIATELY by a severity
        // This indicates a fragment from an NXLog split.
        if (rawline.matches("^\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}\\s+" + SEVERITIES_REGEX + ".*$")) {
            return false;
        }

        // The ultimate fallback.
        return true;
    }

    @Override
    public LogObject parse(String rawline) {

        if (rawline == null || rawline.isBlank()) {
            System.out.println("DEBUG DROP [HEURISTIC] - Blank or Null line received.");
            return null;
        }

        if (rawline == null || rawline.isBlank()) return null;

        long epochTime = 0;
        String host = "UNKNOWN-HOST";
        String severity = "INFO";
        String category = "UNCATEGORIZED";
        String pid = "N/A";
        String message = rawline; // Default to whole line if we fail to extract

        try {
            // 1. Hunt for a Timestamp ONLY at the beginning
            Matcher dateMatcher = DATE_HUNTER.matcher(rawline);

            if (dateMatcher.find()) {
                String dateStr = dateMatcher.group(1);

                // TODO: Parse dateStr to set epochTime

                // Safely chop off ONLY the matched prefix, leaving the rest of the line alone
                rawline = rawline.substring(dateMatcher.end()).trim();
            } else {
                // No timestamp at the very beginning, use current time
                epochTime = System.currentTimeMillis() / 1000;
            }

            // 2. Tokenize the remaining string
            // Improved tokenization to handle TABS and SPACES
            // ONLY use tab-separated logic if the line actually has enough tabs to be a structured NXLog
            String[] tokens;
            boolean isTabbed = rawline.contains("\t") && rawline.split("\t").length >= 4;

            if (isTabbed) {
                tokens = rawline.split("\t");
            } else {
                // Not tab-separated (or not enough tabs), fallback to space/tab mixture
                tokens = rawline.split("[\\t ]+");
            }
            List<String> messageTokens = new ArrayList<>();

            // 3. Score ONLY the first token to see if it's a host
            if (tokens.length > 0) {
                if (isLikelyHost(tokens[0])) {
                    host = tokens[0];
                    // The next few tokens might be Severity, Category, etc. if it's the tabbed format
                    int startIndex = 1;
                    
                    // Optional: Skip Severity and Category if they match patterns
                    while (startIndex < tokens.length) {
                        String t = tokens[startIndex].toUpperCase();
                        if (t.matches(SEVERITIES_REGEX)) {
                            severity = t;
                            startIndex++;
                        } else if (isTabbed && t.matches("^[A-Z& ]{3,24}$") && (t.contains("&") || t.contains("SYSTEM") || t.contains("SERVICES") || t.contains("AUTH") || t.contains("NETWORK") || t.contains("POLICY") || t.contains("AUDIT") || t.contains("SECURITY"))) {
                            category = t;
                            startIndex++;
                        } else {
                            break;
                        }
                    }

                    // Check if the next token looks like an AppName[PID] or AppName[PID][ThreadID]
                    // ONLY take it as PID if it's really the tabbed format, otherwise it's likely message content
                    if (isTabbed && startIndex < tokens.length && tokens[startIndex].matches("^.*\\[\\d+\\].*$")) {
                        pid = tokens[startIndex];
                        startIndex++;
                    }

                    messageTokens.addAll(Arrays.asList(tokens).subList(startIndex, tokens.length));
                } else {
                    Collections.addAll(messageTokens, tokens);
                }
            }

            // Rebuild the message
            // Preserve TABS if it was a tabbed log but we are rebuilding message
            message = String.join(isTabbed ? "\t" : " ", messageTokens);

        } catch (Exception e) {
            System.out.println("DEBUG DROP [HEURISTIC] - Exception: " + e.getMessage() + " | Raw: " + rawline);
            message = rawline;
        }

        ParseStatus.incrementUniversal();
        LogObject logObject = new LogObject(epochTime, host, severity, category, pid, message);
        return CategorizationMaster.categorize(logObject);
    }

    private boolean isLikelyHost(String token) {
        // Strip common brackets that might surround an IP/Host
        String cleanToken = token.replaceAll("[\\[\\]()=:]", "");

        // Is it an IPv4 address?
        if (cleanToken.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) return true;

        // Is it a MAC address?
        if (cleanToken.matches("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")) return true;

        // Does it look like a Fully Qualified Domain Name (FQDN)? (e.g., host.domain.local)
        // Must contain at least one dot surrounded by alphanumeric characters
        if (cleanToken.matches("^[a-zA-Z0-9-]+\\.[a-zA-Z0-9.-]+$")) return true;

        // Does it look like a standard machine name?
        if (cleanToken.matches("^[A-Za-z0-9.-]+$") && cleanToken.length() > 2) {
            String lower = cleanToken.toLowerCase();
            if (lower.equals("info") || lower.equals("error") || lower.equals("warn") || lower.equals("debug")) {
                return false;
            }

            boolean hasLetter = cleanToken.matches(".*[A-Za-z].*");
            boolean hasNumber = cleanToken.matches(".*[0-9].*");
            boolean hasDash = cleanToken.contains("-");

            // If it is pure letters (like "The" or "Connection"), reject it.
            // A valid guessed hostname should contain a mix of letters AND a number or dash.
            if (hasLetter && (hasNumber || hasDash)) {
                return true;
            }
        }

        return false;
    }
}