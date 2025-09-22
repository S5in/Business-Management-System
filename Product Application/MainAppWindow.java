import javax.swing.*;
import java.awt.*;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class MainAppWindow extends JFrame {
    private JPanel cashPanel;  // Panel to hold cash-related labels for better layout
    private JPanel buttonPanel;  // Panel for buttons
    private JButton btnResetCash;

    // Maps to hold one JLabel per seller
    private final Map<String, JLabel> cashOnHandLabels = new LinkedHashMap<>();
    private final Map<String, JLabel> totalPaidLabels = new LinkedHashMap<>();
    private JLabel overallTotalPaidLabel;

    public MainAppWindow() {
        setTitle("Inventory Manager");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 600); // Adjust size for cash info display
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));  // Use BorderLayout for better control

        // Initialize panels
        cashPanel = new JPanel(new BorderLayout(10, 10));
        cashPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Container for the two rows of labels
        JPanel labelsContainer = new JPanel();
        labelsContainer.setLayout(new BoxLayout(labelsContainer, BoxLayout.Y_AXIS));

        // Fetch all sellers dynamically from the database
        java.util.List<String> sellers = getAllSellers();

        // 1) Cash on Hand row
        JPanel cashRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        cashRow.add(new JLabel("Cash on Hand:"));
        for (String seller : sellers) {
            JLabel lbl = new JLabel(seller + ": $0");
            cashOnHandLabels.put(seller, lbl);
            cashRow.add(lbl);
        }
        labelsContainer.add(cashRow);

        // 2) Total Cash Paid row (per-seller + overall)
        JPanel paidRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        paidRow.add(new JLabel("Total Cash Paid:"));
        for (String seller : sellers) {
            JLabel lbl = new JLabel(seller + ": $0");
            totalPaidLabels.put(seller, lbl);
            paidRow.add(lbl);
        }
        labelsContainer.add(paidRow);

        // 3) Overall Total Paid row
        JPanel overallRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        overallRow.add(new JLabel("Overall:"));
        overallTotalPaidLabel = new JLabel("$0");
        overallRow.add(overallTotalPaidLabel);
        labelsContainer.add(overallRow);

        // Add labels container to the left of the cash panel
        cashPanel.add(labelsContainer, BorderLayout.CENTER);

        // 3) Reset Cash button
        btnResetCash = new JButton("Reset Cash");
        btnResetCash.addActionListener(_ -> resetCashCounters());

        // Add the Reset Cash button to the right of the cash panel
        cashPanel.add(btnResetCash, BorderLayout.EAST);

        // 4) Panel for buttons
        buttonPanel = new JPanel(new GridLayout(5, 1, 10, 10)); // Buttons in a column
        JButton btnProducts = new JButton("Manage Products");
        JButton btnClients = new JButton("Manage Clients");
        JButton btnShipments = new JButton("Manage Shipments");
        JButton btnSales = new JButton("Manage Sales");
        JButton btnExit = new JButton("Exit");

        btnProducts.addActionListener(_ -> new ProductWindow(this));
        btnClients.addActionListener(_ -> new ClientWindow(this));
        btnShipments.addActionListener(_ -> new ShipmentWindow(this));
        btnSales.addActionListener(_ -> {
            new SalesWindow(this);
            updateCashLabels(); // Update cash labels after managing sales
        });
        btnExit.addActionListener(_ -> System.exit(0));

        // Add buttons to the button panel
        buttonPanel.add(btnProducts);
        buttonPanel.add(btnClients);
        buttonPanel.add(btnShipments);
        buttonPanel.add(btnSales);
        buttonPanel.add(btnExit);

        // Add panels to the main window
        add(cashPanel, BorderLayout.NORTH);  // Cash info at the top
        add(buttonPanel, BorderLayout.CENTER);  // Buttons in the center, stretched to window width

        setVisible(true);

        // Update cash labels on startup
        updateCashLabels();
    }

    /** Refreshes every seller’s cash labels and the overall total. */
    public void updateCashLabels() {
        try (Connection conn = db.DatabaseManager.connect()) {
            double overall = 0;
            for (var entry : cashOnHandLabels.entrySet()) {
                String seller = entry.getKey();
                double cash = getSellerCash(seller);
                entry.getValue().setText(seller + ": $" + cash);
            }
            for (var entry : totalPaidLabels.entrySet()) {
                String seller = entry.getKey();
                double paid = getTotalCashPaid(seller);
                entry.getValue().setText(seller + ": $" + paid);
                overall += paid;
            }
            overallTotalPaidLabel.setText("$" + overall);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // Get all sellers dynamically from the database
    private java.util.List<String> getAllSellers() {
        java.util.List<String> sellers = new java.util.ArrayList<>();
        try (Connection conn = db.DatabaseManager.connect()) {
            String sql = "SELECT Name FROM Seller";  // Query to fetch all seller names
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                String sellerName = rs.getString("Name");
                sellers.add(sellerName);  // Add each seller's name to the list
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return sellers;
    }

    // Get the cash on hand for a specific seller
    private double getSellerCash(String sellerName) {
        double cash = 0;
        try (Connection conn = db.DatabaseManager.connect()) {
            String sql = "SELECT CashOnHand FROM Seller WHERE Name = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, sellerName);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                cash = rs.getDouble("CashOnHand");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return cash;
    }

    // Get total cash paid for a specific seller
    private double getTotalCashPaid(String seller) {
        double cash = 0;
        try (Connection conn = db.DatabaseManager.connect()) {
            String sql = """
                SELECT SUM(Sale.TotalRevenue)
                FROM Sale
                JOIN Seller ON Sale.Seller_ID = Seller.Seller_ID
                WHERE Seller.Name = ? AND (Sale.PaymentStatus = 'Paid' OR Sale.PaymentStatus = 'Unpaid')
            """;
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, seller);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                cash = rs.getDouble(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return cash;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MainAppWindow::new);
    }

    private void resetCashCounters() {
        int choice = JOptionPane.showConfirmDialog(
            this,
            "This will set all cash‐on‐hand and total cash paid back to zero.\nAre you sure?",
            "Confirm Cash Reset",
            JOptionPane.YES_NO_OPTION
        );
        if (choice != JOptionPane.YES_OPTION) return;

        try (Connection conn = db.DatabaseManager.connect();
             Statement stmt = conn.createStatement()) {

            conn.setAutoCommit(false);

            // reset cash on hand
            stmt.executeUpdate("UPDATE Seller SET CashOnHand = 0");

            // reset total cash paid
            stmt.executeUpdate("UPDATE Sale SET TotalRevenue = 0");

            conn.commit();

            // refresh labels
            updateCashLabels();

            JOptionPane.showMessageDialog(this, "Cash counters reset successfully.");

        } catch (SQLException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error resetting cash counters.");
        }
    }
}
