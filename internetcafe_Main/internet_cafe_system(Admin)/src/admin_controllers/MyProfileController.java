package admin_controllers;

import animation.PixelMotion;
import member_controllers.ClientInterface;
import database.DatabaseConnection;   // not used directly but kept as import
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ResourceBundle;
import java.util.regex.Pattern;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Base64;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class MyProfileController implements Initializable, InitializableController {

    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private PasswordField oldPass;
    @FXML private PasswordField newPass;
    @FXML private PasswordField confirmPass;

    private Connection con;
    private ServerInterface server;
    private ClientInterface client;
    private String admin_name = Admin_Login.admin_name;
    private String name;
    private Runnable refreshCallback;
    private StaffCardController cardController;
    private String password;          // stored value (hash or plain text)
    private Staff selectedStaff;

    // Validation patterns
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(09|\\+?959)\\d{7,9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[0-9])" +
        "(?=.*[a-z])" +
        "(?=.*[A-Z])" +
        "(?=.*[@#$%^&+=!])" +
        "(?=\\S+$)" +
        ".{8,}$"
    );

    // ---------- PBKDF2 Hashing constants ----------
    private static final String PBKDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final byte[] STATIC_SALT = "Admin_Static_Salt_32bytes_long!!".getBytes(); // use per-user salt in production

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (con != null) {
            System.out.println("Database connection already available in MyProfileController");
            Platform.runLater(() -> {
                try {
                    initData();
                } catch (SQLException e) {
                    e.printStackTrace();
                    showError("Database Error", e.getMessage());
                }
            });
        } else {
            System.out.println("MyProfileController: Waiting for database connection to be injected...");
        }
    }

    public void setParentController(StaffCardController controller) {
        this.cardController = controller;
    }

    public void setRefreshCallback(Runnable callback) {
        this.refreshCallback = callback;
    }

    public void initData() throws SQLException {
        if (con == null) {
            System.err.println("Cannot initialize data: database connection is null");
            return;
        }

        String sql = "SELECT * FROM internet_cafe.admin WHERE admin_name=?";
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, admin_name);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    nameField.setText(rs.getString("admin_name"));
                    phoneField.setText(rs.getString("phone"));
                    emailField.setText(rs.getString("email"));
                    password = rs.getString("passwords");   // may be hash or plain

                    selectedStaff = new Staff(
                        rs.getInt("admin_id"),
                        rs.getString("admin_name"),
                        rs.getString("phone"),
                        rs.getString("email"),
                        rs.getString("passwords")
                    );
                    System.out.println("Profile data loaded for: " + admin_name);
                }
            }
        }
    }

    // ==========================================
    // VALIDATION METHODS (unchanged)
    // ==========================================

    private boolean validatePhoneNumber(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            showError("Validation Error", "Phone number is required");
            return false;
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            showError("Invalid Phone Number",
                "Phone number must be:\n" +
                "• 10-11 digits long\n" +
                "• Start with 09 or +959 or 959\n" +
                "• Example: 09123456789 or +959123456789");
            return false;
        }
        return true;
    }

    private boolean validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            showError("Validation Error", "Email is required");
            return false;
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            showError("Invalid Email",
                "Email must be in valid format:\n" +
                "• Example: username@domain.com\n" +
                "• Must contain @ and domain\n" +
                "• No spaces allowed");
            return false;
        }
        return true;
    }

    private boolean validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            showError("Validation Error", "Password is required");
            return false;
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            StringBuilder requirements = new StringBuilder();
            requirements.append("Password must meet ALL requirements:\n\n");
            if (password.length() < 8) {
                requirements.append("❌ At least 8 characters long\n");
            } else {
                requirements.append("✓ At least 8 characters long\n");
            }
            if (!password.matches(".*[0-9].*")) {
                requirements.append("❌ At least 1 number (0-9)\n");
            } else {
                requirements.append("✓ At least 1 number (0-9)\n");
            }
            if (!password.matches(".*[a-z].*")) {
                requirements.append("❌ At least 1 lowercase letter (a-z)\n");
            } else {
                requirements.append("✓ At least 1 lowercase letter (a-z)\n");
            }
            if (!password.matches(".*[A-Z].*")) {
                requirements.append("❌ At least 1 uppercase letter (A-Z)\n");
            } else {
                requirements.append("✓ At least 1 uppercase letter (A-Z)\n");
            }
            if (!password.matches(".*[@#$%^&+=!].*")) {
                requirements.append("❌ At least 1 special character (@#$%^&+=!)\n");
            } else {
                requirements.append("✓ At least 1 special character (@#$%^&+=!)\n");
            }
            if (password.contains(" ")) {
                requirements.append("❌ No spaces allowed\n");
            } else {
                requirements.append("✓ No spaces allowed\n");
            }
            showError("Invalid Password Format", requirements.toString());
            return false;
        }
        return true;
    }

    private boolean validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            showError("Validation Error", "Name is required");
            return false;
        }
        if (name.trim().length() < 2) {
            showError("Validation Error", "Name must be at least 2 characters long");
            return false;
        }
        if (name.trim().length() > 50) {
            showError("Validation Error", "Name must be less than 50 characters");
            return false;
        }
        return true;
    }

    @FXML
    private void handleCloseAction(ActionEvent event) {
        closeWindow(event);
    }

    @FXML
    private void handleRemoveAction(ActionEvent event) {
        showInfo("Info", "Remove functionality not implemented");
    }

    @FXML
    private void handleSaveAction(ActionEvent event) {
        if (con == null) {
            showError("Database Error", "No database connection available");
            return;
        }

        String newName = nameField.getText().trim();
        String newEmail = emailField.getText().trim();
        String newPhone = phoneField.getText().trim();

        if (!validateName(newName)) {
            nameField.clear();
            nameField.setPromptText("Enter valid name!");
            return;
        }
        if (!validateEmail(newEmail)) {
            emailField.clear();
            emailField.setPromptText("Enter valid email!");
            return;
        }
        if (!validatePhoneNumber(newPhone)) {
            phoneField.clear();
            phoneField.setPromptText("Enter valid phone number!");
            return;
        }

        // Old password is required
        if (oldPass.getText().isEmpty()) {
            oldPass.setPromptText("Enter current password!");
            showError("Validation Error", "Current password is required to save changes");
            return;
        }

        // ---------- Verify current password (handles hash or plain text) ----------
        boolean oldPassCorrect = false;
        if (password != null && password.contains(":") && password.split(":").length == 3) {
            // stored as hash
            oldPassCorrect = verifyPassword(oldPass.getText(), password);
        } else {
            // plain text fallback
            oldPassCorrect = oldPass.getText().equals(password);
        }

        if (!oldPassCorrect) {
            oldPass.clear();
            oldPass.setPromptText("Invalid current password!");
            showError("Validation Error", "Invalid current password");
            return;
        }

        // ---------- Password Change Logic ----------
        String newPassword = newPass.getText();
        String confirmPassword = confirmPass.getText();

        if (newPassword.isEmpty() && confirmPassword.isEmpty()) {
            // No password change
            updateStaffData(newName, newPhone, newEmail);
        } else {
            // Password change requested
            if (!validatePassword(newPassword)) {
                newPass.clear();
                confirmPass.clear();
                newPass.setPromptText("Enter valid password!");
                confirmPass.setPromptText("Confirm password!");
                return;
            }
            if (!newPassword.equals(confirmPassword)) {
                confirmPass.clear();
                confirmPass.setPromptText("Passwords do not match!");
                showError("Validation Error", "New passwords do not match");
                return;
            }

            // Hash the new password
            String hashed = hashPassword(newPassword);
            if (hashed == null) {
                showError("Error", "Failed to secure new password.");
                return;
            }
            updateStaffAllData(newName, newPhone, newEmail, hashed);
        }

        Admin_Login.admin_name = newName;
        closeWindow(event);
    }

    private void updateStaffAllData(String newName, String newPhone, String newEmail, String hashedPassword) {
        String sql = "UPDATE internet_cafe.admin SET admin_name=?, phone=?, email=?, passwords=? WHERE admin_name=?";
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, newName);
            pst.setString(2, newPhone);
            pst.setString(3, newEmail);
            pst.setString(4, hashedPassword);          // store the hash
            pst.setString(5, admin_name);

            int rowsAffected = pst.executeUpdate();
            if (rowsAffected > 0) {
                selectedStaff.setAdmin_name(newName);
                selectedStaff.setEmail(newEmail);
                selectedStaff.setPhone(newPhone);
                selectedStaff.setPassword(hashedPassword);   // update with hash
                this.password = hashedPassword;

                if (cardController != null) {
                    cardController.setData(selectedStaff);
                }
                if (refreshCallback != null) {
                    refreshCallback.run();
                }
                showInfo("Success", "Profile updated successfully with new password");
                System.out.println("Profile updated for: " + newName);
            }
        } catch (SQLException ex) {
            System.err.println("Error updating profile: " + ex.getMessage());
            ex.printStackTrace();
            showError("Database Error", "Failed to update profile: " + ex.getMessage());
        }
    }

    private void updateStaffData(String newName, String newPhone, String newEmail) {
        String sql = "UPDATE internet_cafe.admin SET admin_name=?, phone=?, email=? WHERE admin_name=?";
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, newName);
            pst.setString(2, newPhone);
            pst.setString(3, newEmail);
            pst.setString(4, admin_name);

            int rowsAffected = pst.executeUpdate();
            if (rowsAffected > 0) {
                selectedStaff.setAdmin_name(newName);
                selectedStaff.setEmail(newEmail);
                selectedStaff.setPhone(newPhone);

                if (cardController != null) {
                    cardController.setData(selectedStaff);
                }
                if (refreshCallback != null) {
                    refreshCallback.run();
                }
                showInfo("Success", "Profile updated successfully");
                System.out.println("Profile updated for: " + newName);
            }
        } catch (SQLException ex) {
            System.err.println("Error updating profile: " + ex.getMessage());
            ex.printStackTrace();
            showError("Database Error", "Failed to update profile: " + ex.getMessage());
        }
    }

    @FXML
    private void closeEditPopup(ActionEvent event) {
        closeWindow(event);
    }

    private void closeWindow(ActionEvent event) {
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }

    private void showError(String title, String message) {
        Platform.runLater(() ->
            PixelMotion.toastGlitch(nameField, title,
                message == null ? "Unknown error" : message,
                PixelMotion.ToastType.ERROR));
    }

    private void showInfo(String title, String message) {
        Platform.runLater(() ->
            PixelMotion.toastGlitch(nameField, title,
                message == null ? "" : message,
                PixelMotion.ToastType.INFO));
    }

    // ==========================================
    // InitializableController Interface Methods
    // ==========================================
    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
        System.out.println("MyProfileController: Server injected");
    }

    @Override
    public void setClient(ClientInterface client) {
        this.client = client;
        System.out.println("MyProfileController: Client injected");
    }

    @Override
    public void setDatabaseConnection(Connection con) {
        this.con = con;
        System.out.println("MyProfileController: Database connection injected");
        if (con != null) {
            Platform.runLater(() -> {
                try {
                    initData();
                } catch (SQLException e) {
                    e.printStackTrace();
                    showError("Database Error", e.getMessage());
                }
            });
        }
    }

    // =================== PASSWORD HASHING METHODS ===================

    private String hashPassword(String password) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), STATIC_SALT, ITERATIONS, KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(PBKDF_ALGORITHM);
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return ITERATIONS + ":" + Base64.getEncoder().encodeToString(STATIC_SALT) + ":" +
                   Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean verifyPassword(String password, String storedHash) {
        try {
            String[] parts = storedHash.split(":");
            if (parts.length != 3) return false;
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] hash = Base64.getDecoder().decode(parts[2]);

            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(PBKDF_ALGORITHM);
            byte[] testHash = skf.generateSecret(spec).getEncoded();

            // Time-constant comparison
            int diff = hash.length ^ testHash.length;
            for (int i = 0; i < hash.length && i < testHash.length; i++) {
                diff |= hash[i] ^ testHash[i];
            }
            return diff == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * One‑time migration: hashes all plain‑text admin passwords.
     * Run this once after enlarging the 'passwords' column to VARCHAR(255).
     */
    public void migrateAdminPasswords() {
        if (con == null) return;
        String selectSql = "SELECT admin_id, passwords FROM internet_cafe.admin";
        try (PreparedStatement pstSelect = con.prepareStatement(selectSql);
             ResultSet rs = pstSelect.executeQuery()) {

            PreparedStatement pstUpdate = con.prepareStatement(
                "UPDATE internet_cafe.admin SET passwords = ? WHERE admin_id = ?"
            );
            int migrated = 0;
            while (rs.next()) {
                int id = rs.getInt("admin_id");
                String current = rs.getString("passwords");
                if (current == null) continue;
                // Skip if already hashed
                if (current.contains(":") && current.split(":").length == 3) continue;

                String newHash = hashPassword(current);
                if (newHash == null) continue;
                pstUpdate.setString(1, newHash);
                pstUpdate.setInt(2, id);
                pstUpdate.executeUpdate();
                migrated++;
                System.out.println("Migrated admin ID: " + id);
            }
            pstUpdate.close();
            System.out.println("Admin password migration complete. Hashed " + migrated + " passwords.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}