package Interfaces;

import SentryStack.LogObject;

/**
 * Interfaces.LogParser is an interface that defines the contract for parsing log entries.
 * Classes implementing this interface should provide mechanisms to determine
 * whether a given log entry can be parsed and to convert the raw log entry
 * into a structured log object.
 */
public abstract interface ParserMaster {

    // Validate if the line can be parsed by our parsers
    boolean canParse(String rawline);

    // Turn this line into a SentryStack.LogObject
    LogObject parse(String rawline);

}
