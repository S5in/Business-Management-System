import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;

public class ClientWindow extends JDialog {

    private JTable table;
    private DefaultTableModel model;

    public ClientWindow(JFrame parent) {
        super(parent, "Manage Clients", true);
        setSize(600, 400);
        setLocationRelativeTo(parent);

        // Step 1: Change table model to include "ID" and hide it
        model = new DefaultTableModel(new String[]{
            "ID", "Name", "Preferences", "Purchases"
        }, 0);
        table = new JTable(model);
        table.setDefaultEditor(Object.class, null);  // Disable inline editing for all columns
        table.getColumnModel().removeColumn(table.getColumnModel().getColumn(0)); // Hide ID column
        loadClients();

        JButton btnAdd = new JButton("Add");
        JButton btnEdit = new JButton("Edit Selected");
        btnEdit.addActionListener(_ -> editSelectedClient());
        JButton btnDelete = new JButton("Delete");

        btnAdd.addActionListener(_ -> addClient());
        btnDelete.addActionListener(_ -> deleteSelectedClient());

        JPanel btnPanel = new JPanel();
        btnPanel.add(btnAdd);
        btnPanel.add(btnEdit);
        btnPanel.add(btnDelete);

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);

        setVisible(true);
    }

    // Step 2: Update loadClients() to fetch Client_ID
    private void loadClients() {
        model.setRowCount(0); // Clear the existing rows
        try (Connection conn = db.DatabaseManager.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT Client_ID, Name, Preferences, NumberOfPurchases FROM Client ORDER BY Name ASC")) {

            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getInt("Client_ID"),
                    rs.getString("Name"),
                    rs.getString("Preferences"),
                    rs.getInt("NumberOfPurchases")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void addClient() {
        JTextField nameField = new JTextField();
        JComboBox<String> preferencesBox = new JComboBox<>();

        try (Connection conn = db.DatabaseManager.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT Name FROM Product")) {
            ArrayList<String> productNames = new ArrayList<>();
            while (rs.next()) {
                productNames.add(rs.getString("Name"));
            }
            preferencesBox.setModel(new DefaultComboBoxModel<>(productNames.toArray(new String[0])));
        } catch (SQLException e) {
            e.printStackTrace();
        }

        JPanel panel = new JPanel(new GridLayout(3, 2));
        panel.add(new JLabel("Name:")); panel.add(nameField);
        panel.add(new JLabel("Preferences:")); panel.add(preferencesBox);

        int result = JOptionPane.showConfirmDialog(this, panel, "Add Client", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            Object selectedPref = preferencesBox.getSelectedItem();

            if (name.isEmpty() || selectedPref == null) {
                JOptionPane.showMessageDialog(this, "Please enter a name and select preferences.");
                return;
            }

            try (Connection conn = db.DatabaseManager.connect()) {
                PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO Client (Name, Preferences, NumberOfPurchases) VALUES (?, ?, ?)"
                );
                stmt.setString(1, name);
                stmt.setString(2, selectedPref.toString());
                stmt.setInt(3, 0);

                int rows = stmt.executeUpdate();
                if (rows == 0) {
                    JOptionPane.showMessageDialog(this, "Nothing was added/updated. Possible duplicate or constraint issue.");
                    conn.rollback();
                    return;
                }
                conn.commit();

                loadClients();
            } catch (SQLException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error inserting client:\n" + e.getMessage());
            }
        }
    }

    // Step 4: Same for deleteSelectedClient()
    private void deleteSelectedClient() {
        int selected = table.getSelectedRow();
        if (selected == -1) return;

        int clientId = (int) model.getValueAt(selected, 0); // Use ID as the identifier
        try (Connection conn = db.DatabaseManager.connect()) {
            conn.setAutoCommit(false); // Disable auto-commit

            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM Client WHERE Client_ID = ?")) {
                stmt.setInt(1, clientId);
                stmt.executeUpdate();

                conn.commit(); // Commit the transaction
                loadClients();
            } catch (SQLException e) {
                conn.rollback(); // Rollback on error
                e.printStackTrace();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Step 3: In editSelectedClient(), fetch Client_ID from the hidden column and update SQL
    private void editSelectedClient() {
        int selected = table.getSelectedRow();
        if (selected == -1) {
            JOptionPane.showMessageDialog(this, "Please select a client to edit.");
            return;
        }

        int clientId = (int) model.getValueAt(selected, 0); // ID
        String currentName = (String) model.getValueAt(selected, 1);
        String currentPref = (String) model.getValueAt(selected, 2);
        int currentPurchases = (int) model.getValueAt(selected, 3);

        JTextField nameField = new JTextField(currentName);
        JComboBox<String> prefBox = new JComboBox<>();
        JTextField purchasesField = new JTextField(String.valueOf(currentPurchases));

        try (Connection conn = db.DatabaseManager.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT Name FROM Product")) {
            while (rs.next()) {
                prefBox.addItem(rs.getString("Name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        prefBox.setSelectedItem(currentPref);

        JPanel panel = new JPanel(new GridLayout(3, 2));
        panel.add(new JLabel("Name:")); panel.add(nameField);
        panel.add(new JLabel("Preferences:")); panel.add(prefBox);
        panel.add(new JLabel("Number of Purchases:")); panel.add(purchasesField);

        int result = JOptionPane.showConfirmDialog(this, panel, "Edit Client", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            try (Connection conn = db.DatabaseManager.connect()) {
                PreparedStatement stmt = conn.prepareStatement("""
                    UPDATE Client SET Name = ?, Preferences = ?, NumberOfPurchases = ?
                    WHERE Client_ID = ?
                """);
                stmt.setString(1, nameField.getText().trim());
                stmt.setString(2, (String) prefBox.getSelectedItem());
                stmt.setInt(3, Integer.parseInt(purchasesField.getText()));
                stmt.setInt(4, clientId); // Use ID for update
                stmt.executeUpdate();
                conn.commit();
                loadClients();
            } catch (SQLException | NumberFormatException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Failed to update client.");
            }
        }
    }
}
