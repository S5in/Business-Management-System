import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class ProductWindow extends JDialog {

    private JTable table;
    private DefaultTableModel model;

    public ProductWindow(JFrame parent) {
        super(parent, "Manage Products", true);
        setSize(800, 400);
        setLocationRelativeTo(parent);

        model = new DefaultTableModel(new String[]{
                "ID", "Name", "Amount Left", "Price", "Rate"
        }, 0);
        table = new JTable(model);
        table.setDefaultEditor(Object.class, null);  // Disable inline editing for all columns

        // Hide the "ID" column
        table.getColumnModel().removeColumn(table.getColumnModel().getColumn(0));

        loadProducts();

        JButton btnAdd = new JButton("Add Product");
        JButton btnEdit = new JButton("Edit Selected");
        JButton btnDelete = new JButton("Delete Selected");

        btnAdd.addActionListener(_ -> addProduct());
        btnEdit.addActionListener(_ -> editSelectedProduct());
        btnDelete.addActionListener(_ -> deleteSelectedProduct());

        JPanel btnPanel = new JPanel();
        btnPanel.add(btnAdd);
        btnPanel.add(btnEdit);
        btnPanel.add(btnDelete);

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);

        setVisible(true);
    }

    private void loadProducts() {
        model.setRowCount(0);
        try (Connection conn = db.DatabaseManager.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT Product_ID, Name, AmountLeft, Price, Rate FROM Product")) {

            while (rs.next()) {
                model.addRow(new Object[]{
                        rs.getInt("Product_ID"),
                        rs.getString("Name"),
                        rs.getInt("AmountLeft"),
                        rs.getDouble("Price"),
                        rs.getDouble("Rate")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void addProduct() {
        JTextField nameField = new JTextField();
        JTextField priceField = new JTextField();
        JTextField rateField = new JTextField();

        JPanel panel = new JPanel(new GridLayout(3, 2));
        panel.add(new JLabel("Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Price:"));
        panel.add(priceField);
        panel.add(new JLabel("Rate:"));
        panel.add(rateField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Product", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();

            try (Connection conn = db.DatabaseManager.connect()) {
                conn.setAutoCommit(false);

                PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO Product(Name, AmountLeft, Price, Rate) VALUES (?, 0, ?, ?)");
                stmt.setString(1, name);
                stmt.setDouble(2, Double.parseDouble(priceField.getText()));
                stmt.setDouble(3, Double.parseDouble(rateField.getText()));
                stmt.executeUpdate();

                conn.commit();
                loadProducts();
            } catch (SQLException | NumberFormatException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Invalid input or database error.");
            }
        }
    }

    private void editSelectedProduct() {
        int selected = table.getSelectedRow();
        if (selected == -1) {
            JOptionPane.showMessageDialog(this, "Please select a product to edit.");
            return;
        }

        int productId = (int) model.getValueAt(selected, 0);
        String currentName = model.getValueAt(selected, 1).toString();
        int currentAmount = (int) model.getValueAt(selected, 2);
        double currentPrice = (double) model.getValueAt(selected, 3);
        double currentRate = (double) model.getValueAt(selected, 4);

        JTextField nameField = new JTextField(currentName);
        JTextField amountField = new JTextField(String.valueOf(currentAmount));
        JTextField priceField = new JTextField(String.valueOf(currentPrice));
        JTextField rateField = new JTextField(String.valueOf(currentRate));

        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(new JLabel("Name:"));
        panel.add(nameField);
        panel.add(new JLabel("Amount Left:"));
        panel.add(amountField);
        panel.add(new JLabel("Price:"));
        panel.add(priceField);
        panel.add(new JLabel("Rate:"));
        panel.add(rateField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Edit Product", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            try (Connection conn = db.DatabaseManager.connect()) {
                conn.setAutoCommit(false);

                PreparedStatement stmt = conn.prepareStatement(
                        "UPDATE Product SET Name = ?, AmountLeft = ?, Price = ?, Rate = ? WHERE Product_ID = ?");
                stmt.setString(1, nameField.getText().trim());
                stmt.setInt(2, Integer.parseInt(amountField.getText()));
                stmt.setDouble(3, Double.parseDouble(priceField.getText()));
                stmt.setDouble(4, Double.parseDouble(rateField.getText()));
                stmt.setInt(5, productId);
                stmt.executeUpdate();

                conn.commit();
                loadProducts();
            } catch (SQLException | NumberFormatException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Failed to update product.");
            }
        }
    }

    private void deleteSelectedProduct() {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "Please select one or more products to delete.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete the selected product(s)?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try (Connection conn = db.DatabaseManager.connect()) {
            conn.setAutoCommit(false);
            PreparedStatement stmt = conn.prepareStatement("DELETE FROM Product WHERE Product_ID = ?");

            // Map from view index to model index since the ID column is hidden
            for (int viewRow : selectedRows) {
                int modelRow = table.convertRowIndexToModel(viewRow);
                int productId = (int) model.getValueAt(modelRow, 0);  // Column 0 in model is the hidden ID
                stmt.setInt(1, productId);
                stmt.addBatch();
            }

            stmt.executeBatch();
            conn.commit();
            loadProducts();
            JOptionPane.showMessageDialog(this, "Selected product(s) deleted successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Failed to delete product(s).");
        }
    }
}
