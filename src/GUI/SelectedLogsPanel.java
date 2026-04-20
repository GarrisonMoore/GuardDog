package GUI;

import SentryStack.LogObject;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * SelectedLogsPanel displays a table of logs filtered by the user's selection in the sidebar.
 */
public class SelectedLogsPanel extends JPanel {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DefaultTableModel logTableModel = new DefaultTableModel(
            new Object[]{"Timestamp", "Host", "Severity", "Category", "PID", "Message"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    // Tracks the last known state so we don't do useless redraws
    private int lastLogCount = -1;
    private String lastFilter = "";

    private final JTable selectedLogTable = new JTable(logTableModel) {
        // Override the native tooltip method instead of using a motion listener
        @Override
        public String getToolTipText(java.awt.event.MouseEvent e) {
            java.awt.Point p = e.getPoint();
            int row = rowAtPoint(p);
            int col = columnAtPoint(p);

            // Only show the HTML tooltip if hovering over the Message column (index 5)
            if (row >= 0 && col == 5) {
                Object value = getValueAt(row, col);
                if (value != null) {
                    return "<html><body style='width: 400px;'>" + value.toString() + "</body></html>";
                }
            }
            // Default behavior for other columns
            return super.getToolTipText(e);
        }
    };

    private final JScrollPane selectedScroll = new JScrollPane(selectedLogTable);
    private static final JTextField logSearchField = new JTextField();
    private final GUI parent;

    public SelectedLogsPanel(GUI parent) {
        this.parent = parent;
        setLayout(new BorderLayout());
        setOpaque(false);
        initComponents();
    }

    private void initComponents() {
        selectedScroll.setBorder(BorderFactory.createLineBorder(GUIConstants.BORDER_COLOR, 1));
        selectedScroll.putClientProperty("JComponent.arc", 12);
        selectedScroll.getVerticalScrollBar().setUnitIncrement(16);

        logSearchField.setFont(GUIConstants.MAIN_FONT);
        logSearchField.putClientProperty("JTextField.placeholderText", "Search within logs (e.g. error, 404)...");
        logSearchField.putClientProperty("JComponent.outline", GUIConstants.ACCENT_COLOR);
        logSearchField.setPreferredSize(new Dimension(515, 35));

        logSearchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                parent.refreshDisplay();
                parent.refreshLiveFeed();
            }

            public void removeUpdate(DocumentEvent e) {
                parent.refreshDisplay();
                parent.refreshLiveFeed();
            }

            public void changedUpdate(DocumentEvent e) {
                parent.refreshDisplay();
                parent.refreshLiveFeed();
            }
        });

        configureLogTable(selectedLogTable);

        add(selectedScroll, BorderLayout.CENTER);
    }

    private void configureLogTable(JTable table) {
        table.setBackground(GUIConstants.LOG_BG);
        table.setForeground(Color.WHITE);
        table.setGridColor(GUIConstants.BORDER_COLOR);
        table.getTableHeader().setBackground(GUIConstants.PANEL_BG);
        table.getTableHeader().setForeground(Color.WHITE);
        table.setSelectionBackground(new Color(30, 80, 150));
        table.setSelectionForeground(Color.WHITE);
        table.setFont(GUIConstants.MAIN_FONT);
        table.setDefaultRenderer(Object.class, new LogSeverityRenderer());

        // Ensure "Message" column is wider
        table.getColumnModel().getColumn(5).setPreferredWidth(600);
    }

    /**
     * Updates the data in the table with a new list of logs.
     * @param logs The list of logs to display.
     */
    public void renderLogs(List<LogObject> logs) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> renderLogs(logs));
            return;
        }

        String filter = logSearchField.getText().trim().toLowerCase();

        // THE CPU SAVER: If the data size is identical and the filter hasn't changed, DO NOTHING.
        if (logs.size() == lastLogCount && filter.equals(lastFilter)) {
            return;
        }

        // Update the trackers for next time
        lastLogCount = logs.size();
        lastFilter = filter;

        // --- 1. THE SNAPSHOT LIST ---
        // We make a quick copy of the incoming 'logs' to prevent the
        // background thread from crashing the UI if it adds a new log right now.
        List<LogObject> snapshotList = new ArrayList<>(logs);

        // --- 2. UI OPTIMIZATION ---
        // Build a temporary 2D array to hold the data so we only update the table ONCE
        Object[][] tableData = new Object[snapshotList.size()][6];
        int addedCount = 0;

        for (LogObject log : snapshotList) {
            if (filter.isEmpty() ||
                    log.getMessage().toLowerCase().contains(filter) ||
                    log.getSource().toLowerCase().contains(filter) ||
                    log.getCategory().toLowerCase().contains(filter) ||
                    log.getSeverity().toLowerCase().contains(filter)) {

                tableData[addedCount] = new Object[]{
                        formatTimestamp(log),  // (Make sure you also added the static DATE_FORMATTER fix for this!)
                        log.getSource(),
                        log.getSeverity(),
                        log.getCategory(),
                        log.getPid(),
                        log.getMessage()
                };
                addedCount++;
            }
        }
        // Trim the array to exact size if the filter removed anything
        Object[][] finalData = java.util.Arrays.copyOf(tableData, addedCount);

        // Push all data to the UI at exactly the same time. This fires only ONE event!
        logTableModel.setDataVector(finalData, new Object[]{"Timestamp", "Host", "Severity", "Category", "PID", "Message"});

        // Re-apply column sizing if setDataVector overwrites them
        selectedLogTable.getColumnModel().getColumn(5).setPreferredWidth(600);
    }

    /**
     * Formats the timestamp of a log object for display.
     * @param log The log object.
     * @return A formatted timestamp string.
     */
    private String formatTimestamp(LogObject log) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(log.getTimestamp()), ZoneId.systemDefault())
                .format(DATE_FORMATTER);
    }

    /**
     * Clears the current table data and resets tracking state.
     */
    public void clearMemory() {
        // Reset the short-circuit trackers so the UI doesn't ignore the next load
        lastLogCount = -1;
        lastFilter = "";

        // Instantly wipe the table model to detach the old data from the UI
        logTableModel.setRowCount(0);
    }

    /**
     * Returns the JTable used to display the logs.
     * @return The JTable instance.
     */
    public JTable getSelectedLogTable() {
        return selectedLogTable;
    }

    public static JTextField getSearchField() {
        return logSearchField;
    }

    /**
     * Returns the scroll pane containing the table.
     * @return The JScrollPane instance.
     */
    public JScrollPane getScroll() {
        return selectedScroll;
    }
}
