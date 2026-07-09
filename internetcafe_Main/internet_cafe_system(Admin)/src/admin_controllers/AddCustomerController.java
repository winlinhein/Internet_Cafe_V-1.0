/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/javafx/FXMLController.java to edit this template
 */
package admin_controllers;

import animation.PixelMotion;

import member_controllers.ClientInterface;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.regex.Pattern;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.Base64;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;

/**
 * FXML Controller class
 *
 * @author Hello
 */
public class AddCustomerController implements Initializable, InitializableController {

    @FXML private VBox rootModal;
    @FXML private Label titleLabel;
    @FXML private TextField nameField;
    @FXML private ComboBox<String> memberTypeCombo;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPassword;
    @FXML private Label pointsLabel;

    private Customer currentCustomer;
    private CustomersController mainController;
    private CustomersCardController cardController;
    private Runnable refreshCallback;
    private int confirm_member_type_id;
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
    private static final byte[] STATIC_SALT = "Member_Salt_32_bytes_long_key!!".getBytes(); // use per-user salt in production

    public void setParentController(CustomersController controller) {
        this.mainController = controller;
    }

    public void setRefreshCallback(Runnable callback) {
        this.refreshCallback = callback;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (con != null) {
            System.out.println("AddCustomerController: Database connection available");
            loadMemberType();
        } else {
            System.out.println("AddCustomerController: Waiting for database connection to be injected...");
        }
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
            passwordField.setPromptText("Enter password!");
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
            passwordField.clear();
            confirmPassword.clear();
            passwordField.setPromptText("Enter valid password!");
            confirmPassword.setPromptText("Confirm password!");
            return false;
        }
        return true;
    }

    private boolean validatePasswordsMatch(String password, String confirm) {
        if (!password.equals(confirm)) {
            showError("Validation Error", "Passwords do not match!");
            confirmPassword.clear();
            confirmPassword.setPromptText("Passwords don't match!");
            return false;
        }
        return true;
    }

    private boolean validateMemberType() {
        if (memberTypeCombo.getSelectionModel().getSelectedIndex() == -1) {
            showError("Validation Error", "Please select a member type");
            memberTypeCombo.setPromptText("Select member type!");
            return false;
        }
        return true;
    }

    @FXML
    private void close(ActionEvent event) {
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }

    @FXML
    private void openPointAction(MouseEvent event) {
        openModal(event);
    }

    @FXML
    private void save(ActionEvent event) {
        if (con == null) {
            System.err.println("Cannot save: database connection is null");
            showError("Database Error", "Database connection not available");
            return;
        }

        String name = nameField.getText().trim();
        String phone = phoneField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        String confirm = confirmPassword.getText();

        if (!validateName(name)) return;
        if (!validateMemberType()) return;
        if (!validatePhoneNumber(phone)) return;
        if (!validateEmail(email)) return;
        if (!validatePassword(password)) return;
        if (!validatePasswordsMatch(password, confirm)) return;

        confirmMemberTypeId();

        if (isNameExists(name)) {
            showError("Duplicate Name", "A customer with this name already exists. Please use a different name.");
            nameField.clear();
            nameField.setPromptText("Name already exists!");
            return;
        }
        if (isPhoneExists(phone)) {
            showError("Duplicate Phone", "A customer with this phone number already exists.");
            phoneField.clear();
            phoneField.setPromptText("Phone already exists!");
            return;
        }
        if (isEmailExists(email)) {
            showError("Duplicate Email", "A customer with this email already exists.");
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

        String sql = "INSERT INTO internet_cafe.member (member_name, member_type_id, phone, email, password, point) VALUES (?,?,?,?,?,?)";

        try (PreparedStatement pst = con.prepareStatement(sql)) {
            String rawPoints = pointsLabel.getText().replace(" pts", "").trim();
            double points = Double.parseDouble(rawPoints);

            pst.setString(1, name);
            pst.setInt(2, confirm_member_type_id);
            pst.setString(3, phone);
            pst.setString(4, email);
            pst.setString(5, hashedPassword);   // Store the hash, not the plain password
            pst.setDouble(6, points);

            int rowsAffected = pst.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Customer added successfully: " + name);

                showSuccess("Success", "Customer added successfully!\n\n" +
                    "Name: " + name + "\n" +
                    "Phone: " + phone + "\n" +
                    "Email: " + email + "\n" +
                    "Initial Points: " + points);

                if (refreshCallback != null) {
                    refreshCallback.run();
                }
                closeEditPopup(event);
            } else {
                showError("Error", "Failed to add customer. Please try again.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Database Error", "Could not add customer: " + e.getMessage());
        } catch (NumberFormatException e) {
            showError("Error", "Invalid points value");
        }
    }

    private boolean isNameExists(String name) {
        if (con == null) return false;
        String sql = "SELECT COUNT(*) FROM internet_cafe.member WHERE member_name = ?";
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

    private boolean isPhoneExists(String phone) {
        if (con == null) return false;
        String sql = "SELECT COUNT(*) FROM internet_cafe.member WHERE phone = ?";
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

    private boolean isEmailExists(String email) {
        if (con == null) return false;
        String sql = "SELECT COUNT(*) FROM internet_cafe.member WHERE email = ?";
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
    private void cancel(ActionEvent event) {
        closeEditPopup(event);
    }

    private void loadMemberType() {
        if (con == null) {
            System.err.println("Cannot load member types: database connection is null");
            return;
        }
        String sql = "SELECT member_type_name FROM internet_cafe.member_type";
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            memberTypeCombo.getItems().clear();
            while (rs.next()) {
                memberTypeCombo.getItems().add(rs.getString("member_type_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Database Error", "Failed to load member types: " + e.getMessage());
        }
    }

    private void confirmMemberTypeId() {
        if (con == null) {
            System.err.println("Cannot confirm member type: database connection is null");
            return;
        }
        String memberType = memberTypeCombo.getSelectionModel().getSelectedItem();
        String sql = "SELECT member_type_id from internet_cafe.member_type WHERE member_type_name=?";
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, memberType);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    confirm_member_type_id = rs.getInt("member_type_id");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void openModal(MouseEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/add_new_point.fxml"));
            Parent root = loader.load();

            AddNewPointController addController = loader.getController();
            addController.setParentController(this);

            if (addController instanceof InitializableController) {
                ((InitializableController) addController).setServer(server);
                ((InitializableController) addController).setClient(client);
                ((InitializableController) addController).setDatabaseConnection(con);
            }

            PixelMotion.showOverlayInStack(resolveContentStack((Node) event.getSource()), root, false);
        } catch (IOException e) {
            e.printStackTrace();
            showError("Error", "Failed to open points modal: " + e.getMessage());
        }
    }

    public void updatePointsDisplay(double newPoints) {
        pointsLabel.setText((int) newPoints + " pts");
    }

    @FXML
    private void closeEditPopup(ActionEvent event) {
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }

    private StackPane resolveContentStack(Node node) {
        Node current = node;
        while (current != null) {
            if (current instanceof StackPane && Boolean.TRUE.equals(current.getProperties().get("px.overlay.content"))) {
                Parent parent = current.getParent();
                if (parent instanceof StackPane) {
                    return (StackPane) parent;
                }
            }
            current = current.getParent();
        }
        return node != null && node.getScene() != null && node.getScene().getRoot() instanceof StackPane ? (StackPane) node.getScene().getRoot() : null;
    }

    private void showError(String title, String message) {
        javafx.application.Platform.runLater(() ->
            animation.PixelMotion.toastGlitch(nameField, title,
                message == null ? "Unknown error" : message,
                animation.PixelMotion.ToastType.ERROR));
    }

    private void showSuccess(String title, String message) {
        javafx.application.Platform.runLater(() ->
            animation.PixelMotion.toastGlitch(nameField, title,
                message == null ? "" : message,
                animation.PixelMotion.ToastType.OK));
    }

    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
        System.out.println("AddCustomerController: Server injected");
    }

    @Override
    public void setClient(ClientInterface client) {
        this.client = client;
        System.out.println("AddCustomerController: Client injected");
    }

    @Override
    public void setDatabaseConnection(Connection con) {
        this.con = con;
        System.out.println("AddCustomerController: Database connection injected");
        if (con != null && memberTypeCombo != null) {
            loadMemberType();
        }
    }

    // =================== NEW: Password Hashing Methods ===================

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

    /**
     * One‑time migration: updates all plain‑text passwords to hashed versions.
     * Call this after enlarging the 'password' column to VARCHAR(255).
     */
    public void migrateMemberPasswords() {
        if (con == null) return;
        String selectSql = "SELECT member_id, password FROM internet_cafe.member";
        try (PreparedStatement pstSelect = con.prepareStatement(selectSql);
             ResultSet rs = pstSelect.executeQuery()) {

            PreparedStatement pstUpdate = con.prepareStatement(
                "UPDATE internet_cafe.member SET password = ? WHERE member_id = ?"
            );
            int migrated = 0;
            while (rs.next()) {
                int id = rs.getInt("member_id");
                String current = rs.getString("password");
                if (current == null) continue;
                // Skip if already a hash (contains ":" and exactly 3 parts)
                if (current.contains(":") && current.split(":").length == 3) continue;

                String newHash = hashPassword(current);
                if (newHash == null) continue;
                pstUpdate.setString(1, newHash);
                pstUpdate.setInt(2, id);
                pstUpdate.executeUpdate();
                migrated++;
                System.out.println("Migrated member ID: " + id);
            }
            pstUpdate.close();
            System.out.println("Member password migration complete. Hashed " + migrated + " passwords.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}