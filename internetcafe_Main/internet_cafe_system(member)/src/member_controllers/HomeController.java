package member_controllers;

import admin_controllers.ServerInterface;
import database.DatabaseConnection;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import animation.PixelMotion;
import javafx.util.Duration;

public class HomeController implements Initializable, InitializableController {
    private final IntegerProperty currentPointsProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty elapsedSecondsProperty = new SimpleIntegerProperty(0);
    private final IntegerProperty usedPointsProperty = new SimpleIntegerProperty(0);

    @FXML private Label lblName;
    @FXML private Label lblPcInfo;
    @FXML private Label lblSessionTime;
    @FXML private Label lblUserTier;
    @FXML private BorderPane mainBorderPane;
    @FXML private VBox sidebar;
    @FXML private Label myPoint;
    @FXML private Label pointCost;
    @FXML private Button btnCollapse;
    @FXML private Button btnHome;
    @FXML private Button btnGames;
    @FXML private Button btnFood;
    
    @FXML private Button btnProfile;
    @FXML private VBox sidebarBrandBox;
    @FXML private Label lblHomeNav;
    @FXML private Label lblGamesNav;
    @FXML private Label lblFoodNav;
    @FXML private Label lblTimeNav;
    @FXML private Label lblProfileNav;

    private Node currentCenter;
    private Connection databaseConnection;
    private ServerInterface server;
    private ClientImpl client;
    private String username;
    private boolean isLoggingOut = false;
    private boolean timerStarted = false;
    private volatile int currentPoints = 0;
    private Object currentCenterController;
    private String memberTier = "normal";
    private final IntegerProperty totalLifetimeSpentProperty = new SimpleIntegerProperty(0);
    private Timeline syncTimer;
    private Timeline pointsMonitor;
    private List<Button> navigationButtons;
    private Button activeNavButton;
    private String currentPage = "";
    private boolean navigationInProgress = false;
    private boolean sidebarCollapsed = false;
    private List<Node> collapsibleSidebarNodes;
    private final Map<String, CachedPage> pageCache = new HashMap<>();
    private ProfilePageController profilePageController;

    private static final Duration FADE_OUT_DURATION = Duration.millis(200);
    private static final Duration FADE_IN_DURATION = Duration.millis(250);
    private static final Duration BUTTON_SCALE_DURATION = Duration.millis(120);
    private static final Duration SIDEBAR_ANIMATION_DURATION = Duration.millis(240);
    private static final Duration LOGOUT_TOAST_DELAY = Duration.millis(2100);
    private static final double PAGE_SLIDE_DISTANCE = 36.0;
    private static final double SIDEBAR_EXPANDED_WIDTH = 272.0;
    private static final double SIDEBAR_COLLAPSED_WIDTH = 92.0;

    private static final class CachedPage {
        private final Node content;
        private final Object controller;

        private CachedPage(Node content, Object controller) {
            this.content = content;
            this.controller = controller;
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
         myPoint.textProperty().bind(currentPointsProperty.asString());
        pointCost.textProperty().bind(Bindings.createStringBinding(() -> {
            int used = usedPointsProperty.get();
            return used + " P";
        }, usedPointsProperty));
    
        if (lblPcInfo != null) lblPcInfo.setText(TestController.clientName);
        if (lblSessionTime != null) lblSessionTime.setText("00:00:00");

        initializeSidebarButtons();
        initializeSidebarCollapseNodes();

        try {
            setCenterContent("Homepage.fxml", btnHome, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
          System.out.println("=== HomeController Initialized (Points Only) ===");
    }

    public void updatePointsDisplay(int points) {
        Platform.runLater(() -> {
            currentPointsProperty.set(Math.max(0, points));
            currentPoints = points;

            if (currentCenterController instanceof HomepageController) {
                ((HomepageController) currentCenterController).updatePoints(points);
            }
        });
    }

    public void updateUserInfo(String name, String tier) {
        Platform.runLater(() -> {
            if (lblName != null && name != null && !name.isEmpty()) {
                lblName.setText(name);
            }
            if (lblUserTier != null && tier != null && !tier.isEmpty()) {
                lblUserTier.setText("Tier." + tier);
            }

            if (currentCenterController instanceof HomepageController) {
                ((HomepageController) currentCenterController).updateMemberInfo(name, tier);
            }
        });
    }

    public IntegerProperty currentPointsProperty() {
        return currentPointsProperty;
    }

    public void onReady() {
        if (client == null) {
            System.out.println("Warning: No RMI Client object received");
            if (databaseConnection == null) {
                initializeDatabaseConnection();
            }
            return;
        }

        client.setController(this);
        this.username = client.getUsername();

        String tier = client.fetchTierFromDatabase();
        this.memberTier = tier;
        updateUserInfo(client.getCurrentMemberName(), tier);

        if (databaseConnection == null) {
            initializeDatabaseConnection();
        }

        int memberId = getMemberIdFromUsername(this.username);
        if (memberId != -1) {
            fetchTotalLifetimeSpent(memberId);
        }

        currentPoints = client.getCurrentPoints();
        if (currentPoints <= 0) {
            showAlertAndLogout("Insufficient Points", "You have 0 points. Please recharge to continue.");
            return;
        }

        updatePointsDisplay(currentPoints);
        UserSession.setCurrentUsername(this.username);

        if (!timerStarted) {
            client.startTimer();
            timerStarted = true;
        }

        startPointsMonitoring();
        startSyncTimer();
    }

    private void startSyncTimer() {
        if (syncTimer != null) {
            syncTimer.stop();
        }

        syncTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            if (client == null || !client.isActive() || server == null || isLoggingOut) {
                return;
            }

            try {
                int serverElapsed = server.getElapsedTimeForPC(client.getClientName());
                client.updateTimer(serverElapsed);

                int currentPointsCheck = client.getCurrentPoints();
                if (currentPointsCheck <= 0) {
                    showAlertAndLogout("Session Ending", "Your points have been depleted. The session will now end.");
                }
            } catch (RemoteException ex) {
                System.err.println("Sync timer error: " + ex.getMessage());
            }
        }));
        syncTimer.setCycleCount(Animation.INDEFINITE);
        syncTimer.play();
    }

    private void startPointsMonitoring() {
        if (pointsMonitor != null) {
            pointsMonitor.stop();
        }

        pointsMonitor = new Timeline(new KeyFrame(Duration.seconds(10), e -> {
            if (client == null || !client.isActive() || isLoggingOut) {
                return;
            }

            int currentPointsCheck = client.getCurrentPoints();
            int elapsedSeconds = client.getElapsedSeconds();
            int secondsPerPoint = Math.max(1, client.getSecondsPerPoint());
            int pointsUsed = elapsedSeconds / secondsPerPoint;
            int remaining = currentPointsCheck - pointsUsed;

            if (remaining <= 0 || currentPointsCheck <= 0) {
                showAlertAndLogout(
                    "Session Ending",
                    "Your points have been depleted.\n\n" +
                    "Current points: " + currentPointsCheck + "\n" +
                    "Points used this session: " + pointsUsed + "\n\n" +
                    "The session will now end."
                );
            }
        }));
        pointsMonitor.setCycleCount(Animation.INDEFINITE);
        pointsMonitor.play();
    }

    @FXML
    void checkPointsStatus(ActionEvent event) {
        if (client == null) {
            return;
        }

        int currentPointsValue = client.getCurrentPoints();
        int elapsedSeconds = client.getElapsedSeconds();
        int secondsPerPoint = Math.max(1, client.getSecondsPerPoint());
        int pointsUsed = elapsedSeconds / secondsPerPoint;
        int remaining = currentPointsValue - pointsUsed;

        String message = String.format(
            "Current points in database: %d%n" +
            "Points used this session: %d%n" +
            "Remaining points: %d%n" +
            "Elapsed time: %d minutes%n" +
            "Rate: 1 point per %d minutes",
            currentPointsValue,
            pointsUsed,
            remaining,
            elapsedSeconds / 60,
            secondsPerPoint / 60
        );

        showToast("Points Status", message, PixelMotion.ToastType.INFO);

        if (remaining <= 0 || currentPointsValue <= 0) {
            showAlertAndLogout("Session Ending", "Your points have been depleted. The session will now end.");
        }
    }

    private void showAlertAndLogout(String title, String message) {
        if (isLoggingOut) {
            return;
        }

        isLoggingOut = true;
        stopTimers();

        Platform.runLater(() -> {
            showToast(title, message, PixelMotion.ToastType.WARN);

            PauseTransition delay = new PauseTransition(LOGOUT_TOAST_DELAY);
            delay.setOnFinished(event -> {
                if (client != null) {
                    try {
                        client.forceLogout();
                    } catch (RemoteException ex) {
                        ex.printStackTrace();
                        navigateToTestView();
                    }
                } else {
                    navigateToTestView();
                }
            });
            delay.play();
        });
    }

    private void initializeDatabaseConnection() {
        try {
            if (databaseConnection == null || databaseConnection.isClosed()) {
                DatabaseConnection db = new DatabaseConnection();
                databaseConnection = db.connectDB();
            }
        } catch (SQLException ex) {
            System.err.println("Connection Failed: " + ex.getMessage());
            ex.printStackTrace();
        } catch (Exception ex) {
            System.err.println("Unexpected error: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public Connection getDatabaseConnection() {
        return databaseConnection;
    }

    public boolean isDatabaseConnected() {
        try {
            return databaseConnection != null && !databaseConnection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
    }

    @Override
    public void setClient(ClientImpl client) {
        this.client = client;
        if (client != null) {
            client.setController(this);
            this.username = client.getUsername();
            currentPoints = client.getCurrentPoints();
            updatePointsDisplay(currentPoints);

            if (!timerStarted) {
                client.startTimer();
                timerStarted = true;
            }
        }
    }

    @Override
    public void setDatabaseConnection(Connection con) {
        this.databaseConnection = con;
    }

    @FXML
    void openGames(ActionEvent event) throws IOException {
        setCenterContent("Gamepage.fxml", btnGames, true);
    }

    @FXML
    void openFood(ActionEvent event) throws IOException {
        setCenterContent("foodorder.fxml", btnFood, true);
    }

    @FXML
    void openHome(ActionEvent event) throws IOException {
        setCenterContent("Homepage.fxml", btnHome, true);
    }

    @FXML
    void openProfile(ActionEvent event) throws IOException {
        setCenterContent("ProfilePage.fxml", btnProfile, true);
    }

    

    @FXML
    void handleLogout(ActionEvent event) {
        if (isLoggingOut) {
            return;
        }

        isLoggingOut = true;
        stopTimers();

        if (client != null) {
            client.manualLogout();
        } else {
            navigateToTestView();
        }
    }

    @FXML
    void toggleSidebar(ActionEvent event) {
        animateSidebarCollapse(!sidebarCollapsed);
    }

    private void navigateToTestView() {
        Platform.runLater(() -> {
            try {
                if (MemberDashboard.stage == null) {
                    return;
                }

                FXMLLoader loader = new FXMLLoader(getClass().getResource("/member_view/Test.fxml"));
                Parent root = loader.load();
                TestController testController = loader.getController();

                if (client != null) {
                    testController.setClient(client);
                }
                testController.resetToLoginScreen();

                MemberDashboard.stage.setScene(new Scene(root));
                MemberDashboard.stage.setTitle("Internet Cafe - Login");
                MemberDashboard.stage.setFullScreen(true);
                MemberDashboard.stage.show();

                closeDatabaseConnection();
            } catch (IOException e) {
                System.err.println("Error loading test view: " + e.getMessage());
                e.printStackTrace();
                isLoggingOut = false;
            }
        });
    }

    private void closeDatabaseConnection() {
        try {
            if (databaseConnection != null && !databaseConnection.isClosed()) {
                databaseConnection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        databaseConnection = null;
    }

    private void stopTimers() {
        if (syncTimer != null) {
            syncTimer.stop();
            syncTimer = null;
        }
        if (pointsMonitor != null) {
            pointsMonitor.stop();
            pointsMonitor = null;
        }
    }

    void setCenterContent(String fxmlFile) throws IOException {
        setCenterContent(fxmlFile, null, true);
    }

    void setCenterContent(String fxmlFile, Button targetButton, boolean animate) throws IOException {
        if (navigationInProgress || fxmlFile.equals(currentPage)) {
            if (targetButton != null) {
                setActiveNavButton(targetButton);
            }
            return;
        }

        CachedPage page = getOrCreatePage(fxmlFile);
        Node content = page.content;
        currentCenterController = page.controller;
        prepareController(page.controller);

        currentPage = fxmlFile;
        if (targetButton != null) {
            setActiveNavButton(targetButton);
        }

        if (currentCenter == null) {
            currentCenter = content;
            if (page.controller instanceof InitializableController ic) ic.preHideForEntrance();
            mainBorderPane.setCenter(content);
            prepareIncomingNode(content);
            if (page.controller instanceof InitializableController ic) ic.replayEntranceAnimations();
            navigationInProgress = false;
            return;
        }

        Node previous = currentCenter;
        currentCenter = content;
        if (!animate) {
            if (page.controller instanceof InitializableController ic) ic.preHideForEntrance();
            mainBorderPane.setCenter(content);
            prepareIncomingNode(content);
            if (page.controller instanceof InitializableController ic) ic.replayEntranceAnimations();
            navigationInProgress = false;
            return;
        }

        animateCenterTransition(previous, content);
    }

    private void animateCenterTransition(Node currentNode, Node nextNode) {
        navigationInProgress = true;

        FadeTransition fadeOut = new FadeTransition(FADE_OUT_DURATION, currentNode);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeOut.setOnFinished(event -> {
            prepareIncomingNode(nextNode);
            nextNode.setOpacity(0.0);
            // Hide cards BEFORE the page slides in so they are never visible during the transition
            if (currentCenterController instanceof InitializableController ic) ic.preHideForEntrance();
            mainBorderPane.setCenter(nextNode);

            FadeTransition fadeIn = new FadeTransition(FADE_IN_DURATION, nextNode);
            fadeIn.setFromValue(0.0);
            fadeIn.setToValue(1.0);

            TranslateTransition slideIn = new TranslateTransition(FADE_IN_DURATION, nextNode);
            slideIn.setFromX(PAGE_SLIDE_DISTANCE);
            slideIn.setToX(0.0);

            ParallelTransition reveal = new ParallelTransition(fadeIn, slideIn);
            reveal.setOnFinished(finish -> {
                if (currentCenterController instanceof InitializableController ic) ic.replayEntranceAnimations();
                navigationInProgress = false;
            });
            reveal.play();
        });
        fadeOut.play();
    }

    private void prepareIncomingNode(Node node) {
        node.setOpacity(1.0);
        node.setTranslateX(0.0);
    }

    private void initializeSidebarButtons() {
        navigationButtons = Arrays.asList(btnHome, btnGames, btnFood, btnProfile);
        for (Button button : navigationButtons) {
            if (button == null) {
                continue;
            }
            if (!button.getStyleClass().contains("nav-button-animated")) {
                button.getStyleClass().add("nav-button-animated");
            }
            installButtonMotion(button);
        }
    }

    private void initializeSidebarCollapseNodes() {
        collapsibleSidebarNodes = Arrays.asList(
            sidebarBrandBox,
            lblHomeNav,
            lblGamesNav,
            lblFoodNav,
            lblTimeNav,
            lblProfileNav
        );
    }

    private void installButtonMotion(Button button) {
        button.setOnMouseEntered(event -> animateButtonScale(button, 1.03));
        button.setOnMouseExited(event -> animateButtonScale(button, isActiveButton(button) ? 1.02 : 1.0));
        button.setOnMousePressed(event -> animateButtonScale(button, 0.97));
        button.setOnMouseReleased(event ->
            animateButtonScale(button, button.isHover() ? 1.03 : (isActiveButton(button) ? 1.02 : 1.0))
        );
    }

    private void animateButtonScale(Button button, double scale) {
        ScaleTransition transition = new ScaleTransition(BUTTON_SCALE_DURATION, button);
        transition.setToX(scale);
        transition.setToY(scale);
        transition.play();
    }

    private void setActiveNavButton(Button selectedButton) {
        activeNavButton = selectedButton;
        if (navigationButtons == null) {
            return;
        }

        for (Button button : navigationButtons) {
            if (button == null) {
                continue;
            }

            if (button == selectedButton) {
                if (!button.getStyleClass().contains("active")) {
                    button.getStyleClass().add("active");
                }
                button.setScaleX(1.02);
                button.setScaleY(1.02);
            } else {
                button.getStyleClass().remove("active");
                button.setScaleX(1.0);
                button.setScaleY(1.0);
            }
        }
    }

    private boolean isActiveButton(Button button) {
        return button != null && button == activeNavButton;
    }

    private void animateSidebarCollapse(boolean collapse) {
        if (sidebar == null) {
            return;
        }

        sidebarCollapsed = collapse;
        double targetWidth = collapse ? SIDEBAR_COLLAPSED_WIDTH : SIDEBAR_EXPANDED_WIDTH;
        ParallelTransition animation = new ParallelTransition();

        if (collapse) {
            applySidebarCollapsedClass(true);
        } else {
            for (Node node : collapsibleSidebarNodes) {
                if (node != null) {
                    node.setManaged(true);
                    node.setVisible(true);
                }
            }
            applySidebarCollapsedClass(false);
        }

        Timeline widthAnimation = new Timeline(
            new KeyFrame(
                SIDEBAR_ANIMATION_DURATION,
                new KeyValue(sidebar.prefWidthProperty(), targetWidth, Interpolator.EASE_BOTH),
                new KeyValue(sidebar.minWidthProperty(), targetWidth, Interpolator.EASE_BOTH),
                new KeyValue(sidebar.maxWidthProperty(), targetWidth, Interpolator.EASE_BOTH)
            )
        );

        animation.getChildren().add(widthAnimation);
        animation.getChildren().add(createSidebarFadeTransition(!collapse));
        animation.setOnFinished(event -> {
            if (collapse) {
                for (Node node : collapsibleSidebarNodes) {
                    if (node != null) {
                        node.setManaged(false);
                        node.setVisible(false);
                    }
                }
            } else {
                for (Node node : collapsibleSidebarNodes) {
                    if (node != null) {
                        node.setOpacity(1.0);
                    }
                }
            }
        });
        animation.play();
    }

    private ParallelTransition createSidebarFadeTransition(boolean fadeIn) {
        ParallelTransition transition = new ParallelTransition();
        for (Node node : collapsibleSidebarNodes) {
            if (node == null) {
                continue;
            }

            FadeTransition fade = new FadeTransition(Duration.millis(160), node);
            fade.setFromValue(fadeIn ? 0.0 : 1.0);
            fade.setToValue(fadeIn ? 1.0 : 0.0);
            transition.getChildren().add(fade);
        }
        return transition;
    }

    private void applySidebarCollapsedClass(boolean collapsed) {
        if (collapsed) {
            if (!sidebar.getStyleClass().contains("collapsed")) {
                sidebar.getStyleClass().add("collapsed");
            }
        } else {
            sidebar.getStyleClass().remove("collapsed");
        }
    }

    public void updateElapsedTimeDisplay(int elapsedSeconds) {
        Platform.runLater(() -> {
            elapsedSecondsProperty.set(elapsedSeconds);
            int secondsPerPoint = (client != null) ? Math.max(1, client.getSecondsPerPoint()) : 360;
            int used = elapsedSeconds / secondsPerPoint;
            usedPointsProperty.set(used);

            int hours = elapsedSeconds / 3600;
            int minutes = (elapsedSeconds % 3600) / 60;
            int seconds = elapsedSeconds % 60;

            if (lblSessionTime != null) {
                lblSessionTime.setText(String.format("%02d:%02d:%02d", hours, minutes, seconds));
            }

            if (currentCenterController instanceof HomepageController) {
                ((HomepageController) currentCenterController).updateElapsedTime(elapsedSeconds);
            }
        });
    }

    private int secondsToPoints(int seconds) {
        int secondsPerPoint = (client != null) ? Math.max(1, client.getSecondsPerPoint()) : 360;
        return (int) Math.ceil(seconds / (double) secondsPerPoint);
    }

    public void showPointCostForSeconds(int seconds) {
        int requiredPoints = secondsToPoints(seconds);
        Platform.runLater(() -> {
            if (pointCost != null) {
                pointCost.setText(String.format("%d point%s required", requiredPoints, requiredPoints == 1 ? "" : "s"));
            }
        });
    }

    public void addPoints(int pointsToAdd, boolean persistToDb) {
        if (client != null) {
            client.addPoints(pointsToAdd, persistToDb);
        }
    }

    public void deductPoints(int pointsToDeduct, boolean persistToDb) {
        if (client != null) {
            client.deductPoints(pointsToDeduct, persistToDb);
        }
        int memberId = getMemberIdFromUsername(this.username);
        if (memberId != -1) {
            fetchTotalLifetimeSpent(memberId);
        }
    }

    public boolean chargePointsForSeconds(int seconds) {
        return client != null && client.chargePointsForSeconds(seconds);
    }

    public void showPointsDeductedNotification(int pointsDeducted, int remainingPoints, int actualMinutesSpent) {
        Platform.runLater(() -> {
            showToast("Session Summary", String.format(
                "Time used: %d minutes%nPoints deducted: %d%nRemaining points: %d%nRate: 1 point per %d minutes",
                actualMinutesSpent,
                pointsDeducted,
                remainingPoints,
                (client != null ? Math.max(1, client.getSecondsPerPoint()) : 360) / 60
            ), PixelMotion.ToastType.INFO);
        });
    }

    public void cleanup() {
        stopTimers();
        try {
            if (client != null) {
                client.setController(null);
            }
            for (CachedPage page : pageCache.values()) {
                if (page.controller instanceof FoodorderController foodorderController) {
                    foodorderController.cleanup();
                }
            }
            if (profilePageController != null) {
                profilePageController.cleanup();
                profilePageController = null;
            }
            currentCenter = null;
            currentCenterController = null;
            pageCache.clear();
            if (mainBorderPane != null) {
                mainBorderPane.setCenter(null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
}

    public int getMinutesSpent(int elapsedSeconds) {
        return elapsedSeconds / 60;
    }

    public Object getCurrentCenterController() {
        return currentCenterController;
    }

    public int getElapsedSeconds() {
        return elapsedSecondsProperty.get();
    }

    public int getUsedPoints() {
        return usedPointsProperty.get();
    }

    public String getMemberName() {
        return lblName != null ? lblName.getText() : "";
    }

    public String getMemberTier() {
        return memberTier;
    }

    public int getSecondsPerPoint() {
        return (client != null) ? Math.max(1, client.getSecondsPerPoint()) : 360;
    }

    public int getTotalLifetimeSpent() {
        return totalLifetimeSpentProperty.get();
    }

    private void fetchTotalLifetimeSpent(int memberId) {
        if (databaseConnection == null) {
            return;
        }

        String sql = "SELECT COALESCE(SUM(session_total_cost), 0) AS total_spent " +
                     "FROM internet_cafe.session WHERE member_id = ?";
        try (PreparedStatement stmt = databaseConnection.prepareStatement(sql)) {
            stmt.setInt(1, memberId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int total = rs.getInt("total_spent");
                Platform.runLater(() -> {
                    totalLifetimeSpentProperty.set(total);
                    if (currentCenterController instanceof HomepageController) {
                        ((HomepageController) currentCenterController).updateTotalSpent(total);
                    }
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private int getMemberIdFromUsername(String memberUsername) {
        if (databaseConnection == null || memberUsername == null || memberUsername.isEmpty()) {
            return -1;
        }

        String sql = "SELECT member_id FROM internet_cafe.member WHERE member_name = ?";
        try (PreparedStatement stmt = databaseConnection.prepareStatement(sql)) {
            stmt.setString(1, memberUsername);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("member_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public void refreshTotalLifetimeSpent() {
        if (username == null || username.isEmpty()) {
            return;
        }

        new Thread(() -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {
            }
            Platform.runLater(() -> {
                int memberId = getMemberIdFromUsername(username);
                if (memberId != -1) {
                    fetchTotalLifetimeSpent(memberId);
                }
            });
        }).start();
    }

    public ClientImpl getClient() {
        return client;
    }

    public BorderPane getMainBorderPane() {
        return mainBorderPane;
    }

    public Stage getStage() {
        if (mainBorderPane != null && mainBorderPane.getScene() != null) {
            return (Stage) mainBorderPane.getScene().getWindow();
        }
        return MemberDashboard.stage;
    }

    private CachedPage getOrCreatePage(String fxmlFile) throws IOException {
        CachedPage cached = pageCache.get(fxmlFile);
        if (cached != null) {
            return cached;
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/member_view/" + fxmlFile));
        Node content = loader.load();
        CachedPage page = new CachedPage(content, loader.getController());
        pageCache.put(fxmlFile, page);
        return page;
    }

    private void prepareController(Object controller) {
        if (controller instanceof InitializableController initController) {
            initController.setDatabaseConnection(databaseConnection);
            if (server != null) {
                initController.setServer(server);
            }
            if (client != null) {
                initController.setClient(client);
            }
        }

        if (controller instanceof HomepageController homepageController) {
            homepageController.setHomeController(this);
            this.profilePageController = profilePageController;
            homepageController.updatePoints(currentPointsProperty.get());
            homepageController.updateElapsedTime(elapsedSecondsProperty.get());
            homepageController.updateMemberInfo(getMemberName(), getMemberTier());
        }

        if (controller instanceof ProfilePageController profilePageController) {
            profilePageController.setHomeController(this);
        }
    }

    private void showToast(String title, String message, PixelMotion.ToastType type) {
        Node anchor = mainBorderPane != null ? mainBorderPane : btnHome;
        if (anchor == null) {
            return;
        }
        PixelMotion.toastGlitch(anchor, title, message, type);
    }
}
