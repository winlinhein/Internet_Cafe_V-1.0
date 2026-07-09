package admin_controllers;

import animation.AnimationUtil;
import animation.PixelFX;
import animation.PixelMotion;
import animation.UniversalGlitch;
import database.DatabaseConnection;

import java.io.IOException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ResourceBundle;
import java.util.regex.Pattern;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class Admin_Login implements Initializable {

    @FXML private VBox loginBox, OTPwindow, Resetpwd, logobox;
    @FXML private HBox titleBox, backBar, BackBar4ResetPwd;
    @FXML private Pane forgotPane;
    @FXML private Button btnBackForgotpwd, btnOTP, btnVerify, btnResetPwdEnter,
                        btnClear, btnEnter, btnMin;
    @FXML private Label ForgotPwd, lblWelcome;
    @FXML private ImageView logo;
    @FXML private PasswordField txtPw;
    @FXML private TextField txtEmail;
    @FXML private TextField OTP1, OTP2, OTP3, OTP4, OTP5, OTP6;
    @FXML private TextField txtUsername;
    @FXML private PasswordField pwNew, pwConfirm;
    @FXML private AnchorPane LoginPanel;
    @FXML private Label timerLabel;
    @FXML private Button btnResendOTP;

    private Connection con;
    private String generatedOTP;
    private long otpGeneratedMillis;
    private Timeline otpTimeline;
    private TextField[] otpFields;
    public static String admin_name;
    private String pendingEmail;

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");

    private static final String PBKDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final byte[] STATIC_SALT = "My_Super_Secret_Fixed_Salt_32bytes!".getBytes();

    // Rate limiter fields
    private int failedAttempts = 0;
    private long blockedUntil = 0;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        javafx.scene.text.Font.loadFont(
            getClass().getResourceAsStream("/fonts/PressStart2P-Regular.ttf"), 12);

        DatabaseConnection db = new DatabaseConnection();
        try {
            con = db.connectDB();
        } catch (Exception ex) {
            System.err.println("Connection Failed: " + ex.getMessage());
            showAlert(Alert.AlertType.ERROR, "Database Error",
                      "Could not connect to database.");
        }

        AnimationUtil.animateAppear(loginBox, 0.3);
        PixelFX.installAll(titleBox);
        AnimationUtil.animateAppear(logo, 0.3);
        AnimationUtil.animateAppear(lblWelcome, 0.2);
        UniversalGlitch.attach(lblWelcome).start();
        UniversalGlitch.attach(logo).start();

        otpFields = new TextField[]{OTP1, OTP2, OTP3, OTP4, OTP5, OTP6};
        for (TextField field : otpFields) {
            field.setTextFormatter(new TextFormatter<>(change ->
                (change.getControlNewText().length() <= 1) ? change : null));
        }
       
    }

    @FXML
    void sendOTP(ActionEvent event) {
        if (con == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "Database not connected.");
            return;
        }
        String inputEmail = txtEmail.getText().trim();
        if (inputEmail.isEmpty() || !EMAIL_PATTERN.matcher(inputEmail).matches()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Please enter a valid email address.");
            return;
        }

        String sql = "SELECT email FROM internet_cafe.admin WHERE LOWER(TRIM(email)) = LOWER(TRIM(?))";
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, inputEmail);
            ResultSet rs = pst.executeQuery();
            if (!rs.next()) {
                showAlert(Alert.AlertType.ERROR, "Not Found", "This email is not registered.");
                return;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error", "Unable to verify email.");
            return;
        }

        pendingEmail = inputEmail;
        generateAndStartTimer();
    }

    @FXML
    void handleResendOTP(ActionEvent event) {
        if (pendingEmail == null || pendingEmail.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "No email available. Please go back.");
            return;
        }
        generateAndStartTimer();
    }

    private void generateAndStartTimer() {
        generatedOTP = generateCode(6);
        otpGeneratedMillis = System.currentTimeMillis();

        new Thread(() -> Methods.sendEmail(pendingEmail, generatedOTP)).start();

        hideResendButton();

        if (!OTPwindow.isVisible()) {
            PixelFX.glitchSwitch(forgotPane, OTPwindow);
            PixelFX.glitchSwitch(backBar, OTPwindow);
        }

        for (TextField f : otpFields) f.clear();

        if (otpTimeline != null) otpTimeline.stop();
        startOtpTimer();
    }

    @FXML
    void VerifyOTP(ActionEvent event) {
        if (generatedOTP == null) {
            showAlert(Alert.AlertType.ERROR, "No OTP", "Please request an OTP first.");
            return;
        }
        long elapsedSec = (System.currentTimeMillis() - otpGeneratedMillis) / 1000;
        if (elapsedSec > 180) {
            showAlert(Alert.AlertType.ERROR, "Expired", "OTP has expired. Use the Resend button.");
            generatedOTP = null;
            showResendButton();
            if (otpTimeline != null) otpTimeline.stop();
            return;
        }
        String userInput = getOtpCode();
        if (userInput.equals(generatedOTP)) {
            if (otpTimeline != null) otpTimeline.stop();
            generatedOTP = null;
            AnimationUtil.fadeSwitch(OTPwindow, Resetpwd);
            AnimationUtil.fadeSwitch(OTPwindow, BackBar4ResetPwd);
        } else {
            showAlert(Alert.AlertType.ERROR, "Invalid OTP", "The code you entered is incorrect.");
        }
    }

    private void startOtpTimer() {
        if (timerLabel == null) return;
        final int DURATION_SEC = 180;
        timerLabel.setText("3:00");
        otpTimeline = new Timeline(
            new KeyFrame(Duration.seconds(1), e -> {
                long elapsed = (System.currentTimeMillis() - otpGeneratedMillis) / 1000;
                long remaining = DURATION_SEC - elapsed;
                if (remaining <= 0) {
                    otpTimeline.stop();
                    generatedOTP = null;
                    timerLabel.setText("0:00");
                    showResendButton();
                } else {
                    int min = (int) remaining / 60;
                    int sec = (int) remaining % 60;
                    timerLabel.setText(String.format("%d:%02d", min, sec));
                }
            })
        );
        otpTimeline.setCycleCount(Timeline.INDEFINITE);
        otpTimeline.play();
    }

    private void showResendButton() {
         if (btnResendOTP != null) {
            btnResendOTP.setManaged(true);
            btnResendOTP.setVisible(true);
            btnResendOTP.setDisable(false);
        }
        if (btnVerify != null) {
            btnVerify.setManaged(false);
            btnVerify.setVisible(false);
        }   
        for (TextField f : otpFields) f.setDisable(true);
    }

    private void hideResendButton() {
        if (btnResendOTP != null) {
            btnResendOTP.setVisible(false);
            btnResendOTP.setManaged(false);
            btnResendOTP.setDisable(true);
        }
        if (btnVerify != null) {
            btnVerify.setManaged(true);
            btnVerify.setVisible(true);
        }
        for (TextField f : otpFields) f.setDisable(false);
    }

    private String getOtpCode() {
        StringBuilder sb = new StringBuilder();
        for (TextField field : otpFields) {
            if (!field.getText().isEmpty()) {
                sb.append(field.getText());
            }
        }
        return sb.toString();
    }

    @FXML
    private void handleForgotAction(MouseEvent event) {
        AnimationUtil.fadeSwitch(loginBox, forgotPane);
        AnimationUtil.fadeSwitch(loginBox, backBar);
    }

    @FXML
    void BactToLogin(MouseEvent event) {
        AnimationUtil.fadeSwitch(forgotPane, loginBox);
        AnimationUtil.fadeSwitch(backBar, loginBox);
        if (otpTimeline != null) otpTimeline.stop();
        generatedOTP = null;
        pendingEmail = null;
    }

    @FXML
    void Back2Login(ActionEvent event) {
        AnimationUtil.fadeSwitch(Resetpwd, loginBox);
        AnimationUtil.fadeSwitch(BackBar4ResetPwd, loginBox);
        if (otpTimeline != null) otpTimeline.stop();
        generatedOTP = null;
        pendingEmail = null;
    }

    @FXML
    void Reset2Login(ActionEvent event) {
        if (con == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "Database not connected.");
            return;
        }
        String newPassword = pwNew.getText();
        String confirmPassword = pwConfirm.getText();
        String userEmail = txtEmail.getText().trim();

        System.out.println("=== Reset2Login called ===");
        System.out.println("newPassword     : '" + newPassword + "' (length=" + newPassword.length() + ")");
        System.out.println("confirmPassword : '" + confirmPassword + "' (length=" + confirmPassword.length() + ")");
        System.out.println("userEmail       : '" + userEmail + "'");

        if (newPassword.trim().isEmpty() || confirmPassword.trim().isEmpty()) {
            System.out.println("-> REJECTED: empty field");
            showAlert(Alert.AlertType.ERROR, "Error", "Please fill in both password fields.");
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            System.out.println("-> REJECTED: passwords don't match");
            showAlert(Alert.AlertType.ERROR, "Error", "Passwords do not match!");
            return;
        }

        String passwordError = validatePasswordFormat(newPassword);
        if (passwordError != null) {
            System.out.println("-> REJECTED: " + passwordError);
            showAlert(Alert.AlertType.ERROR, "Invalid Password", passwordError);
            pwNew.clear();
            pwConfirm.clear();
            pwNew.setPromptText("Enter valid password!");
            pwConfirm.setPromptText("Confirm password!");
            return;
        }

        String hashed = hashPassword(newPassword);
        if (hashed == null) {
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to secure password.");
            return;
        }

        String sql = "UPDATE internet_cafe.admin SET passwords = ? WHERE LOWER(TRIM(email)) = LOWER(TRIM(?))";
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, hashed);
            pst.setString(2, userEmail);
            int rows = pst.executeUpdate();
            System.out.println("Rows updated: " + rows);
            if (rows > 0) {
                showAlert(Alert.AlertType.INFORMATION, "Success", "Password updated successfully!");
                pwNew.clear();
                pwConfirm.clear();
                txtEmail.clear();
                txtUsername.clear();
                txtPw.clear();
                AnimationUtil.fadeSwitch(Resetpwd, loginBox);
                AnimationUtil.fadeSwitch(BackBar4ResetPwd, loginBox);
            } else {
                System.out.println("-> REJECTED: 0 rows updated (email not found?)");
                showAlert(Alert.AlertType.ERROR, "Error", "Failed to update password. Email may not be valid.");
            }
        } catch (SQLException e) {
            System.err.println("SQL Exception:");
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Database Error",
                      "An error occurred while updating the password:\n" + e.getMessage());
        }
    }

    private boolean isLoginBlocked() {
        if (blockedUntil > 0 && System.currentTimeMillis() < blockedUntil) {
            return true;
        } else if (blockedUntil > 0) {
            failedAttempts = 0;
            blockedUntil = 0;
        }
        return false;
    }

  @FXML
    void handleEnterAction(ActionEvent event) {
        if (isLoginBlocked()) {
            long remainingSec = (blockedUntil - System.currentTimeMillis()) / 1000;
            showAlert(Alert.AlertType.ERROR, "Too Many Attempts",
                      "Please wait " + remainingSec + " seconds before trying again.");
            return;
        }

        if (con == null) {
            showAlert(Alert.AlertType.ERROR, "Database Error", "No database connection.");
            return;
        }
        String user = txtUsername.getText().trim();
        String pass = txtPw.getText();

        if (user.isEmpty() || pass.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Form Error!", "Please enter your credentials.");
            return;
        }

        btnEnter.setDisable(true);

        Task<Boolean> loginTask = new Task<>() {
            @Override
            protected Boolean call() throws Exception {
                String sql = "SELECT admin_name, passwords FROM internet_cafe.admin WHERE admin_name = ?";
                try (PreparedStatement pst = con.prepareStatement(sql)) {
                    pst.setString(1, user);
                    ResultSet rs = pst.executeQuery();
                    if (rs.next()) {
                        String storedHash = rs.getString("passwords");
                        boolean ok;
                        if (storedHash != null && storedHash.contains(":") && storedHash.split(":").length == 3) {
                            ok = verifyPassword(pass, storedHash);
                        } else {
                            ok = pass.equals(storedHash);
                            if (ok) {
                                String newHash = hashPassword(pass);
                                updatePasswordHash(user, newHash);
                            }
                        }
                        if (ok) {
                            admin_name = rs.getString("admin_name");
                        }
                        return ok;
                    }
                    return false;
                }
            }
        };

        loginTask.setOnSucceeded(e -> {
            btnEnter.setDisable(false);
            boolean authenticated = loginTask.getValue();
            if (authenticated) {
                failedAttempts = 0;
                blockedUntil = 0;
                loadDashboard(event);
            } else {
                failedAttempts++;
                if (failedAttempts >= 5) {
                    blockedUntil = System.currentTimeMillis() + 60_000;
                    showAlert(Alert.AlertType.ERROR, "Account Locked",
                              "Too many failed attempts. Please wait 1 minute.");
                }
                showAlert(Alert.AlertType.ERROR, "Login Failed", "Invalid Username or Password");
            }
        });

        loginTask.setOnFailed(e -> {
            btnEnter.setDisable(false);
            loginTask.getException().printStackTrace();
            failedAttempts++;
            if (failedAttempts >= 5) {
                blockedUntil = System.currentTimeMillis() + 60_000;
                showAlert(Alert.AlertType.ERROR, "Account Locked",
                          "Too many failed attempts. Please wait 1 minute.");
            }
            showAlert(Alert.AlertType.ERROR, "Database Error", "Error during login");
        });

        new Thread(loginTask).start();
    }

    private void updatePasswordHash(String adminName, String newHash) {
        if (con == null) return;
        String sql = "UPDATE internet_cafe.admin SET passwords = ? WHERE admin_name = ?";
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, newHash);
            pst.setString(2, adminName);
            pst.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadDashboard(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/admin_dashboard.fxml"));
            Parent root = loader.load();
            Object controller = loader.getController();
            Admin_Main.setActiveController(controller);
            Stage currentStage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            currentStage.getScene().setRoot(root);
            currentStage.setResizable(true);
            currentStage.setFullScreenExitHint("");
            currentStage.setFullScreen(false);
            Platform.runLater(() -> currentStage.setFullScreen(true));
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Could not load dashboard.");
        }
    }

    @FXML
    void handleClearAction(ActionEvent event) {
        txtUsername.clear();
        txtPw.clear();
        txtEmail.clear();
        pwNew.clear();
        pwConfirm.clear();
        for (TextField f : otpFields) f.clear();
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.close();
    }

    @FXML
    void handleMinAction(ActionEvent event) {
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML void To2(KeyEvent event) {
        if (event.getCode().isLetterKey() || event.getCode().isDigitKey()) OTP2.requestFocus();
    }
    @FXML void To3(KeyEvent event) {
        if (event.getCode() == KeyCode.BACK_SPACE && OTP2.getText().isEmpty()) OTP1.requestFocus();
        else if (event.getCode().isLetterKey() || event.getCode().isDigitKey())
            if (!OTP2.getText().isEmpty()) OTP3.requestFocus();
    }
    @FXML void To4(KeyEvent event) {
        if (event.getCode() == KeyCode.BACK_SPACE && OTP3.getText().isEmpty()) OTP2.requestFocus();
        else if (event.getCode().isLetterKey() || event.getCode().isDigitKey())
            if (!OTP3.getText().isEmpty()) OTP4.requestFocus();
    }
    @FXML void To5(KeyEvent event) {
        if (event.getCode() == KeyCode.BACK_SPACE && OTP4.getText().isEmpty()) OTP3.requestFocus();
        else if (event.getCode().isLetterKey() || event.getCode().isDigitKey())
            if (!OTP4.getText().isEmpty()) OTP5.requestFocus();
    }
    @FXML void To6(KeyEvent event) {
        if (event.getCode() == KeyCode.BACK_SPACE && OTP5.getText().isEmpty()) OTP4.requestFocus();
        else if (event.getCode().isLetterKey() || event.getCode().isDigitKey())
            if (!OTP5.getText().isEmpty()) OTP6.requestFocus();
    }
    @FXML void ToEnter(KeyEvent event) {
        if (event.getCode() == KeyCode.BACK_SPACE && OTP6.getText().isEmpty()) OTP5.requestFocus();
        else if (event.getCode().isLetterKey() || event.getCode().isDigitKey())
            if (!OTP6.getText().isEmpty()) btnVerify.requestFocus();
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Platform.runLater(() -> {
            PixelMotion.ToastType toastType = (type == Alert.AlertType.ERROR)
                ? PixelMotion.ToastType.ERROR
                : (type == Alert.AlertType.INFORMATION)
                    ? PixelMotion.ToastType.OK
                    : PixelMotion.ToastType.WARN;
            PixelMotion.toastGlitch(lblWelcome, title,
                content == null ? "" : content, toastType);
        });
    }

    private String generateCode(int length) {
        String chars = "0123456789";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String validatePasswordFormat(String password) {
        if (password.length() < 8) {
            return "Password must be at least 8 characters long.";
        }
        if (!password.matches(".*[0-9].*")) {
            return "Password must contain at least one digit (0-9).";
        }
        if (!password.matches(".*[a-z].*")) {
            return "Password must contain at least one lowercase letter (a-z).";
        }
        if (!password.matches(".*[A-Z].*")) {
            return "Password must contain at least one uppercase letter (A-Z).";
        }
        if (!password.matches(".*[@#$%^&+=!].*")) {
            return "Password must contain at least one special character (@#$%^&+=!).";
        }
        if (password.contains(" ")) {
            return "Password must not contain spaces.";
        }
        return null;
    }

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

            return slowEquals(hash, testHash);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean slowEquals(byte[] a, byte[] b) {
        int diff = a.length ^ b.length;
        for (int i = 0; i < a.length && i < b.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    private void migratePlainPasswordsToHashed() {
        if (con == null) return;
        String selectSql = "SELECT admin_name, passwords FROM internet_cafe.admin";
        try (PreparedStatement pstSelect = con.prepareStatement(selectSql);
             ResultSet rs = pstSelect.executeQuery()) {

            PreparedStatement pstUpdate = con.prepareStatement(
                "UPDATE internet_cafe.admin SET passwords = ? WHERE admin_name = ?"
            );
            int migrated = 0;
            while (rs.next()) {
                String name = rs.getString("admin_name");
                String current = rs.getString("passwords");
                if (current == null) continue;
                if (current.contains(":") && current.split(":").length == 3) continue;

                String newHash = hashPassword(current);
                if (newHash == null) continue;
                pstUpdate.setString(1, newHash);
                pstUpdate.setString(2, name);
                pstUpdate.executeUpdate();
                migrated++;
                System.out.println("Migrated: " + name);
            }
            pstUpdate.close();
            System.out.println("Migration complete. Hashed " + migrated + " passwords.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
