package member_controllers;

import admin_controllers.ServerInterface;
import animation.Login;
import animation.PixelMotion;
import database.DatabaseConnection;
import java.io.File;
import java.net.URL;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ResourceBundle;
import java.util.Base64;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import javafx.stage.Stage;

public class TestController implements Initializable {

    @FXML private StackPane root;
    @FXML private ImageView bgGif;
    @FXML private VBox loginBox;
    @FXML private VBox loadingBox;
    @FXML private VBox logobox;
    @FXML private ImageView logo;
    @FXML private Label lblWelcome1;
    @FXML private Label lblHint;
    @FXML private Label loadingText;
    @FXML private Label loadingSubtext;
    @FXML private Label enterLabel;
    @FXML private Region dimOverlay;
    @FXML private Region vignetteOverlay;
    @FXML private Region scanlineOverlay;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;

    private boolean introStarted = false;
    private boolean waitingForUnlock = false;
    private boolean stationUnlocked = false;
    private Timeline unlockPoller;
    private ServerInterface server = null;
    private ClientImpl client = null;

    static String clientName = "pc-1";
    private Connection con;
    public static String username;

    private static final String STATION_FLAG_FILE = "unlock_PC-01.flag";
    
    private static Image STATIC_BG_IMAGE = null;
    private static boolean gifLoaded = false;
    private boolean alreadyLoggedIn = false;

    private static final String PBKDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final byte[] STATIC_SALT = "Member_Salt_32_bytes_long_key!!".getBytes();

    private int failedAttempts = 0;
    private long blockedUntil = 0;   

    public static volatile String pendingLogoutMessage = null;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loadStaticBackground();
        setupInitialState();
        Login.installVisuals(root, bgGif, logobox, logo, lblWelcome1, lblHint, scanlineOverlay,
                usernameField, passwordField, enterLabel);
        databaseCon();
        setupIntroClick();
        setupUnlockHotkey();
        setupRMI();

        if (pendingLogoutMessage != null) {
            showLogoutReasonToast(pendingLogoutMessage);
            pendingLogoutMessage = null;   
        }
    }
    
    private void loadStaticBackground() {
        if (gifLoaded) {
            if (bgGif != null && STATIC_BG_IMAGE != null) {
                bgGif.setImage(STATIC_BG_IMAGE);
            }
            return;
        }
    
        Task<Image> loadImageTask = new Task<>() {
            @Override
            protected Image call() throws Exception {
                try (java.io.InputStream is = getClass().getResourceAsStream("/Images/download.gif")) {
                    if (is != null) {
                        return new Image(is);
                    }
                }
                return null;
            }
        };
    
        loadImageTask.setOnSucceeded(e -> {
            STATIC_BG_IMAGE = loadImageTask.getValue();
            gifLoaded = true;
            if (bgGif != null && STATIC_BG_IMAGE != null) {
                bgGif.setImage(STATIC_BG_IMAGE);
            }
        });
    
        loadImageTask.setOnFailed(e -> {
            System.out.println("Background GIF not found");
            gifLoaded = true;
        });
    
        new Thread(loadImageTask).start();
    }

    private void setupRMI() {
        if (client == null) {
            try {
                if (server == null) {
                    Registry registry = LocateRegistry.getRegistry("localhost", 1100);
                    server = (ServerInterface) registry.lookup("Client");
                }
                
                client = new ClientImpl(clientName, null, server);
                client.setTestController(this);
                UnicastRemoteObject.exportObject(client, 0);
                server.registerClient((ClientInterface) client);
                System.out.println("New client created and registered: " + clientName);
                
            } catch (Exception e) {
                e.printStackTrace();
                showToast("Connection Failed", "Connection failed: " + e.getMessage(), PixelMotion.ToastType.ERROR);
            }
        } else {
            try {
                if (server == null) {
                    Registry registry = LocateRegistry.getRegistry("localhost", 1100);
                    server = (ServerInterface) registry.lookup("Client");
                }
                
                if (client != null) {
                    boolean isStillActive = false;
                    try {
                        client.getClientName();
                        isStillActive = true;
                    } catch (RemoteException re) {
                        System.out.println("Old client is no longer active, reconnecting...");
                    }
                    
                    if (!isStillActive) {
                        client = new ClientImpl(clientName, null, server);
                        client.setTestController(this);
                        UnicastRemoteObject.exportObject(client, 0);
                        server.registerClient((ClientInterface) client);
                        System.out.println("Reconnected client: " + clientName);
                    } else {
                        System.out.println("Existing client is still active: " + clientName);
                        client.setTestController(this);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Failed to reconnect client: " + e.getMessage());
            }
        }
    }

    public void setClient(ClientImpl existingClient) {
        this.client = existingClient;
        if (client != null) {
            try {
                if (server == null) {
                    Registry registry = LocateRegistry.getRegistry("localhost", 1100);
                    server = (ServerInterface) registry.lookup("Client");
                }
                
                client.setTestController(this);
                System.out.println("Existing client set in TestController: " + client.getClientName());
                
            } catch (Exception e) {
                e.printStackTrace();
                setupRMI();
            }
        } else {
            setupRMI();
        }
    }

    private void databaseCon() {
        try {
            DatabaseConnection dbobj = new DatabaseConnection();
            con = dbobj.connectDB();
            System.out.println("Connection Success!");
        } catch (Exception e) {
            e.printStackTrace();
            showToast("Database Error", "Database connection failed: " + e.getMessage(), PixelMotion.ToastType.ERROR);
        }
    }

    private void setupInitialState() {
        if (loadingBox != null) {
            loadingBox.setVisible(false);
            loadingBox.setManaged(false);
            loadingBox.setOpacity(0.0);
        }
        if (loginBox != null) {
            loginBox.setVisible(false);
            loginBox.setManaged(false);
            loginBox.setOpacity(0.0);
        }
        if (logobox != null) {
            logobox.setVisible(true);
            logobox.setManaged(true);
            logobox.setOpacity(1.0);
        }
        if (lblWelcome1 != null) {
            lblWelcome1.setVisible(true);
            lblWelcome1.setManaged(true);
            lblWelcome1.setOpacity(1.0);
        }
        if (lblHint != null) {
            lblHint.setVisible(true);
            lblHint.setManaged(true);
            lblHint.setOpacity(1.0);
        }
    }

    private void setupIntroClick() {
        if (root == null) return;
        root.setOnMouseClicked(e -> {
            if (introStarted) return;
            introStarted = true;

            Login.animateIntroOut(logobox, lblWelcome1, lblHint, dimOverlay, vignetteOverlay, () -> {
                Login.showLoadingBox(loadingBox, loadingText, loadingSubtext);
                loadingText.setText("Waiting for admin approval...");
            
                new Thread(() -> {
                    try {
                        if (server != null) {
                            server.requestLogin(clientName);
                        } else {
                            System.err.println("Server not connected");
                        }
                    } catch (RemoteException ex) {
                        ex.printStackTrace();
                        Platform.runLater(() -> {
                            loadingText.setText("Connection error. Please try again.");
                        });
                    }
                }).start();
            
                waitingForUnlock = true;
                startUnlockPolling();
                Platform.runLater(() -> {
                    if (root != null) root.requestFocus();
                });
            });
        });
    }

    private void startUnlockPolling() {
        stopUnlockPolling();

        unlockPoller = new Timeline(new KeyFrame(Duration.millis(2000), e -> {
            if (!waitingForUnlock) return;
            if (checkIfStationUnlocked()) {
                waitingForUnlock = false;
                stopUnlockPolling();
                Login.hideLoadingBox(loadingBox, () -> {
                    Login.showLoginBox(loginBox, usernameField, passwordField, enterLabel);
                    Platform.runLater(() -> {
                        if (usernameField != null) usernameField.requestFocus();
                    });
                });
            }
        }));
        unlockPoller.setCycleCount(Animation.INDEFINITE);
        unlockPoller.play();
    }

    private boolean checkIfStationUnlocked() {
        if (stationUnlocked) return true;
        File flagFile = new File(STATION_FLAG_FILE);
        if (flagFile.exists()) {
            stationUnlocked = true;
            return true;
        }
        return false;
    }

    private void stopUnlockPolling() {
        if (unlockPoller != null) {
            unlockPoller.stop();
            unlockPoller = null;
        }
    }

    private void setupUnlockHotkey() {
        if (root == null) return;
        root.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                attachSceneHotkey(newScene);
                Platform.runLater(() -> root.requestFocus());
            }
        });
    }

    private void attachSceneHotkey(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.F10) {
                stationUnlocked = true;
                event.consume();
            }
        });
    }

    public void forceCloseLoading() {
        waitingForUnlock = false;
        stopUnlockPolling();

        Login.hideLoadingBox(loadingBox, () -> {
            Login.showLoginBox(loginBox, usernameField, passwordField, enterLabel);
            if (usernameField != null) usernameField.requestFocus();
        });
    }

    public void resetToLoginScreen() {
        alreadyLoggedIn = false;
        Platform.runLater(() -> {
            introStarted = false;
            waitingForUnlock = false;
            stationUnlocked = false;

            stopUnlockPolling();

            if (usernameField != null) usernameField.clear();
            if (passwordField != null) passwordField.clear();

            if (loadingBox != null) {
                loadingBox.setVisible(false);
                loadingBox.setManaged(false);
                loadingBox.setOpacity(0.0);
            }

            if (loginBox != null) {
                loginBox.setVisible(false);
                loginBox.setManaged(false);
                loginBox.setOpacity(0.0);
            }

            if (logobox != null) {
                logobox.setVisible(true);
                logobox.setManaged(true);
                logobox.setOpacity(1.0);
            }

            if (lblWelcome1 != null) {
                lblWelcome1.setVisible(true);
                lblWelcome1.setManaged(true);
                lblWelcome1.setOpacity(1.0);
            }

            if (lblHint != null) {
                lblHint.setVisible(true);
                lblHint.setManaged(true);
                lblHint.setOpacity(1.0);
            }

            System.out.println("Login screen reset successfully");
        });
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
    void loginAction(MouseEvent event) {
        if (isLoginBlocked()) {
            long remainingSec = (blockedUntil - System.currentTimeMillis()) / 1000;
            showAlert("Too Many Attempts",
                      "Please wait " + remainingSec + " seconds before trying again.");
            return;
        }

        usernameField.setPromptText("Username");
        passwordField.setPromptText("Password");

        if (usernameField.getText().isEmpty()) {
            showAlert("Login Error", "Please enter your username!");
            return;
        }
        if (passwordField.getText().isEmpty()) {
            showAlert("Login Error", "Please enter your password!");
            return;
        }

        final String enteredName = usernameField.getText().trim();
        final String enteredPass = passwordField.getText().trim();

        if (loginBox != null) loginBox.setDisable(true);

        Task<LoginResult> loginTask = new Task<>() {
            private String resolvedName;
            private int userPoints;

            @Override
            protected LoginResult call() throws Exception {
                try (Connection freshCon = new DatabaseConnection().connectDB()) {
                    String sql = "SELECT member_id, member_name, point, password FROM internet_cafe.member WHERE member_name=?";
                    try (PreparedStatement pstLocal = freshCon.prepareStatement(sql)) {
                        pstLocal.setString(1, enteredName);
                        try (ResultSet rsLocal = pstLocal.executeQuery()) {
                            if (!rsLocal.next()) {
                                return new LoginResult(false, "Invalid username or password.");
                            }
                            int memberId = rsLocal.getInt("member_id");
                            resolvedName = rsLocal.getString("member_name");
                            userPoints = rsLocal.getInt("point");
                            String storedPassword = rsLocal.getString("password");

                            boolean authenticated = false;
                            if (storedPassword != null && storedPassword.contains(":") && storedPassword.split(":").length == 3) {
                                authenticated = verifyPassword(enteredPass, storedPassword);
                            } else {
                                if (enteredPass.equals(storedPassword)) {
                                    authenticated = true;
                                    try {
                                        String newHash = hashPassword(enteredPass);
                                        if (newHash != null) {
                                            updatePasswordHash(freshCon, memberId, newHash);
                                        }
                                    } catch (Exception ex) {
                                        ex.printStackTrace(); 
                                    }
                                }
                            }

                            if (!authenticated) {
                                return new LoginResult(false, "Invalid username or password.");
                            }

                            if (userPoints <= 0) {
                                return new LoginResult(false, "Insufficient points (0 points). Please recharge to continue.");
                            }

                            int sessionId = createSession(freshCon, memberId, clientName);
                            client.setSessionId(sessionId);
                            client.resetForNewSession(memberId, resolvedName, userPoints);
                            client.setInitialPoints(userPoints);
                            client.setMemberName(resolvedName);
                        }
                    }
                }

                try {
                    server.loginClient((member_controllers.ClientInterface) client, resolvedName);
                } catch (RemoteException re) {
                    throw re;
                }

                return new LoginResult(true, resolvedName, userPoints);
            }
        };

        loginTask.setOnSucceeded(e -> {
            LoginResult result = loginTask.getValue();
            if (result.success) {
                failedAttempts = 0;
                blockedUntil = 0;

                if (alreadyLoggedIn) {
                    System.err.println("Already logged in – ignoring duplicate login");
                    return;
                }
                alreadyLoggedIn = true;
                try {
                    username = enteredName;
                    switchToDashboard();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    showAlert("Error", "Error loading dashboard: " + ex.getMessage());
                } finally {
                    if (loginBox != null) loginBox.setDisable(false);
                }
            } else {
                failedAttempts++;
                if (failedAttempts >= 5) {
                    blockedUntil = System.currentTimeMillis() + 60_000; 
                    showAlert("Account Locked",
                              "Too many failed attempts. Please wait 1 minute.");
                }
                usernameField.clear();
                passwordField.clear();
                showAlert("Login Failed", result.message);
                if (loginBox != null) loginBox.setDisable(false);
            }
        });

        loginTask.setOnFailed(e -> {
            Throwable ex = loginTask.getException();
            ex.printStackTrace();

            failedAttempts++;
            if (failedAttempts >= 5) {
                blockedUntil = System.currentTimeMillis() + 60_000;
                showAlert("Account Locked",
                          "Too many failed attempts. Please wait 1 minute.");
            }

            showAlert("Login Error", "Login error: " + (ex != null ? ex.getMessage() : "Unknown error"));
            if (loginBox != null) loginBox.setDisable(false);
        });

        new Thread(loginTask).start();
    }

    private void switchToDashboard() {
         deactivate();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/member_view/Home.fxml"));
            Parent dashboardRoot = loader.load();

            HomeController homeController = loader.getController();
            homeController.setServer(server);
            homeController.setClient(client);
            homeController.setDatabaseConnection(con);
        
            if (client != null) {
                client.setController(homeController);
                if (!client.isActive()) {
                    client.startTimer();
                }
                System.out.println("Client controller set, active status: " + client.isActive());
            }
        
            homeController.onReady();

            root.getScene().setRoot(dashboardRoot);
            System.out.println("Switching to Home.fxml");

        } catch (Exception e) {
            e.printStackTrace();
            showToast("Dashboard Error", "Error loading dashboard: " + e.getMessage(), PixelMotion.ToastType.ERROR);
        }
    }
    
    public void cleanup() {
        try {
            if (client != null) {
                UnicastRemoteObject.unexportObject(client, true);
            }
            if (con != null && !con.isClosed()) {
                con.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void deactivate() {
        stopUnlockPolling();
    }
    
    public void updateLoggedInUsername(String newUsername) {
        this.username = newUsername;
        System.out.println("TestController username updated to: " + newUsername);
    }
    
    private int createSession(Connection freshCon, int memberId, String clientName) {
        String insertSQL = "INSERT INTO internet_cafe.session (member_id, computer_id, session_date, total_time, session_total_cost) " +
                        "VALUES (?, ?, CURDATE(), '00:00:00', 0)";
        try (PreparedStatement pst = freshCon.prepareStatement(insertSQL, Statement.RETURN_GENERATED_KEYS)) {
            pst.setInt(1, memberId);
            String numeric = clientName.replaceAll("[^0-9]", "");
            int computerId = Integer.parseInt(numeric);
            pst.setInt(2, computerId);
            pst.executeUpdate();
            ResultSet rs = pst.getGeneratedKeys();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }
    
    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            PixelMotion.toastGlitch(getToastAnchor(), title, message, PixelMotion.ToastType.ERROR);
        });
    }

    public Node getToastAnchor() {
        if (root != null) {
            return root;
        }
        if (loginBox != null) {
            return loginBox;
        }
        return usernameField;
    }

    private void showToast(String title, String message, PixelMotion.ToastType type) {
        Platform.runLater(() -> {
            Node anchor = getToastAnchor();
            if (anchor != null) {
                PixelMotion.toastGlitch(anchor, title, message, type);
            }
        });
    }
    
    private boolean verifyPassword(String enteredPassword, String storedHash) {
        try {
            String[] parts = storedHash.split(":");
            if (parts.length != 3) return false;
            int iterations = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] hash = Base64.getDecoder().decode(parts[2]);

            PBEKeySpec spec = new PBEKeySpec(enteredPassword.toCharArray(), salt, iterations, KEY_LENGTH);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(PBKDF_ALGORITHM);
            byte[] testHash = skf.generateSecret(spec).getEncoded();

            int diff = hash.length ^ testHash.length;
            for (int i = 0; i < hash.length && i < testHash.length; i++) {
                diff |= hash[i] ^ testHash[i];
            }
            return diff == 0;
        } catch (Exception e) {
            return false;
        }
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

    private void updatePasswordHash(Connection freshCon, int memberId, String newHash) {
        String updateSql = "UPDATE internet_cafe.member SET password = ? WHERE member_id = ?";
        try (PreparedStatement pst = freshCon.prepareStatement(updateSql)) {
            pst.setString(1, newHash);
            pst.setInt(2, memberId);
            pst.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private static class LoginResult {
        final boolean success;
        final String message;
        final String name;
        final int points;

        LoginResult(boolean success, String message) {
            this.success = success;
            this.message = message;
            this.name = null;
            this.points = 0;
        }

        LoginResult(boolean success, String name, int points) {
            this.success = success;
            this.message = null;
            this.name = name;
            this.points = points;
        }
    }
    
    public void markLoggedOut() {
        alreadyLoggedIn = false;
    }

    public void showLogoutReasonToast(String reason) {
        Platform.runLater(() -> {
            javafx.animation.PauseTransition delay =
                new javafx.animation.PauseTransition(javafx.util.Duration.millis(800));
            delay.setOnFinished(e ->
                showToast("Session Ended – Points Depleted", reason, PixelMotion.ToastType.WARN)
            );
            delay.play();
        });
    }
}