package admin_controllers;

import animation.PixelMotion;

import member_controllers.ClientInterface;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ResourceBundle;
import java.util.regex.Pattern;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
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
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import java.rmi.RemoteException;
import javafx.application.Platform;

public class EditCustomerController implements Initializable, InitializableController {

    @FXML private VBox rootModal;
    @FXML private Label titleLabel;
    @FXML private TextField nameField;
    @FXML private ComboBox<String> memberTypeCombo;
    @FXML private TextField phoneField;
    @FXML private TextField emailField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label pointsLabel;

    private Customer selectedCustomer;
    private CustomersCardController cardController;
    private Connection con;
    private int member_id;
    private int member_type_id;
    private int confirm_member_type_id;
    private Runnable refreshCallback;
    private ServerInterface server;
    private ClientInterface client;
    private String currentPasswordHash; // now holds the hashed password

    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z\\s]{2,50}$");
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
    private static final byte[] STATIC_SALT = "Member_Salt_32_bytes_long_key!!".getBytes(); // replace with per-user salt in production

    public void setParentController(CustomersCardController controller) { 
        this.cardController = controller; 
    }
    
    public void setRefreshCallback(Runnable callback) {
        this.refreshCallback = callback;
    }
    
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        System.out.println("EditCustomerController: Initialized");
    }

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
    
    private boolean validateNewPassword(String newPassword, boolean isChanging) {
        if (!isChanging) {
            return true;
        }
        if (newPassword == null || newPassword.isEmpty()) {
            showError("Validation Error", "New password cannot be empty");
            newPasswordField.setPromptText("Enter new password!");
            return false;
        }
        if (!PASSWORD_PATTERN.matcher(newPassword).matches()) {
            StringBuilder requirements = new StringBuilder();
            requirements.append("Password must meet ALL requirements:\n\n");
            
            if (newPassword.length() < 8) {
                requirements.append("❌ At least 8 characters long\n");
            } else {
                requirements.append("✓ At least 8 characters long\n");
            }
            if (!newPassword.matches(".*[0-9].*")) {
                requirements.append("❌ At least 1 number (0-9)\n");
            } else {
                requirements.append("✓ At least 1 number (0-9)\n");
            }
            if (!newPassword.matches(".*[a-z].*")) {
                requirements.append("❌ At least 1 lowercase letter (a-z)\n");
            } else {
                requirements.append("✓ At least 1 lowercase letter (a-z)\n");
            }
            if (!newPassword.matches(".*[A-Z].*")) {
                requirements.append("❌ At least 1 uppercase letter (A-Z)\n");
            } else {
                requirements.append("✓ At least 1 uppercase letter (A-Z)\n");
            }
            if (!newPassword.matches(".*[@#$%^&+=!].*")) {
                requirements.append("❌ At least 1 special character (@#$%^&+=!)\n");
            } else {
                requirements.append("✓ At least 1 special character (@#$%^&+=!)\n");
            }
            if (newPassword.contains(" ")) {
                requirements.append("❌ No spaces allowed\n");
            } else {
                requirements.append("✓ No spaces allowed\n");
            }
            
            showError("Invalid Password Format", requirements.toString());
            newPasswordField.clear();
            confirmPasswordField.clear();
            newPasswordField.setPromptText("Enter valid password!");
            confirmPasswordField.setPromptText("Confirm password!");
            return false;
        }
        return true;
    }
    
    private boolean validatePasswordsMatch(String newPassword, String confirmPassword) {
        if (newPassword == null || newPassword.isEmpty()) {
            return true;
        }
        if (!newPassword.equals(confirmPassword)) {
            showError("Validation Error", "New passwords do not match!");
            confirmPasswordField.clear();
            confirmPasswordField.setPromptText("Passwords don't match!");
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
    
    private boolean checkDuplicateName(String name, int excludeMemberId) {
        if (con == null) return false;
        String sql = "SELECT COUNT(*) FROM internet_cafe.member WHERE member_name = ? AND member_id != ?";
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, name);
            pst.setInt(2, excludeMemberId);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    private boolean checkDuplicatePhone(String phone, int excludeMemberId) {
        if (con == null) return false;
        String sql = "SELECT COUNT(*) FROM internet_cafe.member WHERE phone = ? AND member_id != ?";
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, phone);
            pst.setInt(2, excludeMemberId);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }
    
    private boolean checkDuplicateEmail(String email, int excludeMemberId) {
        if (con == null) return false;
        String sql = "SELECT COUNT(*) FROM internet_cafe.member WHERE email = ? AND member_id != ?";
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, email);
            pst.setInt(2, excludeMemberId);
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
    private void close(ActionEvent event) {
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }

    @FXML
    void AddPoints(ActionEvent event) {
        openModal(event);
    }

    @FXML
    private void save(ActionEvent event) throws RemoteException {
        if (con == null) {
            showError("Database Error", "Database connection not available");
            return;
        }
    
        String newName = nameField.getText().trim();
        String newPhone = phoneField.getText().trim();
        String newEmail = emailField.getText().trim();
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        
        boolean changingPassword = !newPassword.isEmpty();
        
        String originalName = selectedCustomer.getMemberName();
        String originalTier = getMemberTierFromDatabase(selectedCustomer.getMemberId());
        
        if (!validateName(newName)) return;
        if (!validateMemberType()) return;
        if (!validatePhoneNumber(newPhone)) return;
        if (!validateEmail(newEmail)) return;
        
        if (checkDuplicateName(newName, member_id)) {
            showError("Duplicate Name", "A customer with this name already exists.");
            nameField.clear();
            return;
        }
        if (checkDuplicatePhone(newPhone, member_id)) {
            showError("Duplicate Phone", "A customer with this phone number already exists.");
            phoneField.clear();
            return;
        }
        if (checkDuplicateEmail(newEmail, member_id)) {
            showError("Duplicate Email", "A customer with this email already exists.");
            emailField.clear();
            return;
        }
        
        if (changingPassword) {
            if (!validateNewPassword(newPassword, true)) return;
            if (!validatePasswordsMatch(newPassword, confirmPassword)) return;
        }
        
        confirmMemberTypeId();
        
        // Hash new password if provided
        String hashedPassword = null;
        if (changingPassword) {
            hashedPassword = hashPassword(newPassword);
            if (hashedPassword == null) {
                showError("Error", "Failed to secure new password.");
                return;
            }
        }
        
        String sql;
        if (changingPassword) {
            sql = "UPDATE internet_cafe.member SET member_name=?, member_type_id=?, phone=?, email=?, password=? WHERE member_id=?";
        } else {
            sql = "UPDATE internet_cafe.member SET member_name=?, member_type_id=?, phone=?, email=? WHERE member_id=?";
        }
        
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, newName);
            pst.setInt(2, confirm_member_type_id);
            pst.setString(3, newPhone);
            pst.setString(4, newEmail);
            
            if (changingPassword) {
                pst.setString(5, hashedPassword);   // store the hash
                pst.setInt(6, member_id);
            } else {
                pst.setInt(5, member_id);
            }
            
            int rowsAffected = pst.executeUpdate();
            if (rowsAffected > 0) {
                selectedCustomer.setMemberName(newName);
                selectedCustomer.setMemberTypeId(confirm_member_type_id);
                selectedCustomer.setPhone(newPhone);
                selectedCustomer.setEmail(newEmail);
                if (changingPassword) {
                    selectedCustomer.setPassword(hashedPassword); // now holds hash
                    currentPasswordHash = hashedPassword;
                }
       
                if (cardController != null) cardController.setData(selectedCustomer);
                if (refreshCallback != null) refreshCallback.run();

                if (server != null) {
                    int memberId = selectedCustomer.getMemberId();
                    if (!newName.equals(originalName)) {
                        server.updateMemberNameAndNotify(memberId, newName);
                    }
                    String newTier = memberTypeCombo.getSelectionModel().getSelectedItem();
                    if (newTier != null && !newTier.equals(originalTier)) {
                        server.updateMemberTierAndNotify(memberId, newTier);
                    }
                }
                
                showSuccess("Success", "Customer updated successfully!");
                closeEditPopup(event);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Database Error", "Failed to update customer: " + e.getMessage());
        }
    }

    @FXML
    private void cancel(ActionEvent event) {
        closeEditPopup(event);
    }
    
    private void loadMemberType() {
        if (con == null) return;
        
        String sql = "SELECT member_type_name FROM internet_cafe.member_type";
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            
            memberTypeCombo.getItems().clear();
            while (rs.next()) {
                memberTypeCombo.getItems().add(rs.getString("member_type_name"));
            }
            
            if (selectedCustomer != null && member_type_id > 0) {
                setSelectedMemberType(member_type_id);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showError("Database Error", "Failed to load member types: " + e.getMessage());
        }
    }
    
    private void setSelectedMemberType(int id) {
        if (con == null || memberTypeCombo.getItems().isEmpty()) return;
        
        String sql = "SELECT member_type_name FROM internet_cafe.member_type WHERE member_type_id = ?";
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setInt(1, id);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    memberTypeCombo.setValue(rs.getString("member_type_name"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    private void confirmMemberTypeId() {
        if (con == null) return;
        
        String memberType = memberTypeCombo.getSelectionModel().getSelectedItem();
        if (memberType == null) return;
        
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
    
    public void initData(Customer customer) {
        this.selectedCustomer = customer;
        this.member_id = customer.getMemberId();
        this.member_type_id = customer.getMemberTypeId();
        this.currentPasswordHash = customer.getPassword();

        nameField.setText(customer.getMemberName());
        phoneField.setText(customer.getPhone());
        emailField.setText(customer.getEmail());

        newPasswordField.clear();
        confirmPasswordField.clear();

        pointsLabel.setText((int) customer.getPoint() + " pts");

        if (con != null && !memberTypeCombo.getItems().isEmpty()) {
            setSelectedMemberType(member_type_id);
        }
    }
    
    @FXML
    private void openModal(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/add_point.fxml"));
            Parent root = loader.load();

            AddPointController addController = loader.getController();
            addController.setParentController(this);
            
            if (addController instanceof InitializableController) {
                ((InitializableController) addController).setServer(server);
                ((InitializableController) addController).setClient(client);
                ((InitializableController) addController).setDatabaseConnection(con);
            }
            
            addController.initPoint(selectedCustomer);
            
            PixelMotion.showOverlayInStack(resolveContentStack((Node) event.getSource()), root, false);
        } catch (IOException e) {
            e.printStackTrace();
            showError("Error", "Failed to open points modal: " + e.getMessage());
        }
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

    public void updatePointsDisplay(double newPoints) {
        pointsLabel.setText((int) newPoints + " pts");
    }
    
    @FXML 
    private void closeEditPopup(ActionEvent event) {
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }
    
    private void showError(String title, String message) {
        javafx.application.Platform.runLater(() ->
            PixelMotion.toastGlitch(nameField, title,
                message == null ? "Unknown error" : message,
                PixelMotion.ToastType.ERROR));
    }
    
    private void showSuccess(String title, String message) {
        javafx.application.Platform.runLater(() ->
            PixelMotion.toastGlitch(nameField, title,
                message == null ? "" : message,
                PixelMotion.ToastType.OK));
    }
    
    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
    }
    
    @Override
    public void setClient(ClientInterface client) {
        this.client = client;
    }
    
    @Override
    public void setDatabaseConnection(Connection con) {
        this.con = con;
        if (con != null && memberTypeCombo != null) {
            loadMemberType();
        }
        if (selectedCustomer != null && con != null && !memberTypeCombo.getItems().isEmpty()) {
            setSelectedMemberType(member_type_id);
        }
    }
    
    private String getMemberTierFromDatabase(int memberId) {
        String sql = "SELECT t.member_type_name FROM internet_cafe.member m " +
                 "JOIN internet_cafe.member_type t ON m.member_type_id = t.member_type_id " +
                 "WHERE m.member_id = ?";
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setInt(1, memberId);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                return rs.getString("member_type_name");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return "";
    }
    
    public void updateTierDisplay(String newTier) {
        if (memberTypeCombo != null && newTier != null) {
            Platform.runLater(() -> memberTypeCombo.setValue(newTier));
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
     * Run after enlarging the 'password' column to VARCHAR(255).
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