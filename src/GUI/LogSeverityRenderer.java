package GUI;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public class LogSeverityRenderer extends DefaultTableCellRenderer {
    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (isSelected) {
            c.setBackground(table.getSelectionBackground());
            c.setForeground(table.getSelectionForeground());
        } else {
            c.setBackground(table.getBackground());
            // Find the "Severity" column index. In both panels it's index 2.
            // But let's be safe and check the model.
            String severity = table.getModel().getValueAt(row, 2).toString().toUpperCase();

            switch (severity) {
                case "CRIT":
                    c.setForeground(GUIConstants.CRIT_COLOR);
                    break;
                case "WARN":
                    c.setForeground(GUIConstants.WARN_COLOR);
                    break;
                case "INFO":
                default:
                    c.setForeground(GUIConstants.INFO_COLOR);
                    break;
            }
        }
        return c;
    }
}
