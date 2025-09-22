import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.time.LocalDate;

public class SalesWindow extends JDialog {

    private JTable table;
    private DefaultTableModel model;

    public SalesWindow(JFrame parent) {
        super(parent, "Manage Sales", true);
        setSize(900, 400);
        setLocationRelativeTo(parent);

        // Include a Revenue column in the table model
        model = new DefaultTableModel(new String[]{
                "ID", "Client", "Product", "Qty", "Date", "Payment Status", "Seller"
        }, 0);
        table = new JTable(model);
        table.setDefaultEditor(Object.class, null); // Disable inline editing for all columns

        // Hide the "ID" column
        table.getColumnModel().removeColumn(table.getColumnModel().getColumn(0));

        loadSales();

        JButton btnAdd = new JButton("Add Sale");
        btnAdd.addActionListener(_ -> addSale());

        JButton btnDelete = new JButton("Delete Sale");
        btnDelete.addActionListener(_ -> deleteSale());

        JButton btnViewAmend = new JButton("View / Amend Sale");
        btnViewAmend.addActionListener(_ -> viewAmendSale());

        JButton btnChangePaymentStatus = new JButton("Change Payment Status");
        btnChangePaymentStatus.addActionListener(_ -> changePaymentStatus());

        JPanel btnPanel = new JPanel();
        btnPanel.add(btnAdd);
        btnPanel.add(btnDelete);
        btnPanel.add(btnViewAmend);
        btnPanel.add(btnChangePaymentStatus);

        add(new JScrollPane(table), BorderLayout.CENTER);
        add(btnPanel, BorderLayout.SOUTH);

        setVisible(true);
    }

    private void loadSales() {
        model.setRowCount(0);
        String sql = """
            SELECT s.Sale_ID,
                   c.Name AS ClientName,
                   p.Name AS ProductName,
                   s.Quantity,
                   s.SaleDate,
                   s.PaymentStatus,
                   se.Name AS SellerName
            FROM Sale s
            JOIN Client c ON s.Client_ID = c.Client_ID
            JOIN Product p ON s.Product_ID = p.Product_ID
            JOIN Seller se ON s.Seller_ID = se.Seller_ID
        """;

        try (Connection conn = db.DatabaseManager.connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                model.addRow(new Object[]{
                        rs.getInt("Sale_ID"),
                        rs.getString("ClientName"),
                        rs.getString("ProductName"),
                        rs.getInt("Quantity"),
                        rs.getString("SaleDate"),
                        rs.getString("PaymentStatus"),
                        rs.getString("SellerName")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void changePaymentStatus() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a sale to update.");
            return;
        }

        int saleId = (int) model.getValueAt(selectedRow, 0);
        String currentStatus = model.getValueAt(selectedRow, 5).toString();
        String newStatus = currentStatus.equals("Paid") ? "Unpaid" : "Paid";

        updateSalePaymentStatus(saleId, newStatus);
        model.setValueAt(newStatus, selectedRow, 5);
    }

    private void updateSalePaymentStatus(int saleId, String newPaymentStatus) {
        try (Connection conn = db.DatabaseManager.connect()) {
            conn.setAutoCommit(false);

            // Fetch the sale's old status, revenue, and seller ID
            String oldStatus;
            double revenue;
            int sellerId;
            try (PreparedStatement sel = conn.prepareStatement(
                    "SELECT PaymentStatus, TotalRevenue, Seller_ID FROM Sale WHERE Sale_ID = ?")) {
                sel.setInt(1, saleId);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) {
                        oldStatus = rs.getString("PaymentStatus");
                        revenue = rs.getDouble("TotalRevenue");
                        sellerId = rs.getInt("Seller_ID");
                    } else {
                        JOptionPane.showMessageDialog(this, "Sale not found.");
                        return;
                    }
                }
            }

            // Update the payment status
            try (PreparedStatement upd = conn.prepareStatement(
                    "UPDATE Sale SET PaymentStatus = ? WHERE Sale_ID = ?")) {
                upd.setString(1, newPaymentStatus);
                upd.setInt(2, saleId);
                upd.executeUpdate();
            }

            // Adjust the seller's cash only if the status actually changed
            if (!oldStatus.equals(newPaymentStatus)) {
                String op = newPaymentStatus.equals("Paid") ? "+" : "-";
                try (PreparedStatement adj = conn.prepareStatement(
                        "UPDATE Seller SET CashOnHand = CashOnHand " + op + " ? WHERE Seller_ID = ?")) {
                    adj.setDouble(1, revenue);
                    adj.setInt(2, sellerId);
                    adj.executeUpdate();
                }
            }

            conn.commit();
            MainAppWindow main = (MainAppWindow) SwingUtilities.getWindowAncestor(this);
            if (main != null) main.updateCashLabels();

        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error updating payment status.");
        }
    }

    private void addSale() {
        try (Connection conn = db.DatabaseManager.connect()) {
            conn.setAutoCommit(false); // Start a transaction

            // Prepare client selection
            ResultSet clients = conn.createStatement().executeQuery("SELECT * FROM Client");
            JComboBox<String> clientBox = new JComboBox<>();
            while (clients.next()) {
                clientBox.addItem(clients.getInt("Client_ID") + " - " + clients.getString("Name"));
            }

            // Create a panel for adding multiple products to the sale
            DefaultTableModel itemModel = new DefaultTableModel(new String[]{"Product", "Quantity"}, 0);
            JTable itemTable = new JTable(itemModel);

            // Dropdown for selecting products
            JComboBox<ProductItem> productBox = new JComboBox<>();
            ResultSet products = conn.createStatement().executeQuery("SELECT * FROM Product");
            while (products.next()) {
                productBox.addItem(new ProductItem(products.getInt("Product_ID"), products.getString("Name")));
            }

            // Dropdown for selecting sellers
            JComboBox<String> sellerBox = new JComboBox<>();
            ResultSet sellers = conn.createStatement().executeQuery("SELECT Seller_ID, Name FROM Seller");
            while (sellers.next()) {
                sellerBox.addItem(sellers.getInt("Seller_ID") + " - " + sellers.getString("Name"));
            }

            // Add product button
            JButton btnAddProduct = new JButton("Add Product");
            JButton btnRemoveProduct = new JButton("Remove Selected"); // Renamed
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
                    if (!qtyText.matches("\\d+")) {
                        JOptionPane.showMessageDialog(this, "Quantity must be a whole number (positive integer).");
                        return;
                    }
                    int qty = Integer.parseInt(qtyText);
                    // Validation
                    if (qty <= 0) {
                        JOptionPane.showMessageDialog(this, "Quantity must be >0.");
                        return;
                    }

                    ProductItem selectedProduct = (ProductItem) prodSelect.getSelectedItem();
                    if (selectedProduct == null) return;
                    itemModel.addRow(new Object[]{selectedProduct, qty});
                }
            });

            // Remove product button
            btnRemoveProduct.addActionListener(_ -> {
                int selectedRow = itemTable.getSelectedRow();
                if (selectedRow != -1) {
                    itemModel.removeRow(selectedRow);
                } else {
                    JOptionPane.showMessageDialog(this, "Please select a product to remove.");
                }
            });

            // Form to gather client, sale date, payment status, and seller
            JComboBox<String> paymentStatusBox = new JComboBox<>(new String[]{"Paid", "Unpaid"});
            JPanel formPanel = new JPanel();
            formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
            formPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            formPanel.add(createLabeledField("Client:", clientBox));
            formPanel.add(createLabeledField("Products in Sale:", itemTable));
            formPanel.add(createLabeledField("Sale Date:", new JTextField(LocalDate.now().toString())));
            formPanel.add(createLabeledField("Payment Status:", paymentStatusBox));
            formPanel.add(createLabeledField("Seller:", sellerBox));

            JPanel productButtonRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
            productButtonRow.add(btnAddProduct);
            productButtonRow.add(btnRemoveProduct);

            formPanel.add(productButtonRow);

            int result = JOptionPane.showConfirmDialog(this, formPanel, "Add Sale", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                // Validate: at least one product
                if (itemModel.getRowCount() == 0) {
                    JOptionPane.showMessageDialog(this, "Please add at least one product.");
                    return;
                }

                // Validate: client selected
                if (clientBox.getSelectedItem() == null) {
                    JOptionPane.showMessageDialog(this, "Please select a client.");
                    return;
                }

                // Validate: seller selected
                if (sellerBox.getSelectedItem() == null) {
                    JOptionPane.showMessageDialog(this, "Please select a seller.");
                    return;
                }

                int clientId = Integer.parseInt(clientBox.getSelectedItem().toString().split(" - ")[0]);
                String saleDate = LocalDate.now().toString();
                String paymentStatus = (String) paymentStatusBox.getSelectedItem();
                int sellerId = Integer.parseInt(sellerBox.getSelectedItem().toString().split(" - ")[0]);

                // Insert one Sale per item
                for (int i = 0; i < itemModel.getRowCount(); i++) {
                    ProductItem product = (ProductItem) itemModel.getValueAt(i, 0);
                    int productId = product.id;
                    int qty = (Integer) itemModel.getValueAt(i, 1);

                    // Fetch Price
                    double salePrice = 0;
                    try (PreparedStatement pst = conn.prepareStatement(
                            "SELECT Price FROM Product WHERE Product_ID = ?")) {
                        pst.setInt(1, productId);
                        try (ResultSet rs = pst.executeQuery()) {
                            if (rs.next()) salePrice = rs.getDouble(1);
                        }
                    }

                    double itemRevenue = salePrice * qty;

                    // Stock check & update
                    try (PreparedStatement chk = conn.prepareStatement(
                            "SELECT AmountLeft FROM Product WHERE Product_ID = ?")) {
                        chk.setInt(1, productId);
                        try (ResultSet rs = chk.executeQuery()) {
                            if (rs.next() && rs.getInt(1) < qty) {
                                JOptionPane.showMessageDialog(this, "Not enough stock for " + product.name);
                                conn.rollback();
                                return;
                            }
                        }
                    }
                    try (PreparedStatement up = conn.prepareStatement(
                            "UPDATE Product SET AmountLeft = AmountLeft - ? WHERE Product_ID = ?")) {
                        up.setInt(1, qty);
                        up.setInt(2, productId);
                        up.executeUpdate();
                    }

                    // Insert sale record
                    try (PreparedStatement ins = conn.prepareStatement(
                            "INSERT INTO Sale(Client_ID, Product_ID, Seller_ID, Quantity, SaleDate, PaymentStatus, TotalRevenue) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                        ins.setInt(1, clientId);
                        ins.setInt(2, productId);
                        ins.setInt(3, sellerId);
                        ins.setInt(4, qty);
                        ins.setString(5, saleDate);
                        ins.setString(6, paymentStatus);
                        ins.setDouble(7, itemRevenue);
                        ins.executeUpdate();
                    }

                    // Update client's number of purchases
                    try (PreparedStatement updateClient = conn.prepareStatement(
                            "UPDATE Client SET NumberOfPurchases = NumberOfPurchases + ? WHERE Client_ID = ?")) {
                        updateClient.setInt(1, qty);
                        updateClient.setInt(2, clientId);
                        updateClient.executeUpdate();
                    }

                    // Update cash if paid
                    if ("Paid".equals(paymentStatus)) {
                        try (PreparedStatement upc = conn.prepareStatement(
                                "UPDATE Seller SET CashOnHand = CashOnHand + ? WHERE Seller_ID = ?")) {
                            upc.setDouble(1, itemRevenue);
                            upc.setInt(2, sellerId);
                            upc.executeUpdate();
                        }
                    }
                }

                conn.commit();
                loadSales();
                JOptionPane.showMessageDialog(this, "Sale added successfully.");
            }
        } catch (SQLException | NumberFormatException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error adding sale.");
        }
    }
        
    private void deleteSale() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a sale to delete.");
            return;
        }

        int saleId = (int) model.getValueAt(selectedRow, 0);
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete this sale?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try (Connection conn = db.DatabaseManager.connect()) {
            conn.setAutoCommit(false);

            // Step 1: Fetch sale info (including Product_ID, Quantity, Client_ID)
            String paymentStatus = "";
            double revenue = 0;
            int sellerId = -1;
            int clientId = -1;

            try (PreparedStatement fetch = conn.prepareStatement(
                    "SELECT TotalRevenue, Seller_ID, PaymentStatus, Client_ID FROM Sale WHERE Sale_ID = ?")) {
                fetch.setInt(1, saleId);
                try (ResultSet rs = fetch.executeQuery()) {
                    if (rs.next()) {
                        revenue = rs.getDouble("TotalRevenue");
                        sellerId = rs.getInt("Seller_ID");
                        paymentStatus = rs.getString("PaymentStatus");
                        clientId = rs.getInt("Client_ID");
                    }
                }
            }

            // Restore product stock before deleting the sale
            int productId = -1;
            int quantity = 0;
            try (PreparedStatement info = conn.prepareStatement(
                    "SELECT Product_ID, Quantity, Client_ID FROM Sale WHERE Sale_ID = ?")) {
                info.setInt(1, saleId);
                ResultSet rs = info.executeQuery();
                if (rs.next()) {
                    productId = rs.getInt("Product_ID");
                    quantity = rs.getInt("Quantity");
                    clientId = rs.getInt("Client_ID");
                }
            }
            if (productId != -1 && quantity > 0) {
                try (PreparedStatement restore = conn.prepareStatement(
                        "UPDATE Product SET AmountLeft = AmountLeft + ? WHERE Product_ID = ?")) {
                    restore.setInt(1, quantity);
                    restore.setInt(2, productId);
                    restore.executeUpdate();
                }
            }

            // Step 2: Adjust cash on hand if status was 'Paid'
            if ("Paid".equals(paymentStatus)) {
                try (PreparedStatement updateCash = conn.prepareStatement(
                        "UPDATE Seller SET CashOnHand = CashOnHand - ? WHERE Seller_ID = ?")) {
                    updateCash.setDouble(1, revenue);
                    updateCash.setInt(2, sellerId);
                    updateCash.executeUpdate();
                }
            }

            // Step 3: Decrease NumberOfPurchases for client (minimum 0)
            try (PreparedStatement updateClient = conn.prepareStatement(
                    "UPDATE Client SET NumberOfPurchases = MAX(NumberOfPurchases - ?, 0) WHERE Client_ID = ?")) {
                updateClient.setInt(1, quantity);
                updateClient.setInt(2, clientId);
                updateClient.executeUpdate();
            }

            // Step 4: Delete sale
            try (PreparedStatement deleteStmt = conn.prepareStatement("DELETE FROM Sale WHERE Sale_ID = ?")) {
                deleteStmt.setInt(1, saleId);
                deleteStmt.executeUpdate();
            }

            conn.commit();
            loadSales();

            // Refresh cash info
            MainAppWindow main = (MainAppWindow) SwingUtilities.getWindowAncestor(this);
            if (main != null) main.updateCashLabels();

            JOptionPane.showMessageDialog(this, "Sale deleted and cash adjusted.");
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error deleting sale.");
        }
    }
    

    private void viewAmendSale() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow == -1) {
            JOptionPane.showMessageDialog(this, "Please select a sale to view or amend.");
            return;
        }

        int saleId = (int) model.getValueAt(selectedRow, 0);

        // Phase 1: Fetch data and close the connection
        SaleData saleData;
        try (Connection conn = db.DatabaseManager.connect()) {
            PreparedStatement stmt = conn.prepareStatement("""
                SELECT s.Quantity, s.SaleDate, se.Name AS SellerName, p.Name AS ProductName
                FROM Sale s
                JOIN Seller se ON s.Seller_ID = se.Seller_ID
                JOIN Product p ON s.Product_ID = p.Product_ID
                WHERE s.Sale_ID = ?
            """);
            stmt.setInt(1, saleId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    JOptionPane.showMessageDialog(this, "Sale not found.");
                    return;
                }
                saleData = new SaleData(
                    rs.getInt("Quantity"),
                    rs.getString("SaleDate"),
                    rs.getString("SellerName"),
                    rs.getString("ProductName")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error fetching sale details.");
            return;
        }

        // Phase 2: Show UI and call amendSale
        showEditDialogAndThenCallAmendSale(saleId, saleData);
    }

    private void showEditDialogAndThenCallAmendSale(int saleId, SaleData saleData) {
        JTextField qtyField = new JTextField(String.valueOf(saleData.getQuantity()));
        JTextField dateField = new JTextField(saleData.getSaleDate());
        JTextField sellerField = new JTextField(saleData.getSellerName());
        JTextField productField = new JTextField(saleData.getProductName());
        productField.setEditable(false); // Product is not editable

        JPanel formPanel = new JPanel(new GridLayout(4, 2));
        formPanel.add(new JLabel("Quantity:"));
        formPanel.add(qtyField);
        formPanel.add(new JLabel("Sale Date:"));
        formPanel.add(dateField);
        formPanel.add(new JLabel("Seller:"));
        formPanel.add(sellerField);
        formPanel.add(new JLabel("Product:"));
        formPanel.add(productField);

        int result = JOptionPane.showConfirmDialog(this, formPanel, "Amend Sale", JOptionPane.OK_CANCEL_OPTION);
        if (result == JOptionPane.OK_OPTION) {
            amendSale(saleId, qtyField, sellerField, dateField);
        }
    }

    private void amendSale(int saleId, JTextField qtyField, JTextField sellerField, JTextField dateField) {
        try (Connection conn = db.DatabaseManager.connect()) {
            conn.setAutoCommit(false); // Start a transaction

            PreparedStatement updateStmt = conn.prepareStatement("""
                UPDATE Sale
                SET Quantity = ?, SaleDate = ?, Seller_ID = (
                    SELECT Seller_ID FROM Seller WHERE Name = ?
                )
                WHERE Sale_ID = ?
            """);
            updateStmt.setInt(1, Integer.parseInt(qtyField.getText()));
            updateStmt.setString(2, dateField.getText());
            updateStmt.setString(3, sellerField.getText());
            updateStmt.setInt(4, saleId);
            updateStmt.executeUpdate();

            conn.commit(); // Commit the transaction
            loadSales(); // Reload the sales table
            JOptionPane.showMessageDialog(this, "Sale updated successfully.");
        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error updating sale.");
            // Rollback on the same connection
            try (Connection conn = db.DatabaseManager.connect()) {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
            }
        }
    }
    
    private JPanel createLabeledField(String labelText, JComponent field) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        panel.add(new JLabel(labelText), BorderLayout.WEST);
        panel.add(field, BorderLayout.CENTER);
        return panel;
    }

    // Step 1: Add ProductItem inner class at the end of the file
    private static class ProductItem {
        int id;
        String name;

        ProductItem(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}