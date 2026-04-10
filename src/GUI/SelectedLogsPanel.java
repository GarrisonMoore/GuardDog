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

public class SelectedLogsPanel extends JPanel {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static class LogTableModel extends javax.swing.table.AbstractTableModel {
        private final String[] columnNames = {"Timestamp", "Host", "Severity", "Category", "PID", "Message"};
        private List<LogObject> logs = new ArrayList<>();
        private List<LogObject> filteredLogs = new ArrayList<>();
        private String filter = "";

        public void setLogs(List<LogObject> logs, String filter) {
            this.logs = logs;
            this.filter = filter.toLowerCase();
            applyFilter();
        }

        private void applyFilter() {
            if (filter.isEmpty()) {
                filteredLogs = new ArrayList<>(logs);
            } else {
                filteredLogs = new ArrayList<>();
                for (LogObject log : logs) {
                    if (log.getMessage().toLowerCase().contains(filter) ||
                            log.getSource().toLowerCase().contains(filter) ||
                            log.getCategory().toLowerCase().contains(filter) ||
                            log.getSeverity().toLowerCase().contains(filter)) {
                        filteredLogs.add(log);
                    }
                }
            }
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return filteredLogs.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (rowIndex < 0 || rowIndex >= filteredLogs.size()) return null;
            LogObject log = filteredLogs.get(rowIndex);
            switch (columnIndex) {
                case 0: return formatTimestamp(log);
                case 1: return log.getSource();
                case 2: return log.getSeverity();
                case 3: return log.getCategory();
                case 4: return log.getPid();
                case 5: return log.getMessage();
                default: return null;
            }
        }

        private String formatTimestamp(LogObject log) {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(log.getTimestamp()), ZoneId.systemDefault())
                    .format(DATE_FORMATTER);
        }

        public void clear() {
            this.logs = new ArrayList<>();
            this.filteredLogs = new ArrayList<>();
            fireTableDataChanged();
        }
    }

    private final LogTableModel logTableModel = new LogTableModel();

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

        // Push all data to the UI.
        logTableModel.setLogs(logs, filter);

        // Re-apply column sizing if model update overwrites them
        selectedLogTable.getColumnModel().getColumn(5).setPreferredWidth(600);
    }

    private String formatTimestamp(LogObject log) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(log.getTimestamp()), ZoneId.systemDefault())
                .format(DATE_FORMATTER);
    }

    public void clearMemory() {
        // Reset the short-circuit trackers so the UI doesn't ignore the next load
        lastLogCount = -1;
        lastFilter = "";

        // Instantly wipe the table model to detach the old data from the UI
        logTableModel.clear();
    }

    public JTable getSelectedLogTable() {
        return selectedLogTable;
    }

    public static JTextField getSearchField() {
        return logSearchField;
    }

    public JScrollPane getScroll() {
        return selectedScroll;
    }
}
