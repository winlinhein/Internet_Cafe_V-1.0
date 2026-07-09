package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class DatabaseConnection {
    private static final String URL = 
                            "";
    private static final String USER = "avnadmin";
    private static final String PASSWORD = "Your Password";

    private static final int POOL_SIZE = 5;
    private static final BlockingQueue<Connection> connectionPool = new ArrayBlockingQueue<>(POOL_SIZE);
    private static boolean poolInitialized = false;

    static {
        initializePool();
    }

    private static void initializePool() {
        if (poolInitialized) return;
        
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            for (int i = 0; i < POOL_SIZE; i++) {
                Connection con = DriverManager.getConnection(URL, USER, PASSWORD);
                connectionPool.offer(con);
            }
            poolInitialized = true;
            System.out.println("Connection pool initialized with " + POOL_SIZE + " connections.");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found. Add mysql-connector-java to your classpath.");
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("Failed to initialize connection pool: " + e.getMessage());
            System.err.println("Error details: " + e.getErrorCode() + ", " + e.getSQLState());
            e.printStackTrace();
        }
    }

    public static Connection connectDB() {
        try {
            Connection con = connectionPool.poll(2, TimeUnit.SECONDS);
            if (con == null || con.isClosed()) {
                System.out.println("Pool exhausted or connection closed, creating new connection");
                con = createNewConnection();
                if (con != null) {
                    System.out.println("New connection created successfully");
                }
            }
            return con;
        } catch (InterruptedException e) {
            System.err.println("Interrupted while waiting for connection: " + e.getMessage());
            Thread.currentThread().interrupt();
            return createNewConnection();
        } catch (Exception e) {
            System.err.println("Unexpected error getting connection: " + e.getMessage());
            System.err.println("Error details: " + e.getClass().getName() + ", " + e.getLocalizedMessage());
            e.printStackTrace();
            return createNewConnection();
        }
    }

    private static Connection createNewConnection() {
        try {
            return DriverManager.getConnection(URL, USER, PASSWORD);
        } catch (SQLException e) {
            System.err.println("Failed to create new connection: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public static void releaseConnection(Connection con) {
        if (con != null) {
            try {
                if (!con.isClosed() && connectionPool.size() < POOL_SIZE) {
                    connectionPool.offer(con);
                } else {
                    con.close();
                }
            } catch (SQLException e) {
                System.err.println("Failed to release connection: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    public static void disconnect() {
        while (!connectionPool.isEmpty()) {
            try {
                Connection con = connectionPool.poll();
                if (con != null && !con.isClosed()) {
                    con.close();
                }
            } catch (SQLException e) {
                System.err.println("Failed to close connection during disconnect: " + e.getMessage());
                e.printStackTrace();
            }
        }
        poolInitialized = false;
        System.out.println("Connection pool closed.");
    }
}