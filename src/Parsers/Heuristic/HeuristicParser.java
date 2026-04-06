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
                // Split by tab but with a limit to avoid splitting the message content itself
                // Host, Severity, Category, PID, Message (5 columns)
                tokens = rawline.split("\t", 6);
                List<String> messageTokens = new ArrayList<>();

                if (tokens.length > 0 && isLikelyHost(tokens[0])) {
                    host = tokens[0];
                    int currentIndex = 1;

                    // Try to map Severity, Category, and PID if they match expected patterns
                    while (currentIndex < tokens.length - 1) { // Leave at least one token for message
                        String t = tokens[currentIndex].trim();
                        String upperT = t.toUpperCase();
                        
                        if (upperT.matches(SEVERITIES_REGEX)) {
                            severity = upperT;
                            currentIndex++;
                        } else if (upperT.matches("^[A-Z& ]{3,24}$") && (upperT.contains("&") || upperT.contains("SYSTEM") || upperT.contains("SERVICES") || upperT.contains("AUTH") || upperT.contains("NETWORK") || upperT.contains("POLICY") || upperT.contains("AUDIT") || upperT.contains("SECURITY") || upperT.equals("UNCATEGORIZED"))) {
                            category = upperT;
                            currentIndex++;
                        } else if (t.matches("^.*\\[\\d+\\].*$")) {
                            pid = t;
                            currentIndex++;
                        } else {
                            break;
                        }
                    }
                    
                    // The rest of the tokens (if any) are part of the message
                    for (int i = currentIndex; i < tokens.length; i++) {
                        messageTokens.add(tokens[i]);
                    }
                    message = String.join("\t", messageTokens).trim();
                } else {
                    message = rawline;
                }
            } else {
                // Not tab-separated (or not enough tabs), fallback to space/tab mixture
                // Use a more conservative approach: don't split the whole line, just look for the header
                tokens = rawline.split("[\\t ]+", 6);
                List<String> messageTokens = new ArrayList<>();

                if (tokens.length > 0 && isLikelyHost(tokens[0])) {
                    host = tokens[0];
                    int currentIndex = 1;
                    
                    // For space-separated logs, we only tentatively extract severity/category
                    // because they could easily be part of the message.
                    while (currentIndex < tokens.length - 1) {
                        String t = tokens[currentIndex].trim();
                        String upperT = t.toUpperCase();
                        if (upperT.matches(SEVERITIES_REGEX)) {
                            severity = upperT;
                            currentIndex++;
                        } else {
                            break;
                        }
                    }

                    // We are NOT extracting Category or PID from space-separated logs here
                    // to avoid eating the message start.
                    
                    // Re-calculate the message by finding the original substring to preserve internal spaces
                    // This is safer than join(" ", tokens)
                    int charPos = 0;
                    for (int i = 0; i < currentIndex; i++) {
                        charPos = rawline.indexOf(tokens[i], charPos) + tokens[i].length();
                    }
                    message = rawline.substring(charPos).trim();
                } else {
                    message = rawline;
                }
            }
            
            // Clean up trailing #015 (common in NXLog outputs)
            if (message.endsWith("#015")) {
                message = message.substring(0, message.length() - 4).trim();
            }

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