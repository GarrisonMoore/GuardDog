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
    private final DefaultTableModel logTableModel = new DefaultTableModel(
            new Object[]{"Timestamp", "Host", "Severity", "Category", "PID", "Message"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable selectedLogTable = new JTable(logTableModel);
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
            public void insertUpdate(DocumentEvent e) { parent.refreshDisplay(); parent.refreshLiveFeed(); }
            public void removeUpdate(DocumentEvent e) { parent.refreshDisplay(); parent.refreshLiveFeed(); }
            public void changedUpdate(DocumentEvent e) { parent.refreshDisplay(); parent.refreshLiveFeed(); }
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
        
        // Add tooltips to show full message
        table.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row >= 0 && col == 5) {
                    Object value = table.getValueAt(row, col);
                    if (value != null) {
                        table.setToolTipText("<html><body style='width: 400px;'>" + value.toString() + "</body></html>");
                    }
                } else {
                    table.setToolTipText(null);
                }
            }
        });
    }

    public void renderLogs(List<LogObject> logs) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> renderLogs(logs));
            return;
        }

        String filter = logSearchField.getText().trim().toLowerCase();
        logTableModel.setRowCount(0);
        // Create a quick snapshot of the list to prevent ConcurrentModificationException
        List<LogObject> snapshotList = new ArrayList<>(someListOfLogs);

        for (LogObject log : snapshotList) {
            if (filter.isEmpty() || 
                log.getMessage().toLowerCase().contains(filter) ||
                log.getSource().toLowerCase().contains(filter) ||
                log.getCategory().toLowerCase().contains(filter) ||
                log.getSeverity().toLowerCase().contains(filter)) {
                
                logTableModel.addRow(new Object[]{
                        formatTimestamp(log),
                        log.getSource(),
                        log.getSeverity(),
                        log.getCategory(),
                        log.getPid(),
                        log.getMessage()
                });
            }
        }
    }

    private String formatTimestamp(LogObject log) {
        LocalDateTime date = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(log.getTimestamp()),
                ZoneId.systemDefault()
        );
        return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    public static JTextField getSearchField() {
        return logSearchField;
    }

    public JScrollPane getScroll() {
        return selectedScroll;
    }
}
