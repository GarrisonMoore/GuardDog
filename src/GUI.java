import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/// bruh
public class GUI extends JFrame {
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> hostList = new JList<>(listModel);
    private final JTextArea logDisplay = new JTextArea();

    public GUI() {
        setTitle("Watch Dog NOC - Phase 2");
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // High-Contrast NOC Styles
        Font nocFont = new Font("Monospaced", Font.BOLD, 18);
        hostList.setFont(nocFont);
        hostList.setBackground(Color.BLACK);
        hostList.setForeground(Color.GREEN);

        logDisplay.setFont(nocFont);
        logDisplay.setBackground(Color.BLACK);
        logDisplay.setForeground(Color.WHITE);
        logDisplay.setEditable(false);

        JTextField searchField = new JTextField();
        searchField.setFont(new Font("Monospaced", Font.BOLD, 18));
        searchField.setBackground(Color.DARK_GRAY);
        searchField.setForeground(Color.WHITE);

        // Create the dropdown before using it
        String[] pivots = {"Hostnames", "Severity", "Time Window"};
        JComboBox<String> pivotBox = new JComboBox<>(pivots);
        pivotBox.setFont(new Font("Monospaced", Font.BOLD, 18));

        // Add a DocumentListener to the search field
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            public void changedUpdate(DocumentEvent e) {
                filter();
            }

            private void filter() {
                String query = searchField.getText().toLowerCase();
                String currentPivot = (String) pivotBox.getSelectedItem();
                listModel.clear();

                if ("Hostnames".equals(currentPivot)) {
                    for (String host : ProcessingEngine.getHostKeys()) {
                        if (host.toLowerCase().contains(query)) {
                            listModel.addElement(host);
                        }
                    }
                }
                else if ("Severity".equals(currentPivot)) {
                    String[] severities = {"INFO", "WARN", "CRIT"};
                    for (String sev : severities) {
                        if (sev.toLowerCase().contains(query)) {
                            listModel.addElement(sev);
                        }
                    }
                }
                else if ("Time Window".equals(currentPivot)) {
                    String[] windows = {"Last 5 Minutes", "Last 30 Minutes", "Last Hour"};
                    for (String window : windows) {
                        if (window.toLowerCase().contains(query)) {
                            listModel.addElement(window);
                        }
                    }
                }
            }
        });

        // Selection Logic: fetch logs for the selected host
        hostList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return; // Ignore the mid-click event

            String selected = hostList.getSelectedValue();
            String currentPivot = (String) pivotBox.getSelectedItem();
            if (selected == null) return;

            List<LogObject> logs = new ArrayList<>();

            // Logic based on the selected Pivot
            switch (currentPivot) {
                case "Hostnames":
                    logs = ProcessingEngine.getLogsForHost(selected);
                    break;

                case "Severity":
                    logs = ProcessingEngine.getLogsBySeverity(selected);
                    break;

                case "Time Window":
                    int mins = 0;
                    if (selected.equalsIgnoreCase("Last Hour")) {
                        mins = 60; // Explicitly set 60 minutes
                    } else {
                        String numericOnly = selected.replaceAll("\\D+", "");
                        if (!numericOnly.isEmpty()) {
                            mins = Integer.parseInt(numericOnly);
                        }
                    }

                    if (mins > 0) {
                        logs = ProcessingEngine.getLogsByTime(mins);
                    }
                    break;
            }

            // Display the results
            logDisplay.setText("");
            for (LogObject log : logs) {
                logDisplay.append(log.toString() + "\n");
            }
        });

        // Now it is safe to attach the listener
        pivotBox.addActionListener(e -> {
            String selected = (String) pivotBox.getSelectedItem();
            listModel.clear();

            if ("Hostnames".equals(selected)) {
                for (String host : ProcessingEngine.getHostKeys()) {
                    listModel.addElement(host);
                }
            } else if ("Severity".equals(selected)) {
                listModel.addElement("INFO");
                listModel.addElement("WARN");
                listModel.addElement("CRIT");
            } else if ("Time Window".equals(selected)) {
                listModel.addElement("Last 5 Minutes");
                listModel.addElement("Last 30 Minutes");
                listModel.addElement("Last Hour");
            }
        });

        // UI layout

        // --- UI LAYOUT & BORDERS ---

        // 1. Stack the Top Bar (Dropdown on top, Search below)
        // GridLayout(rows, columns, horizontalGap, verticalGap)
        JPanel topBar = new JPanel(new GridLayout(2, 1, 0, 8));
        topBar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Padding around the edges
        topBar.add(pivotBox);
        topBar.add(searchField);

        // 2. Create a standardized NOC Border style
        javax.swing.border.Border lineBorder = BorderFactory.createLineBorder(Color.DARK_GRAY, 2);
        Font borderFont = new Font("Monospaced", Font.BOLD, 14);

        // Wrap the list in a ScrollPane and give it a Titled Border
        JScrollPane listScroll = new JScrollPane(hostList);
        listScroll.setBorder(BorderFactory.createTitledBorder(
                lineBorder, " Navigation ",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                borderFont, Color.LIGHT_GRAY));

        // Wrap the text area in a ScrollPane and give it a Titled Border
        JScrollPane logScroll = new JScrollPane(logDisplay);
        logScroll.setBorder(BorderFactory.createTitledBorder(
                lineBorder, " Log Stream Output ",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                borderFont, Color.LIGHT_GRAY));

        // 3. Assemble the Sidebar
        JPanel sidePanel = new JPanel(new BorderLayout());
        sidePanel.setPreferredSize(new Dimension(300, 0)); // Slightly wider for the borders
        sidePanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 5)); // Left/Bottom margins
        sidePanel.add(listScroll, BorderLayout.CENTER);

        // 4. Assemble the Main Center Panel (to add matching margins)
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 10, 10)); // Right/Bottom margins
        centerPanel.add(logScroll, BorderLayout.CENTER);

        // 5. Add everything to the main JFrame
        add(topBar, BorderLayout.NORTH);
        add(sidePanel, BorderLayout.WEST);
        add(centerPanel, BorderLayout.CENTER);

        setVisible(true);
    }

    public void setHosts(Set<String> hosts) {
        listModel.clear();
        for (String h : hosts) {
            listModel.addElement(h);
        }
    }

    private void updateDisplay(String host) {
        // Implementation for the next step...
    }
}