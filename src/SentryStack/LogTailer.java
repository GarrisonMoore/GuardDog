package SentryStack;

import GUI.GUI;
import Interfaces.ParserMaster;
import Parsers.BSD.BSDparser;
import Parsers.Heuristic.HeuristicParser;
import Parsers.JSON.JSONParser;
import Parsers.Syslog.SyslogParser;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * LogTailer is responsible for monitoring a log file and reading new entries as they are appended.
 * It uses a list of registered parsers to convert raw lines into {@link LogObject} instances.
 */
public class LogTailer {

    private static final List<ParserMaster> parsers = new ArrayList<>();

    static {
        parsers.add(new SyslogParser());
        parsers.add(new JSONParser());
        parsers.add(new BSDparser());
        // Use HeuristicParser as the last parser to catch any remaining logs
        parsers.add(new HeuristicParser());
    }

    /**
     * Tails the specified file in a loop, reading new lines and passing them to the indexing engine.
     *
     * @param file The path to the log file to monitor.
     */
    public static void tailFile(Path file) {
        try {
            // make sure the file exists before we start reading
            while (!Files.exists(file)) {
                Thread.sleep(500);
            }

            // start reading from the end of the file by default to prevent memory exhaustion on large logs
            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
                long fileLength = raf.length();
                // Seek to the end of the file.
                raf.seek(fileLength);

                // read the file line by line as new content is appended
                while (true) {
                    String line = raf.readLine();
                    if (line == null) {
                        if (raf.length() < raf.getFilePointer()) {
                            // File was truncated
                            raf.seek(0);
                        } else {
                            Thread.sleep(250);
                        }
                        continue;
                    }

                    // Decode ISO-8859-1 to UTF-8
                    line = new String(line.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), java.nio.charset.StandardCharsets.UTF_8);

                    // Try each parser
                    for (ParserMaster parser : parsers) {
                        if (parser.canParse(line)) {
                            LogObject log = parser.parse(line);
                            if (log != null) {
                                IndexingEngine.indexLog(log);
                                // update GUI
                                if (GUI.getMyGui() != null) {
                                    GUI.getMyGui().appendLiveLog(log);
                                }
                                break; // Found a parser, move to next line
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Log tail stopped: " + e.getMessage());
        }
    }
}
