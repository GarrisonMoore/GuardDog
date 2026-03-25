import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main extends IndexingEngine {

    private static final Path LOG_FILE = Paths.get("/var/log/windows_5141.log");

    public static void main(String[] args) throws InterruptedException {
        FlatDarkLaf.setup();

        SwingUtilities.invokeLater(() -> {
            myGui = new GUI();
            myGui.setHosts(IndexingEngine.HostIndex.keySet());
        });

        Thread logThread = new Thread(() -> tailFile(LOG_FILE), "log-tail");
        logThread.setDaemon(true);
        logThread.start();

        scheduleGuiRefresh();
    }

    private static void scheduleGuiRefresh() {
        new javax.swing.Timer(500, e -> {

            if (myGui != null) {
                SwingUtilities.invokeLater(() -> {
                    myGui.setHosts(HostIndex.keySet());
                    myGui.refreshDisplay();
                });
            }
        }).start();
    }
}
