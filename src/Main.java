import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class Main extends IndexingEngine {

    private static final Path LOG_FILE = Paths.get("/var/log/windows_5141.log");

    public static void main(String[] args) throws InterruptedException {
        FlatDarkLaf.setup();

        SwingUtilities.invokeLater(() -> {
            new GUI();
        });

        Thread logThread = new Thread(() -> tailFile(LOG_FILE), "log-tail");
        logThread.setDaemon(true);
        logThread.start();

        scheduleGuiRefresh();
    }

    private static void scheduleGuiRefresh() {
        new javax.swing.Timer(500, e -> {
            GUI g = GUI.getMyGui();
            if (g != null) {
                SwingUtilities.invokeLater(() -> {
                    g.setHosts(HostIndex.keySet());
                    g.refreshDisplay();
                });
            }
        }).start();
    }
}
