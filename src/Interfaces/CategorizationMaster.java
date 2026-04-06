package Interfaces;

import SentryStack.LogObject;

public class CategorizationMaster {

    public static LogObject categorize(LogObject log){

        Long timestamp = log.getTimestamp();
        String source = log.getSource();
        String severity = log.getSeverity();
        String category = log.getCategory();
        String pid = log.getPid();
        String message = log.getMessage().toLowerCase();

        // 1. Noise Filter (Throw away high-volume, low-value logs)
        if (message.contains("the locale specific resource for the desired message is not present") ||
            message.contains("is being suppressed") ||
            message.contains("the operation was successful") ||
            message.contains("is already in the desired state")) {
            return null;
        }

        // 2. Initial Categorization (Check for high-priority or specific keyword matches)
        
        // --- ERRORS & CRITICAL ---
        if (message.contains("crit") || message.contains("error") || message.contains("exception") ||
                message.contains("err") || message.contains("fail") || message.contains("failed") || 
                message.contains("failure") || message.contains("panic") || message.contains("fatal") ||
                message.contains("eventid: 4625") || // Windows Failed Logon
                message.contains("eventid: 1102") || // Audit log cleared
                message.contains("segfault") || message.contains("core dumped")) {
            severity = "CRIT";
            category = "SECURITY & ERRORS";
        } 
        
        // --- WARNINGS ---
        else if (message.contains("warn") || message.contains("timeout") || message.contains("warning") ||
                message.contains("blocked") || message.contains("denied") || message.contains("refused") ||
                message.contains("eventid: 4740") || // User account locked out
                message.contains("low disk space") || message.contains("cpu spike")) {
            severity = "WARN";
            category = "WARNINGS";
        }

        // --- AUTH & ACCESS ---
        else if (message.contains("logon") || message.contains("auth") || message.contains("access") ||
                message.contains("request") || message.contains("login") || message.contains("ssh") ||
                message.contains("sudo") || message.contains("su:") || message.contains("password") ||
                message.contains("eventid: 4624") || // Windows Successful Logon
                message.contains("eventid: 4648")) { // Logon using explicit credentials
            severity = "INFO";
            category = "AUTH & ACCESS";
        }

        // --- SYSTEM & SERVICES ---
        else if (message.contains("service") || message.contains("systemd") || message.contains("kernel") ||
                message.contains("boot") || message.contains("shutdown") || message.contains("reboot") ||
                message.contains("winrm") || message.contains("remote") || message.contains("management") ||
                message.contains("powershell") || message.contains("ps ") || message.contains("cron") ||
                message.contains("eventid: 7036") || // Service status change
                message.contains("eventid: 6005") || // Event log started
                message.contains("eventid: 6006")) { // Event log stopped
            severity = "INFO";
            category = "SYSTEM & SERVICES";
        }

        // --- POLICY & AUDIT ---
        else if (message.contains("audit") || message.contains("auditd") || message.contains("policy") ||
                message.contains("group") || message.contains(".local") || message.contains("gpo") ||
                message.contains("eventid: 4719") || // System audit policy changed
                message.contains("eventid: 4670")) { // Permissions on an object were changed
            severity = "INFO";
            category = "POLICY & AUDIT";
        }

        // --- NETWORK ---
        else if (message.contains("tcp") || message.contains("udp") || message.contains("port") ||
                message.contains("ip ") || message.contains("connection") || message.contains("packet") ||
                message.contains("dns") || message.contains("dhcp") || message.contains("icmp") ||
                message.contains("firewall") || message.contains("iptables")) {
            severity = "INFO";
            category = "NETWORK";
        }

        // --- DEFAULT ---
        else if (category.equals("UNCATEGORIZED")) {
            severity = "INFO";
            category = "UNCATEGORIZED";
        }

        // New categorized log object
        return new LogObject(timestamp, source, severity, category, pid, message);
    }
}
