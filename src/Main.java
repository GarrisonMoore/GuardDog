import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.awt.Color;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public class Main extends IndexingEngine {

    // Path to the Windows Event Log file
    private static final Path LOG_FILE = Paths.get("/var/log/windows_5141.log");

    public static void main(String[] args) throws InterruptedException {
        // Using FlatDarkLaf for a modern look
        FlatDarkLaf.setup();

        ReadmeGUI readmeGUI = new ReadmeGUI();
        readmeGUI.getGUI();

        // Global UI tweaks for a modern, cleaner look
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("Button.arc", 12);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.trackArc", 999);
        UIManager.put("TabbedPane.selectedBackground", new Color(40, 40, 40));
        UIManager.put("TabbedPane.tabHeight", 45);
        UIManager.put("TabbedPane.underlineColor", new Color(0, 150, 255)); // Electric Blue
        UIManager.put("TabbedPane.selectedForeground", Color.WHITE);
        UIManager.put("TabbedPane.hoverBackground", new Color(45, 45, 45));
        UIManager.put("List.selectionBackground", new Color(30, 80, 150));
        UIManager.put("List.selectionForeground", Color.WHITE);
        UIManager.put("ComboBox.selectionBackground", new Color(30, 80, 150));
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("Separator.foreground", new Color(50, 50, 50));
        UIManager.put("Panel.background", new Color(25, 25, 25));
        UIManager.put("List.background", new Color(20, 20, 20));
        UIManager.put("TextPane.background", new Color(15, 15, 15));
        UIManager.put("ComboBox.background", new Color(35, 35, 35));
        UIManager.put("TextField.background", new Color(35, 35, 35));

        SwingUtilities.invokeLater(() -> {
            new GUI();
        });

        // Start indexing the log file in a separate thread
        Thread logThread = new Thread(() -> tailFile(LOG_FILE), "log-tail");
        logThread.setDaemon(true);
        logThread.start();

        scheduleGuiRefresh();
    }

    // Schedule a GUI refresh every 500ms
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
