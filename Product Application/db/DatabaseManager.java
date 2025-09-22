package db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:app.db";

    public static Connection connect() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);
        conn.setAutoCommit(false); // Disable auto-commit to handle transactions manually
        conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE); // Set isolation level
        conn.setNetworkTimeout(null, 5000); // Set the timeout to 5 seconds
        return conn;
    }
}
