package admin_controllers;

import animation.PixelMotion;

import member_controllers.ClientInterface;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * FXML Controller class for Adding Staff Members
 */
public class AddStaffController implements Initializable, InitializableController {

    @FXML
    private TextField nameField;
    @FXML
    private TextField emailField;
    @FXML
    private TextField phoneField;
    @FXML
    private PasswordField newPass;
    @FXML
    private PasswordField confirmPass;

    private Staff currentStaff;
    private StaffController mainController;
    private CustomersCardController cardController;
    private Runnable refreshCallback;
    private Connection con;
    private ServerInterface server;
    private ClientInterface client;
    
    // Validation patterns
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z\\s]{2,50}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(09|\\+?959)\\d{7,9}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
        "^(?=.*[0-9])" +           // at least 1 digit
        "(?=.*[a-z])" +            // at least 1 lowercase letter
        "(?=.*[A-Z])" +            // at least 1 uppercase letter
        "(?=.*[@#$%^&+=!])" +      // at least 1 special character
        "(?=\\S+$)" +              // no whitespace
        ".{8,}$"                   // at least 8 characters
    );

    // ---------- PBKDF2 Hashing constants ----------
    private static final String PBKDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final byte[] STATIC_SALT = "Staff_Salt_32_bytes_long_key!!".getBytes(); // use per-user salt in production

    public void setParentController(StaffController controller) {
        this.mainController = controller;
    }
    
    public void setRefreshCallback(Runnable callback) {
        this.refreshCallback = callback;
    }
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        System.out.println("AddStaffController: Initialized, waiting for database connection...");
    }
    
    // ==========================================
    // VALIDATION METHODS (unchanged)
    // ==========================================
    
    private boolean validateName(String name) {
        if (name == null || name.trim().isEmpty()) {
            showError("Validation Error", "Name is required");
            nameField.setPromptText("Enter name!");
            return false;
        }
        if (name.trim().length() < 2) {
            showError("Validation Error", "Name must be at least 2 characters long");
            nameField.clear();
            nameField.setPromptText("Name too short!");
            return false;
        }
        if (name.trim().length() > 50) {
            showError("Validation Error", "Name must be less than 50 characters");
            nameField.clear();
            nameField.setPromptText("Name too long!");
            return false;
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            showError("Invalid Name", "Name can only contain letters and spaces");
            nameField.clear();
            nameField.setPromptText("Use only letters!");
            return false;
        }
        return true;
    }
    
    private boolean validatePhoneNumber(String phone) {
        if (phone == null || phone.trim().isEmpty()) {
            showError("Validation Error", "Phone number is required");
            phoneField.setPromptText("Enter phone number!");
            return false;
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            showError("Invalid Phone Number", 
                "Phone number must be:\n" +
                "• 10-11 digits total\n" +
                "• Start with 09, +959, or 959\n" +
                "• Example: 09123456789 or +959123456789\n" +
                "• No spaces or dashes");
            phoneField.clear();
            phoneField.setPromptText("e.g., 09123456789");
            return false;
        }
        return true;
    }
    
    private boolean validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            showError("Validation Error", "Email is required");
            emailField.setPromptText("Enter email!");
            return false;
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            showError("Invalid Email", 
                "Email must be in valid format:\n" +
                "• Example: username@domain.com\n" +
                "• Must contain @ and domain\n" +
                "• No spaces allowed");
            emailField.clear();
            emailField.setPromptText("e.g., name@example.com");
            return false;
        }
        return true;
    }
    
    private boolean validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            showError("Validation Error", "Password is required");
            newPass.setPromptText("Enter password!");
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
            newPass.clear();
            confirmPass.clear();
            newPass.setPromptText("Enter valid password!");
            confirmPass.setPromptText("Confirm password!");
            return false;
        }
        return true;
    }
    
    private boolean validatePasswordsMatch(String password, String confirm) {
        if (!password.equals(confirm)) {
            showError("Validation Error", "Passwords do not match!");
            confirmPass.clear();
            confirmPass.setPromptText("Passwords don't match!");
            return false;
        }
        return true;
    }
    
    private boolean checkDuplicateName(String name) {
        if (con == null) return false;
        String sql = "SELECT COUNT(*) FROM internet_cafe.admin WHERE admin_name = ?";
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, name);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    private boolean checkDuplicatePhone(String phone) {
        if (con == null) return false;
        String sql = "SELECT COUNT(*) FROM internet_cafe.admin WHERE phone = ?";
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, phone);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    private boolean checkDuplicateEmail(String email) {
        if (con == null) return false;
        String sql = "SELECT COUNT(*) FROM internet_cafe.admin WHERE email = ?";
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, email);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    @FXML
    private void handleCloseAction(ActionEvent event) {
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }

    @FXML
    private void handleRemoveAction(ActionEvent event) {
        // Handle remove if needed
    }

    @FXML
    private void handleSaveAction(ActionEvent event) {
        if (con == null) {
            showError("Database Error", "Database connection not available");
            return;
        }
        
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();
        String password = newPass.getText();
        String confirm = confirmPass.getText();
        
        // Validate all fields
        if (!validateName(name)) return;
        if (!validateEmail(email)) return;
        if (!validatePhoneNumber(phone)) return;
        if (!validatePassword(password)) return;
        if (!validatePasswordsMatch(password, confirm)) return;
        
        // Check for duplicates
        if (checkDuplicateName(name)) {
            showError("Duplicate Name", "A staff member with this name already exists.");
            nameField.clear();
            nameField.setPromptText("Name already exists!");
            return;
        }
        if (checkDuplicatePhone(phone)) {
            showError("Duplicate Phone", "A staff member with this phone number already exists.");
            phoneField.clear();
            phoneField.setPromptText("Phone already exists!");
            return;
        }
        if (checkDuplicateEmail(email)) {
            showError("Duplicate Email", "A staff member with this email already exists.");
            emailField.clear();
            emailField.setPromptText("Email already exists!");
            return;
        }
        
        // Hash the password before storing
        String hashedPassword = hashPassword(password);
        if (hashedPassword == null) {
            showError("Error", "Failed to secure password.");
            return;
        }
        
        String sql = "INSERT INTO internet_cafe.admin (admin_name, phone, email, passwords) VALUES (?,?,?,?)";

        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, name);
            pst.setString(2, phone);
            pst.setString(3, email);
            pst.setString(4, hashedPassword);   // Store the hash, not the plain password

            int rowsAffected = pst.executeUpdate();
            if (rowsAffected > 0) {
                showSuccess("Success", "Staff member added successfully!\n\n" +
                    "Name: " + name + "\n" +
                    "Phone: " + phone + "\n" +
                    "Email: " + email);
                
                if (refreshCallback != null) {
                    refreshCallback.run(); 
                }
                closeEditPopup(event);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Database Error", "Could not add staff member.\n" + e.getMessage());
        }
    }
    
    @FXML 
    private void closeEditPopup(ActionEvent event) {
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }
    
    private void showError(String title, String message) {
        Platform.runLater(() ->
            PixelMotion.toastGlitch(nameField, title,
                message == null ? "Unknown error" : message,
                PixelMotion.ToastType.ERROR));
    }
    
    private void showSuccess(String title, String message) {
        Platform.runLater(() ->
            PixelMotion.toastGlitch(nameField, title,
                message == null ? "" : message,
                PixelMotion.ToastType.OK));
    }
    
    // ==========================================
    // InitializableController Interface Methods
    // ==========================================
    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
        System.out.println("AddStaffController: Server injected");
    }
    
    @Override
    public void setClient(ClientInterface client) {
        this.client = client;
        System.out.println("AddStaffController: Client injected");
    }
    
    @Override
    public void setDatabaseConnection(Connection con) {
        this.con = con;
        System.out.println("AddStaffController: Database connection injected: " + (con != null ? "Available" : "NULL"));
    }

    // =================== PASSWORD HASHING METHOD ===================

    /**
     * Hashes a plain-text password using PBKDF2.
     * Returns a string in the format "iterations:salt_base64:hash_base64"
     */
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
}