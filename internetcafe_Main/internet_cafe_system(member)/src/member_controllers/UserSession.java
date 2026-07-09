package member_controllers;

/**
 * Utility class to manage user session across the application
 * Stores the currently logged-in username and provides thread-safe access
 */
public class UserSession {
    
    private static volatile String currentUsername;
    
    // Private constructor to prevent instantiation
    private UserSession() {
    }
    
    /**
     * Set the currently logged-in username
     * @param username The username of the logged-in user
     */
    public static void setCurrentUsername(String username) {
        currentUsername = username;
        System.out.println("User session set for: " + username);
    }
    
    /**
     * Get the currently logged-in username
     * @return The current username or null if no user is logged in
     */
    public static String getCurrentUsername() {
        return currentUsername;
    }
    
    /**
     * Check if a user is currently logged in
     * @return true if a user is logged in, false otherwise
     */
    public static boolean isUserLoggedIn() {
        return currentUsername != null && !currentUsername.trim().isEmpty();
    }
    
    /**
     * Clear the current user session (logout)
     */
    public static void clearSession() {
        currentUsername = null;
        System.out.println("User session cleared");
    }
}
