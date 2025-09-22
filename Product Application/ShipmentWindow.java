import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;

public class ShipmentWindow extends JDialog {

    private JTable table;
    private DefaultTableModel model;
    private JComboBox<String> filterOptionBox;
    private JComboBox<String> filterProductBox;

    public ShipmentWindow(JFrame parent) {
        super(parent, "Manage Shipments", true);
        setSize(1100, 550);
        setLocationRelativeTo(parent);

        model = new DefaultTableModel(new String[]{"Shipment ID", "Deliveryman", "Delivery Date", "Gross Profit", "Revenue"}, 0);
        table = new JTable(model);
        table.setDefaultEditor(Object.class, null);  // Disable inline editing for all columns

        // Hide the "Shipment ID" column
        table.getColumnModel().removeColumn(table.getColumnModel().getColumn(0));

        loadShipments();

        JButton btnAdd = new JButton("Add Shipment");
        JButton btnView = new JButton("View Details");
        JButton btnEdit = new JButton("Edit Shipment");
        JButton btnDelete = new JButton("Delete Shipment");
        JButton btnFilter = new JButton("Apply Filter");

        filterOptionBox = new JComboBox<>(new String[]{"All", "Newest First", "Oldest First"});
        filterProductBox = new JComboBox<>();
        filterProductBox.addItem("All Products");
        try (Connection conn = db.DatabaseManager.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT Name FROM Product")) {
            while (rs.next()) {
                filterProductBox.addItem(rs.getString("Name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        btnAdd.addActionListener(_ -> addShipment());
        btnView.addActionListener(_ -> viewShipmentDetails());
        btnEdit.addActionListener(_ -> editShipment());
        btnDelete.addActionListener(_ -> deleteShipment());
        btnFilter.addActionListener(_ -> applyFilter());

        JPanel btnPanel = new JPanel();
        btnPanel.add(btnAdd);
        btnPanel.add(btnView);
        btnPanel.add(btnEdit);
        btnPanel.add(btnDelete);
        btnPanel.add(new JLabel("Filter by Product:"));
        btnPanel.add(filterProductBox);
        btnPanel.add(new JLabel("Sort by Date:"));
        btnPanel.add(filterOptionBox);
        btnPanel.add(btnFilter);

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);

        setVisible(true);
    }

    private void deleteShipment() {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "Please select one or more shipments to delete.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, "Delete selected shipments?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try (Connection conn = db.DatabaseManager.connect()) {
            conn.setAutoCommit(false); // Start transaction

            for (int row : selectedRows) {
                int shipmentId = (int) model.getValueAt(row, 0); // Hidden column (Shipment ID)

                try (PreparedStatement deleteItems = conn.prepareStatement("DELETE FROM ShipmentItem WHERE Shipment_ID = ?")) {
                    deleteItems.setInt(1, shipmentId);
                    deleteItems.executeUpdate();
                }

                try (PreparedStatement deleteShipment = conn.prepareStatement("DELETE FROM Shipment WHERE Shipment_ID = ?")) {
                    deleteShipment.setInt(1, shipmentId);
                    deleteShipment.executeUpdate();
                }
            }

            conn.commit();
            loadShipments();
            JOptionPane.showMessageDialog(this, "Selected shipments deleted successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error deleting shipments.");
        }
    }

    private void applyFilter() {
        model.setRowCount(0);
        String selectedProduct = (String) filterProductBox.getSelectedItem();
        String sortOrder = (String) filterOptionBox.getSelectedItem();
        boolean filterByProduct = selectedProduct != null && !selectedProduct.equals("All Products");

        StringBuilder sql = new StringBuilder("""
            SELECT DISTINCT
                s.Shipment_ID,
                s.Deliveryman,
                s.DeliveryDate,
                s.GrossProfit,
                s.Revenue
            FROM Shipment s
            LEFT JOIN ShipmentItem si ON s.Shipment_ID = si.Shipment_ID
            LEFT JOIN Product p ON si.Product_ID = p.Product_ID
            WHERE 1 = 1
        """);

        if (filterByProduct) {
            sql.append(" AND p.Name = ? ");
        }

        if (sortOrder.equals("Newest First")) {
            sql.append(" ORDER BY s.DeliveryDate DESC");
        } else if (sortOrder.equals("Oldest First")) {
            sql.append(" ORDER BY s.DeliveryDate ASC");
        }

        try (Connection conn = db.DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {

            if (filterByProduct) {
                stmt.setString(1, selectedProduct);
            }

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                model.addRow(new Object[]{
                        rs.getInt("Shipment_ID"),
                        rs.getString("Deliveryman"),
                        rs.getString("DeliveryDate"),
                        rs.getDouble("GrossProfit"),
                        rs.getDouble("Revenue")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadShipments() {
        model.setRowCount(0);
        String sql = "SELECT * FROM Shipment";

        try (Connection conn = db.DatabaseManager.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                model.addRow(new Object[]{
                        rs.getInt("Shipment_ID"),
                        rs.getString("Deliveryman"),
                        rs.getString("DeliveryDate"),
                        rs.getDouble("GrossProfit"),
                        rs.getDouble("Revenue")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void viewShipmentDetails() {
        int row = table.getSelectedRow();
        if (row == -1) return;
        int shipmentId = (int) model.getValueAt(row, 0);

        DefaultTableModel detailModel = new DefaultTableModel(new String[]{"Product", "Quantity", "Rate", "Cost"}, 0);
        JTable detailTable = new JTable(detailModel);

        double deliveryExpense = 0;
        double equipmentExpense = 0;
        double employeeExpense = 0;

        try (Connection conn = db.DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement("""
                     SELECT Product.Name, ShipmentItem.Input, ShipmentItem.Rate, ShipmentItem.Cost
                     FROM ShipmentItem
                     JOIN Product ON ShipmentItem.Product_ID = Product.Product_ID
                     WHERE Shipment_ID = ?
                 """)) {
            stmt.setInt(1, shipmentId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                detailModel.addRow(new Object[]{
                        rs.getString("Name"),
                        rs.getInt("Input"),
                        rs.getDouble("Rate"),
                        rs.getDouble("Cost")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Fetch the expenses
        try (Connection conn = db.DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement("""
                     SELECT DeliveryExpense, EquipmentExpense, EmployeeExpense
                     FROM Shipment
                     WHERE Shipment_ID = ?
                 """)) {
            stmt.setInt(1, shipmentId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                deliveryExpense = rs.getDouble("DeliveryExpense");
                equipmentExpense = rs.getDouble("EquipmentExpense");
                employeeExpense = rs.getDouble("EmployeeExpense");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        JPanel expensePanel = new JPanel(new GridLayout(3, 2));
        expensePanel.add(new JLabel("Delivery Expense:"));
        expensePanel.add(new JLabel(String.valueOf(deliveryExpense)));
        expensePanel.add(new JLabel("Equipment Expense:"));
        expensePanel.add(new JLabel(String.valueOf(equipmentExpense)));
        expensePanel.add(new JLabel("Employee Expense:"));
        expensePanel.add(new JLabel(String.valueOf(employeeExpense)));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.add(new JScrollPane(detailTable));
        mainPanel.add(new JLabel("Expenses:"));
        mainPanel.add(expensePanel);

        JOptionPane.showMessageDialog(this, mainPanel, "Shipment Details", JOptionPane.INFORMATION_MESSAGE);
    }

    private void editShipment() {
        int row = table.getSelectedRow();
        if (row == -1) return;
        int shipmentId = (int) model.getValueAt(row, 0);

        JTextField deliverymanField = new JTextField(model.getValueAt(row, 1).toString());
        JTextField dateField = new JTextField(model.getValueAt(row, 2).toString());

        JTextField deliveryExpenseField = new JTextField();
        JTextField equipmentExpenseField = new JTextField();
        JTextField employeeExpenseField = new JTextField();

        // Fetch existing expenses
        try (Connection conn = db.DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement("""
                     SELECT DeliveryExpense, EquipmentExpense, EmployeeExpense
                     FROM Shipment
                     WHERE Shipment_ID = ?
                 """)) {
            stmt.setInt(1, shipmentId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                deliveryExpenseField.setText(String.valueOf(rs.getDouble("DeliveryExpense")));
                equipmentExpenseField.setText(String.valueOf(rs.getDouble("EquipmentExpense")));
                employeeExpenseField.setText(String.valueOf(rs.getDouble("EmployeeExpense")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        String[] columnNames = {"Product", "Quantity", "Rate"};
        DefaultTableModel itemModel = new DefaultTableModel(columnNames, 0);
        JTable itemTable = new JTable(itemModel);

        JComboBox<ProductItem> productBox = new JComboBox<>();
        try (Connection conn = db.DatabaseManager.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT Product_ID, Name FROM Product")) {
            while (rs.next()) {
                productBox.addItem(new ProductItem(rs.getInt("Product_ID"), rs.getString("Name")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        try (Connection conn = db.DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM ShipmentItem WHERE Shipment_ID = ?")) {
            stmt.setInt(1, shipmentId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int pid = rs.getInt("Product_ID");
                int qty = rs.getInt("Input");
                double rate = rs.getDouble("Rate");

                try (PreparedStatement nameStmt = conn.prepareStatement("SELECT Name FROM Product WHERE Product_ID = ?")) {
                    nameStmt.setInt(1, pid);
                    ResultSet nameRs = nameStmt.executeQuery();
                    if (nameRs.next()) {
                        String pname = nameRs.getString("Name");
                        itemModel.addRow(new Object[]{pid + " - " + pname, qty, rate});
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        JButton btnAddProduct = new JButton("Add Product");
        btnAddProduct.addActionListener(_ -> {
            JPanel inputPanel = new JPanel(new GridLayout(2, 2));
            JComboBox<ProductItem> prodSelect = new JComboBox<>(productBox.getModel());
            JTextField qtyField = new JTextField();

            inputPanel.add(new JLabel("Product:"));
            inputPanel.add(prodSelect);
            inputPanel.add(new JLabel("Quantity:"));
            inputPanel.add(qtyField);

            int result = JOptionPane.showConfirmDialog(this, inputPanel, "Add Product Item", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                String qtyText = qtyField.getText().trim();
                if (qtyText.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Please enter a quantity.");
                    return;
                }

                int qty;
                try {
                    qty = Integer.parseInt(qtyText);
                    if (qty <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this, "Quantity must be a positive number.");
                    return;
                }

                ProductItem selectedProduct = (ProductItem) prodSelect.getSelectedItem();
                int productId = selectedProduct.id;

                // Fetch rate from Product table
                double rate = 0;
                try (Connection conn = db.DatabaseManager.connect();
                     PreparedStatement pst = conn.prepareStatement("SELECT Rate FROM Product WHERE Product_ID = ?")) {
                    pst.setInt(1, productId);
                    try (ResultSet rs = pst.executeQuery()) {
                        if (rs.next()) rate = rs.getDouble("Rate");
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                if (qty <= 0 || rate <= 0) {
                    JOptionPane.showMessageDialog(this, "Invalid quantity or product has no valid rate.");
                    return;
                }

                itemModel.addRow(new Object[]{
                    prodSelect.getSelectedItem(),
                    qty,
                    rate  // Use the DB-driven rate
                });
            }
        });

        JPanel expensePanel = new JPanel(new GridLayout(3, 2));
        expensePanel.add(new JLabel("Delivery Expense:"));
        expensePanel.add(deliveryExpenseField);
        expensePanel.add(new JLabel("Equipment Expense:"));
        expensePanel.add(equipmentExpenseField);
        expensePanel.add(new JLabel("Employee Expense:"));
        expensePanel.add(employeeExpenseField);

        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        formPanel.add(new JLabel("Deliveryman:"));
        formPanel.add(deliverymanField);
        formPanel.add(new JLabel("Delivery Date:"));
        formPanel.add(dateField);
        formPanel.add(new JLabel("Products in Shipment:"));
        formPanel.add(new JScrollPane(itemTable));
        formPanel.add(btnAddProduct);
        formPanel.add(new JLabel("Expenses:"));
        formPanel.add(expensePanel);

        int result = JOptionPane.showConfirmDialog(this, formPanel, "Edit Shipment", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            try (Connection conn = db.DatabaseManager.connect()) {
                double deliveryExpense = Double.parseDouble(deliveryExpenseField.getText());
                double equipmentExpense = Double.parseDouble(equipmentExpenseField.getText());
                double employeeExpense = Double.parseDouble(employeeExpenseField.getText());

                // Update Shipment details
                PreparedStatement updateShipment = conn.prepareStatement("""
                        UPDATE Shipment
                        SET Deliveryman = ?, DeliveryDate = ?, DeliveryExpense = ?, EquipmentExpense = ?, EmployeeExpense = ?
                        WHERE Shipment_ID = ?
                    """);
                updateShipment.setString(1, deliverymanField.getText());
                updateShipment.setString(2, dateField.getText());
                updateShipment.setDouble(3, deliveryExpense);
                updateShipment.setDouble(4, equipmentExpense);
                updateShipment.setDouble(5, employeeExpense);
                updateShipment.setInt(6, shipmentId);
                updateShipment.executeUpdate();

                loadShipments();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void addShipment() {
        JTextField deliverymanField = new JTextField();
        JTextField dateField = new JTextField(LocalDate.now().toString());
        JTextField deliveryExpenseField = new JTextField("0");
        JTextField equipmentExpenseField = new JTextField("0");
        JTextField employeeExpenseField = new JTextField("0");

        // Predefined column names for table
        String[] columnNames = {"Product", "Quantity", "Rate"};
        DefaultTableModel itemModel = new DefaultTableModel(columnNames, 0);
        JTable itemTable = new JTable(itemModel);

        // Product selection dropdown
        JComboBox<ProductItem> productBox = new JComboBox<>();
        try (Connection conn = db.DatabaseManager.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT Product_ID, Name FROM Product")) {
            while (rs.next()) {
                productBox.addItem(new ProductItem(rs.getInt("Product_ID"), rs.getString("Name")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Add product button
        JButton btnAddProduct = new JButton("Add Product");
        btnAddProduct.addActionListener(_ -> {
            JPanel inputPanel = new JPanel(new GridLayout(2, 2));
            JComboBox<ProductItem> prodSelect = new JComboBox<>(productBox.getModel());
            JTextField qtyField = new JTextField();

            inputPanel.add(new JLabel("Product:"));
            inputPanel.add(prodSelect);
            inputPanel.add(new JLabel("Quantity:"));
            inputPanel.add(qtyField);

            int result = JOptionPane.showConfirmDialog(this, inputPanel, "Add Product Item", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                String qtyText = qtyField.getText().trim();
                if (qtyText.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Please enter a quantity.");
                    return;
                }

                int qty;
                try {
                    qty = Integer.parseInt(qtyText);
                    if (qty <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this, "Quantity must be a positive number.");
                    return;
                }

                ProductItem selectedProduct = (ProductItem) prodSelect.getSelectedItem();
                int productId = selectedProduct.id;

                // Fetch rate from Product table
                double rate = 0;
                try (Connection conn = db.DatabaseManager.connect();
                     PreparedStatement pst = conn.prepareStatement("SELECT Rate FROM Product WHERE Product_ID = ?")) {
                    pst.setInt(1, productId);
                    try (ResultSet rs = pst.executeQuery()) {
                        if (rs.next()) rate = rs.getDouble("Rate");
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                if (qty <= 0 || rate <= 0) {
                    JOptionPane.showMessageDialog(this, "Invalid quantity or product has no valid rate.");
                    return;
                }

                itemModel.addRow(new Object[]{
                    prodSelect.getSelectedItem(),
                    qty,
                    rate  // Use the DB-driven rate
                });
            }
        });

        // Expense panel
        JPanel expensePanel = new JPanel(new GridLayout(3, 2));
        expensePanel.add(new JLabel("Delivery Expense:"));
        expensePanel.add(deliveryExpenseField);
        expensePanel.add(new JLabel("Equipment Expense:"));
        expensePanel.add(equipmentExpenseField);
        expensePanel.add(new JLabel("Employee Expense:"));
        expensePanel.add(employeeExpenseField);

        // Main panel
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        formPanel.add(new JLabel("Deliveryman:"));
        formPanel.add(deliverymanField);
        formPanel.add(new JLabel("Delivery Date:"));
        formPanel.add(dateField);
        formPanel.add(expensePanel);
        formPanel.add(new JLabel("Products in Shipment:"));
        formPanel.add(new JScrollPane(itemTable));
        formPanel.add(btnAddProduct);

        int result = JOptionPane.showConfirmDialog(this, formPanel, "Add Shipment", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            // Validation: Check if deliveryman name is empty
            if (deliverymanField.getText().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please enter a deliveryman name.");
                return;
            }
            // Validation: Check if at least one product is added
            if (itemModel.getRowCount() == 0) {
                JOptionPane.showMessageDialog(this, "Please add at least one product to the shipment.");
                return;
            }

            try (Connection conn = db.DatabaseManager.connect()) {
                conn.setAutoCommit(false); // Ensure auto-commit is disabled

                double revenue = 0;
                double grossProfit = 0;
                double deliveryExpense = Double.parseDouble(deliveryExpenseField.getText());
                double equipmentExpense = Double.parseDouble(equipmentExpenseField.getText());
                double employeeExpense = Double.parseDouble(employeeExpenseField.getText());

                // Insert shipment record with expenses
                PreparedStatement insertShipment = conn.prepareStatement(
                        "INSERT INTO Shipment(Deliveryman, DeliveryDate, DeliveryExpense, EquipmentExpense, EmployeeExpense, GrossProfit, Revenue) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS);
                insertShipment.setString(1, deliverymanField.getText());
                insertShipment.setString(2, dateField.getText());
                insertShipment.setDouble(3, deliveryExpense);
                insertShipment.setDouble(4, equipmentExpense);
                insertShipment.setDouble(5, employeeExpense);
                insertShipment.setDouble(6, 0); // Temporary gross profit
                insertShipment.setDouble(7, 0); // Temporary revenue
                insertShipment.executeUpdate();

                ResultSet keys = insertShipment.getGeneratedKeys();
                int shipmentId = -1;
                if (keys.next()) {
                    shipmentId = keys.getInt(1);
                }

                // Calculate revenue and gross profit
                for (int i = 0; i < itemModel.getRowCount(); i++) {
                    ProductItem product = (ProductItem) itemModel.getValueAt(i, 0);
                    int productId = product.id;
                    int qty = Integer.parseInt(itemModel.getValueAt(i, 1).toString());
                    double rate = Double.parseDouble(itemModel.getValueAt(i, 2).toString());
                    double cost = qty * rate;

                    // Fetch sale price (Price) from the database
                    double salePrice = 0;
                    try (PreparedStatement getPrice = conn.prepareStatement("SELECT Price FROM Product WHERE Product_ID = ?")) {
                        getPrice.setInt(1, productId);
                        ResultSet rs = getPrice.executeQuery();
                        if (rs.next()) {
                            salePrice = rs.getDouble("Price");
                        }
                    }

                    revenue += salePrice * qty; // Revenue is based on sale price
                    grossProfit += (salePrice - rate) * qty; // Profit is the difference between sale price and rate

                    PreparedStatement insertItem = conn.prepareStatement(
                            "INSERT INTO ShipmentItem(Shipment_ID, Product_ID, Input, Rate, Cost) VALUES (?, ?, ?, ?, ?)");
                    insertItem.setInt(1, shipmentId);
                    insertItem.setInt(2, productId);
                    insertItem.setInt(3, qty);
                    insertItem.setDouble(4, rate);
                    insertItem.setDouble(5, cost);
                    insertItem.executeUpdate();

                    PreparedStatement updateStock = conn.prepareStatement(
                            "UPDATE Product SET AmountLeft = AmountLeft + ? WHERE Product_ID = ?");
                    updateStock.setInt(1, qty);
                    updateStock.setInt(2, productId);
                    updateStock.executeUpdate();
                }

                // Adjust gross profit with expenses
                grossProfit -= (deliveryExpense + equipmentExpense + employeeExpense);

                // Update the shipment with the final gross profit and revenue
                PreparedStatement updateShipmentProfit = conn.prepareStatement(
                        "UPDATE Shipment SET GrossProfit = ?, Revenue = ? WHERE Shipment_ID = ?");
                updateShipmentProfit.setDouble(1, grossProfit);
                updateShipmentProfit.setDouble(2, revenue);
                updateShipmentProfit.setInt(3, shipmentId);
                updateShipmentProfit.executeUpdate();

                conn.commit(); // Commit the transaction
                loadShipments();
            } catch (SQLException | NumberFormatException e) {
                e.printStackTrace();
                try (Connection conn = db.DatabaseManager.connect()) {
                    conn.rollback(); // Rollback the transaction in case of an error
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
                }
                JOptionPane.showMessageDialog(this, "Error saving shipment.");
            }
        }
    }

    // Step 1: Add inner class to ShipmentWindow
    private static class ProductItem {
        int id;
        String name;

        ProductItem(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name; // display only the name in JComboBox
        }
    }
}
