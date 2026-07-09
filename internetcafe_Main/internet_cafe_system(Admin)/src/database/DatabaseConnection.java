package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
     // Correct JDBC URL format
    private static final String URL = 
                            "";
    private static final String USER = "avnadmin";
    private static final String PASSWORD = "";

    private static Connection con = null;

    public static Connection connectDB() {
        try {
            // Load MySQL driver explicitly (optional in modern JDBC, but safe)
            Class.forName("com.mysql.cj.jdbc.Driver");

            con = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("Connected to Aiven successfully!");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found. Add mysql-connector-java to your classpath.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Connection failed: " + e.getMessage());
            e.printStackTrace();
        }
        return con;
    }
    
    public static void disconnect() {
        try {
            if (con != null && !con.isClosed()) {
                con.close();
                System.out.println("Connection closed successfully.");
            }
        } catch (SQLException e) {
            System.err.println("Failed to close the connection: " + e.getMessage());
            e.printStackTrace();
        } finally {
            con = null; // Reset the connection object
        }
    }
}