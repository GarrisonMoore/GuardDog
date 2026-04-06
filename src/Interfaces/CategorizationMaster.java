package Interfaces;

import SentryStack.LogObject;

public class CategorizationMaster {

    public static LogObject categorize(LogObject log){

        Long timestamp = log.getTimestamp();
        String source = log.getSource();
        String severity = log.getSeverity();
        String category = log.getCategory();
        String pid = log.getPid();
        String message = log.getMessage();
        String messageLower = message.toLowerCase();

        // 1. Noise Filter (Throw away high-volume, low-value logs)
        if (messageLower.contains("the locale specific resource for the desired message is not present") ||
            messageLower.contains("is being suppressed") ||
            messageLower.contains("the operation was successful") ||
            messageLower.contains("is already in the desired state")) {
            return null;
        }

        // 2. Initial Categorization (Check for high-priority or specific keyword matches)
        
        // --- ERRORS & CRITICAL ---
        if (messageLower.contains("crit") || messageLower.contains("error") || messageLower.contains("exception") ||
                messageLower.contains("err") || messageLower.contains("fail") || messageLower.contains("failed") || 
                messageLower.contains("failure") || messageLower.contains("panic") || messageLower.contains("fatal") ||
                messageLower.contains("eventid: 4625") || // Windows Failed Logon
                messageLower.contains("eventid: 1102") || // Audit log cleared
                messageLower.contains("segfault") || messageLower.contains("core dumped")) {
            severity = "CRIT";
            category = "SECURITY & ERRORS";
        } 
        
        // --- WARNINGS ---
        else if (messageLower.contains("warn") || messageLower.contains("timeout") || messageLower.contains("warning") ||
                messageLower.contains("blocked") || messageLower.contains("denied") || messageLower.contains("refused") ||
                messageLower.contains("eventid: 4740") || // User account locked out
                messageLower.contains("low disk space") || messageLower.contains("cpu spike")) {
            severity = "WARN";
            category = "WARNINGS";
        }

        // --- AUTH & ACCESS ---
        else if (messageLower.contains("logon") || messageLower.contains("auth") || messageLower.contains("access") ||
                messageLower.contains("request") || messageLower.contains("login") || messageLower.contains("ssh") ||
                messageLower.contains("sudo") || messageLower.contains("su:") || messageLower.contains("password") ||
                messageLower.contains("eventid: 4624") || // Windows Successful Logon
                messageLower.contains("eventid: 4648")) { // Logon using explicit credentials
            severity = "INFO";
            category = "AUTH & ACCESS";
        }

        // --- SYSTEM & SERVICES ---
        else if (messageLower.contains("service") || messageLower.contains("systemd") || messageLower.contains("kernel") ||
                messageLower.contains("boot") || messageLower.contains("shutdown") || messageLower.contains("reboot") ||
                messageLower.contains("winrm") || messageLower.contains("remote") || messageLower.contains("management") ||
                messageLower.contains("powershell") || messageLower.contains("ps ") || messageLower.contains("cron") ||
                messageLower.contains("eventid: 7036") || // Service status change
                messageLower.contains("eventid: 6005") || // Event log started
                messageLower.contains("eventid: 6006")) { // Event log stopped
            severity = "INFO";
            category = "SYSTEM & SERVICES";
        }

        // --- POLICY & AUDIT ---
        else if (messageLower.contains("audit") || messageLower.contains("auditd") || messageLower.contains("policy") ||
                messageLower.contains("group") || messageLower.contains(".local") || messageLower.contains("gpo") ||
                messageLower.contains("eventid: 4719") || // System audit policy changed
                messageLower.contains("eventid: 4670")) { // Permissions on an object were changed
            severity = "INFO";
            category = "POLICY & AUDIT";
        }

        // --- NETWORK ---
        else if (messageLower.contains("tcp") || messageLower.contains("udp") || messageLower.contains("port") ||
                messageLower.contains("ip ") || messageLower.contains("connection") || messageLower.contains("packet") ||
                messageLower.contains("dns") || messageLower.contains("dhcp") || messageLower.contains("icmp") ||
                messageLower.contains("firewall") || messageLower.contains("iptables")) {
            severity = "INFO";
            category = "NETWORK";
        }

        // --- DEFAULT ---
        else {
            severity = "INFO";
            if (category == null || category.isEmpty() || category.startsWith("PARSER-")) {
                category = "UNCATEGORIZED";
            }
        }

        // New categorized log object
        return new LogObject(timestamp, source, severity, category, pid, message);
    }
}
