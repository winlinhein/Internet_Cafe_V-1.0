package member_controllers;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import admin_controllers.ServerInterface;
import animation.PixelMotion;
import database.DatabaseConnection;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class ClientImpl implements ClientInterface, ClientCallback {
    private final String clientName;
    private int memberId;
    private String memberName;
    private final ServerInterface server;
    private transient HomeController controller;
    private transient TestController testController;
    private boolean isActive = false;
    private int initialPoints;
    private int currentPoints;
    private int elapsedSeconds = 0;
    private int secondsPerPoint = 360;
    private String currentTier;
    private boolean pointsUpdatedRemotely = false;
    private int sessionId = -1;
    private int displayElapsedOffset = 0;
    private int lastLoggedPointsUsed = 0;
    private volatile boolean logoutInProgress = false;

    // Suppress duplicate "Points Updated" toast when an order is placed
    private volatile boolean suppressPointsUpdateToast = false;

    // Flag to indicate the admin intentionally ended the session
    private volatile boolean adminTerminated = false;

    // Reference to any pending depletion-triggered PauseTransition (to cancel it if admin steps in)
    private transient PauseTransition pendingDepletionTransition = null;

    public ClientImpl(String clientName, String memberName, ServerInterface server) throws RemoteException {
        super();
        this.clientName = clientName;
        this.memberName = memberName;
        this.memberId = 0;
        this.server = server;
        this.initialPoints = 0;
        this.currentPoints = 0;
        this.elapsedSeconds = 0;
        this.currentTier = "";
    }

    public void setSuppressPointsUpdateToast(boolean suppress) {
        this.suppressPointsUpdateToast = suppress;
    }

    public String getUsername() {
        return memberName;
    }

    public void setUsername(String username) {
        this.memberName = username;
    }

    public void setTestController(TestController controller) {
        this.testController = controller;
        System.out.println("TestController set in ClientImpl");
    }

    public void setInitialPoints(int points) {
        this.initialPoints = points;
        this.currentPoints = points;
        System.out.println("Initial points set to: " + points);
        if (controller != null) {
            Platform.runLater(() -> controller.updatePointsDisplay(points));
        }
    }

    public int getMemberId() {
        return memberId;
    }

    public void setMemberId(int id) {
        this.memberId = id;
    }

    public int getSessionId() {
        return sessionId;
    }

    public void setSessionId(int sessionId) {
        this.sessionId = sessionId;
    }

    public void setMemberName(String name) {
        this.memberName = name;
        System.out.println("Member name set to: " + name);
    }

    public String getCurrentMemberName() {
        return memberName;
    }

    public void setCurrentTier(String tier) {
        this.currentTier = tier;
        System.out.println("Current tier set to: " + tier);
    }

    public int getElapsedSeconds() {
        return elapsedSeconds;
    }

    public int getCurrentPoints() {
        return currentPoints;
    }

    public String getCurrentTier() {
        return currentTier;
    }

    public int getSecondsPerPoint() {
        return secondsPerPoint;
    }

    @Override
    public String getClientName() throws RemoteException {
        return clientName;
    }

    @Override
    public String getMemberName() throws RemoteException {
        return memberName;
    }

    public String fetchTierFromDatabase() {
        String tier = "";
        try (Connection con = new DatabaseConnection().connectDB();
             PreparedStatement pst = con.prepareStatement(
                     "SELECT t.member_type_name, t.points_consumption_rate FROM internet_cafe.member m " +
                     "JOIN internet_cafe.member_type t ON m.member_type_id = t.member_type_id " +
                     "WHERE m.member_id = ?")) {
            pst.setInt(1, memberId);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                tier = rs.getString("member_type_name");
                this.secondsPerPoint = rs.getInt("points_consumption_rate");
                System.out.println("Fetched tier: " + tier + " with consumption rate: " + secondsPerPoint + " seconds/point (" + (secondsPerPoint/60) + " min/point)");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tier;
    }

    public void setRateBasedOnTier(String tier) {
        String sql = "SELECT points_consumption_rate FROM internet_cafe.member_type " +
                     "WHERE member_type_name = ?";
        try (Connection con = new DatabaseConnection().connectDB();
             PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, tier);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                this.secondsPerPoint = rs.getInt("points_consumption_rate");
                System.out.println("Rate set for " + tier + ": 1 point per " + secondsPerPoint +
                                 " seconds (" + (secondsPerPoint/60) + " minutes)");
            } else {
                this.secondsPerPoint = 360;
                System.out.println("Tier not found, using default: 360 seconds/point");
            }
        } catch (SQLException e) {
            System.err.println("Error fetching rate for tier " + tier + ": " + e.getMessage());
            this.secondsPerPoint = 360;
        }
    }

    @Override
    public void showLoginScreen() throws RemoteException {
        Platform.runLater(() -> {
            if (testController != null) {
                testController.forceCloseLoading();
                System.out.println("Login screen shown loading skipped");
            } else {
                System.err.println("TestController reference is null");
            }
        });
    }

    @Override
    public void updateTimer(int elapsed) throws RemoteException {
        System.out.println("ClientImpl.updateTimer() CALLED with elapsed=" + elapsed);

        // If a logout is already underway, stop processing
        if (logoutInProgress || adminTerminated) {
    System.out.println("Timer update skipped because logout is in progress or admin terminated");
    return;
        }

        int oldElapsedSeconds = this.elapsedSeconds;
        this.elapsedSeconds = Math.max(0, elapsed);

        int currentPointsUsed = this.elapsedSeconds / secondsPerPoint;
        int pointsUsedInTick = currentPointsUsed - lastLoggedPointsUsed;

        if (pointsUsedInTick > 0 && memberId > 0) {
            System.out.println("POINT DEDUCTION: " + pointsUsedInTick + " point(s) used at " + this.elapsedSeconds + " seconds");
            deductPointsFromDatabase(pointsUsedInTick);
            lastLoggedPointsUsed = currentPointsUsed;
        }

        int displayElapsed = Math.max(0, elapsedSeconds - displayElapsedOffset);
        int pointsUsed = displayElapsed / secondsPerPoint;

        int currentPointBalance = this.currentPoints;
        int remainingPoints = Math.max(0, currentPointBalance);

        final int finalCurrentPointBalance = currentPointBalance;
        final int finalElapsedSeconds = this.elapsedSeconds;

        System.out.println("=== POINTS CALCULATION ===");
        System.out.println("Elapsed seconds: " + elapsedSeconds);
        System.out.println("Seconds per point: " + secondsPerPoint);
        System.out.println("Total points used: " + currentPointsUsed);
        System.out.println("Current cached balance: " + currentPointBalance);
        System.out.println("Remaining points: " + remainingPoints);
        System.out.println("Is Active: " + isActive);

        if (controller != null) {
            Platform.runLater(() -> {
                controller.updateElapsedTimeDisplay(finalElapsedSeconds);
                controller.updatePointsDisplay(finalCurrentPointBalance);
            });
        }

        // Natural point depletion – only if admin has not already terminated
        if (currentPointBalance <= 0 && isActive && !adminTerminated && !logoutInProgress) {
            System.out.println("ZERO POINTS DETECTED. Forcing immediate logout...");
            logoutInProgress = true;          // block all other logout attempts
            isActive = false;

            TestController.pendingLogoutMessage = String.format(
                "Your session was ended automatically because your points reached zero.\n" +
                "Time used: %d min | Rate: 1 point per %d min\n\nPlease recharge to start a new session.",
                finalElapsedSeconds / 60, secondsPerPoint / 60
            );

            // Cancel any previous depletion transition (safety)
            if (pendingDepletionTransition != null) {
                pendingDepletionTransition.stop();
            }

            Platform.runLater(() -> {
                // Prevent showing the depletion toast if admin already took over
                if (adminTerminated) {
                    return;
                }

                showToast(
                    "Session Ended",
                    String.format(
                        "Time used: %d minutes\nRate: 1 point per %d minutes\n\nYour points have been consumed.\nThe session will now end.",
                        finalElapsedSeconds / 60,
                        secondsPerPoint / 60
                    ),
                    PixelMotion.ToastType.WARN
                );

                PauseTransition delay = new PauseTransition(javafx.util.Duration.millis(2100));
                pendingDepletionTransition = delay;
                delay.setOnFinished(event -> {
                    pendingDepletionTransition = null;
                    try {
                        forceLogout();
                    } catch (RemoteException ex) {
                        System.err.println("Error during force logout: " + ex.getMessage());
                    }
                });
                delay.play();
            });
        }
    }

    public void forceLogout() throws RemoteException {
        if (logoutInProgress) {
            System.out.println("Force logout already in progress");
            return;
        }

        System.out.println("=== FORCE LOGOUT INITIATED ===");
        System.out.println("Member: " + memberName);
        System.out.println("Member ID: " + memberId);
        System.out.println("Elapsed seconds: " + elapsedSeconds);

        logoutInProgress = true;
        isActive = false;

        // Cancel any pending depletion-triggered transition
        if (pendingDepletionTransition != null) {
            pendingDepletionTransition.stop();
            pendingDepletionTransition = null;
        }

        // If no specific pending message, decide based on adminTerminated flag
        if (TestController.pendingLogoutMessage == null) {
            if (adminTerminated) {
                TestController.pendingLogoutMessage =
                    "Your session was ended by the administrator.\n\n" +
                    "Please contact staff if this was unexpected.";
                Platform.runLater(() -> showToast(
                    "Logged Out by Admin",
                    "An administrator has ended your session.\nPlease contact staff if this was unexpected.",
                    PixelMotion.ToastType.WARN
                ));
            } else {
                TestController.pendingLogoutMessage =
                    "Your session was ended.\n\nPlease contact staff if this was unexpected.";
                Platform.runLater(() -> showToast(
                    "Session Ended",
                    "Your session has been ended.\nPlease contact staff if this was unexpected.",
                    PixelMotion.ToastType.WARN
                ));
            }
        }

        new Thread(() -> {
            logSessionOnly();
            Platform.runLater(() -> {
                try {
                    Logout();
                } catch (Exception e) {
                    System.err.println("Error during logout: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }).start();
    }

    @Override
    public void updatePoints(int newPoints) throws RemoteException {
        System.out.println("RMI: updatePoints() called from server with: " + newPoints);

        if (!isActive || logoutInProgress) {
            System.out.println("Session not active or logout in progress, ignoring point update");
            return;
        }

        this.currentPoints = newPoints;
        this.initialPoints = newPoints;
        this.pointsUpdatedRemotely = true;

        final int finalNewPoints = newPoints;

        // Server set points to zero – this is an admin‑initiated logout
        if (newPoints <= 0) {
            System.out.println("Server set points to zero, treating as admin logout.");
            logoutInProgress = true;      // block timer/other checks immediately
            adminTerminated = true;
            isActive = false;

            // Cancel any pending depletion transition
            if (pendingDepletionTransition != null) {
                pendingDepletionTransition.stop();
                pendingDepletionTransition = null;
            }

            TestController.pendingLogoutMessage =
                "Your session was ended by the administrator.\n\n" +
                "Please contact staff if this was unexpected.";

            Platform.runLater(() -> {
                showToast(
                    "Logged Out by Admin",
                    "An administrator has ended your session.\nPlease contact staff if this was unexpected.",
                    PixelMotion.ToastType.WARN
                );

                PauseTransition delay = new PauseTransition(javafx.util.Duration.millis(2100));
                delay.setOnFinished(event -> {
                    try {
                        forceLogout();
                    } catch (RemoteException ex) {
                        System.err.println("Error in force logout: " + ex.getMessage());
                    }
                });
                delay.play();
            });
            return;
        }

        if (controller != null) {
            Platform.runLater(() -> {
                controller.updatePointsDisplay(finalNewPoints);
                controller.refreshTotalLifetimeSpent();
                System.out.println("UI updated with new points: " + finalNewPoints);
            });
        } else {
            System.out.println("Warning: Controller is null, cannot update points");
        }

        // Only show the toast if NOT suppressed (e.g., during an order)
        if (!suppressPointsUpdateToast) {
            Platform.runLater(() -> showToast(
                "Points Updated",
                "Your point balance has been updated to " + finalNewPoints + " point" +
                (finalNewPoints == 1 ? "" : "s") + " by the administrator.",
                PixelMotion.ToastType.INFO
            ));
        }
    }

    @Override
    public void updateTier(String newTier) throws RemoteException {
        System.out.println("RMI: updateTier() called from server with: " + newTier);

        if (!isActive || logoutInProgress) {
            System.out.println("Session not active or logout in progress, ignoring tier update");
            return;
        }

        this.currentTier = newTier;
        setRateBasedOnTier(newTier);

        if (controller != null) {
            Platform.runLater(() -> {
                controller.updateUserInfo(memberName, newTier);
                System.out.println("UI updated with new tier: " + newTier);
            });
        } else {
            System.out.println("Warning: Controller is null, cannot update tier");
        }

        Platform.runLater(() -> showToast(
            "Membership Tier Updated",
            "Your membership tier has been changed to \"" + newTier + "\".\n" +
            "Your new point consumption rate has been applied.",
            PixelMotion.ToastType.INFO
        ));
    }

    @Override
    public void updateName(String newName) throws RemoteException {
        String oldName = this.memberName;
        System.out.println("RMI: updateName() called from server: " + oldName + " -> " + newName);

        if (!isActive || logoutInProgress) {
            System.out.println("Session not active or logout in progress, ignoring name update");
            return;
        }

        this.memberName = newName;

        if (controller != null) {
            Platform.runLater(() -> {
                controller.updateUserInfo(newName, currentTier);
                showToast("Username Changed", "You are now logged in as: " + newName, PixelMotion.ToastType.INFO);
                System.out.println("UI updated with new name: " + newName);
            });
        }

        if (testController != null) {
            Platform.runLater(() -> testController.updateLoggedInUsername(newName));
        }
    }

    public void addPoints(int pointsToAdd, boolean persistToDb) {
        if (pointsToAdd <= 0) return;
        int newPoints = getCurrentPoints() + pointsToAdd;
        setInitialPoints(newPoints);
        if (controller != null) {
            Platform.runLater(() -> controller.updatePointsDisplay(newPoints));
        }
        if (persistToDb && memberId > 0) {
            new Thread(() -> {
                try (Connection con = new DatabaseConnection().connectDB();
                    PreparedStatement pst = con.prepareStatement("UPDATE internet_cafe.member SET point = ? WHERE member_id = ?")) {
                    pst.setInt(1, newPoints);
                    pst.setInt(2, memberId);
                    pst.executeUpdate();
                } catch (Exception e) {
                    System.err.println("addPoints DB error: " + e.getMessage());
                }
            }).start();
        }
    }

    public void deductPoints(int pointsToDeduct, boolean persistToDb) {
        if (pointsToDeduct <= 0) return;
        if (persistToDb && memberId > 0 && isActive && sessionId > 0) {
            deductPointsFromDatabase(pointsToDeduct);
            return;
        }
        int newPoints = Math.max(0, getCurrentPoints() - pointsToDeduct);
        setInitialPoints(newPoints);
        if (controller != null) {
            Platform.runLater(() -> {
                controller.updatePointsDisplay(newPoints);
                controller.refreshTotalLifetimeSpent();
            });
        }
        if (persistToDb && memberId > 0) {
            new Thread(() -> {
                try (Connection con = new DatabaseConnection().connectDB();
                     PreparedStatement pst = con.prepareStatement(
                             "UPDATE internet_cafe.member SET point = ? WHERE member_id = ?")) {
                    pst.setInt(1, newPoints);
                    pst.setInt(2, memberId);
                    pst.executeUpdate();
                } catch (Exception e) {
                    System.err.println("deductPoints DB error: " + e.getMessage());
                }
            }).start();
        }
    }

    public boolean chargePointsForSeconds(int seconds) {
        int requiredPoints = (int) Math.ceil(seconds / (double) secondsPerPoint);
        if (requiredPoints <= 0) return true;
        if (getCurrentPoints() < requiredPoints) {
            Platform.runLater(() -> showToast(
                "Insufficient Points",
                String.format(
                    "You need %d points for %d minutes. Current rate: 1 point per %d minutes.",
                    requiredPoints, seconds / 60, secondsPerPoint / 60
                ),
                PixelMotion.ToastType.WARN
            ));
            return false;
        }
        deductPoints(requiredPoints, true);
        return true;
    }

    public void handleSessionEnd(int elapsedSeconds) {
        int pointsUsed = elapsedSeconds / secondsPerPoint;
        int remaining = Math.max(0, getCurrentPoints() - pointsUsed);
        setInitialPoints(remaining);
        if (controller != null) {
            Platform.runLater(() -> controller.updatePointsDisplay(remaining));
        }
        logSessionOnly();
        int minutesUsed = elapsedSeconds / 60;
        Platform.runLater(() -> {
            if (controller != null) {
                controller.showPointsDeductedNotification(pointsUsed, remaining, minutesUsed);
            }
        });
    }

    private void logSessionOnly() {
        if (memberId <= 0) return;
        if (sessionId <= 0) {
            sessionId = createSessionRecord(memberId, TestController.clientName);
            if (sessionId <= 0) return;
        }
        String formattedTime = formatElapsedTime(elapsedSeconds);
        try (Connection con = new DatabaseConnection().connectDB();
             PreparedStatement pst = con.prepareStatement(
                 "UPDATE internet_cafe.session SET total_time = ? WHERE session_id = ?")) {
            pst.setString(1, formattedTime);
            pst.setInt(2, sessionId);
            pst.executeUpdate();
            System.out.println("Session logged: total_time = " + formattedTime);
        } catch (SQLException e) {
            System.err.println("Database error in logSessionOnly: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String formatElapsedTime(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public void setController(HomeController controller) {
        this.controller = controller;
        System.out.println("HomeController set in ClientImpl");
    }

    public void startTimer() {
        System.out.println("startTimer() called - Initial points: " + initialPoints + ", Rate: " + secondsPerPoint + "s/point");
        isActive = true;
        logoutInProgress = false;
        elapsedSeconds = 0;
        lastLoggedPointsUsed = 0;
        pointsUpdatedRemotely = false;
        adminTerminated = false;
        pendingDepletionTransition = null;
        this.displayElapsedOffset = 0;
        if (controller != null) {
            Platform.runLater(() -> controller.updateElapsedTimeDisplay(0));
        }
        System.out.println("Timer started - waiting for server updates");
    }

    public void manualLogout() {
        if (!isActive || logoutInProgress) return;
        logoutInProgress = true;
        isActive = false;
        new Thread(() -> {
            logSessionOnly();
            try {
                server.logoutClient(this);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            Platform.runLater(this::Logout);
        }).start();
    }

    public void Logout() {
        stopTimer();
        isActive = false;
        pointsUpdatedRemotely = false;
        if (controller != null) {
            controller.cleanup();
            controller = null;
        }
        Stage stage = MemberDashboard.stage;
        if (stage == null) return;
        Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/member_view/Test.fxml"));
                Parent loginRoot = loader.load();
                TestController newTestController = loader.getController();
                newTestController.setClient(this);
                this.testController = newTestController;
                Scene newScene = new Scene(loginRoot);
                stage.setScene(newScene);
                stage.setTitle("Internet Cafe - Login");
                stage.setFullScreen(true);
                stage.show();

                System.gc();
                logoutInProgress = false;
                System.out.println("Switched to login screen - Client maintained: " + clientName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        if (testController != null) {
            testController.markLoggedOut();
        }
    }

    public void resetForNewSession(int memberId, String memberName, int points) {
        this.memberId = memberId;
        this.memberName = memberName;
        this.initialPoints = points;
        this.currentPoints = points;
        this.elapsedSeconds = 0;
        this.isActive = false;
        this.logoutInProgress = false;
        this.pointsUpdatedRemotely = false;
        this.adminTerminated = false;
        this.pendingDepletionTransition = null;
        this.displayElapsedOffset = 0;
        this.secondsPerPoint = 360;
        System.out.println("Client reset - Member ID: " + memberId + ", Name: " + memberName + ", Points: " + points);
    }

    public void stopTimer() {
        isActive = false;
        System.out.println("Timer stopped");
    }

    public boolean isActive() {
        return isActive;
    }

    @Override
    public void onProfileUpdated(String updatedUsername) throws RemoteException {
        if (updatedUsername == null || !updatedUsername.equals(memberName)) {
            return;
        }
        Platform.runLater(() -> {
            System.out.println("RMI callback: Profile updated for " + updatedUsername);
            int freshPoints = fetchCurrentPointsFromDB();
            if (freshPoints >= 0) {
                currentPoints = freshPoints;
                initialPoints = freshPoints;
            }
            String freshTier = fetchTierFromDatabase();
            if (freshTier != null && !freshTier.isEmpty()) {
                currentTier = freshTier;
            }
            if (controller != null) {
                controller.updatePointsDisplay(currentPoints);
                controller.updateUserInfo(memberName, currentTier);
                if (controller.getCurrentCenterController() instanceof ProfilePageController) {
                    ((ProfilePageController) controller.getCurrentCenterController()).refreshProfile();
                }
            }
        });
    }

    private int fetchCurrentPointsFromDB() {
        if (memberId <= 0) return -1;
        try (Connection con = new DatabaseConnection().connectDB();
            PreparedStatement pst = con.prepareStatement(
                "SELECT point FROM internet_cafe.member WHERE member_id = ?")) {
            pst.setInt(1, memberId);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                return rs.getInt("point");
            }
        } catch (SQLException e) {
            System.err.println("Error fetching points: " + e.getMessage());
        }
        return -1;
    }

    private int createSessionRecord(int memberId, String pcName) {
        String sql = "INSERT INTO internet_cafe.session (member_id, computer_id, start_time) " +
                    "VALUES (?, (SELECT computer_id FROM internet_cafe.computer WHERE model = ?), NOW())";
        try (Connection con = new DatabaseConnection().connectDB();
            PreparedStatement pst = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pst.setInt(1, memberId);
            pst.setString(2, pcName);
            pst.executeUpdate();
            ResultSet rs = pst.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public String getTierDisplayName() {
        if (currentTier == null) return "Normal";
        return currentTier.substring(0, 1).toUpperCase() + currentTier.substring(1).toLowerCase();
    }

    public void checkPointsAndForceLogoutIfNeeded() {
        if (!isActive || logoutInProgress || adminTerminated) {
            return;   // no action if logout already scheduled or admin terminated
        }
        int currentPointsValue = getCurrentPoints();
        int pointsUsed = elapsedSeconds / secondsPerPoint;
        int remaining = currentPointsValue - pointsUsed;
        System.out.println("Manual points check - Current: " + currentPointsValue + ", Used: " + pointsUsed + ", Remaining: " + remaining);
        if (remaining <= 0 || currentPointsValue <= 0) {
            System.out.println("Manual check detected zero points - forcing logout");
            logoutInProgress = true;
            isActive = false;

            // Cancel any pending depletion transition
            if (pendingDepletionTransition != null) {
                pendingDepletionTransition.stop();
                pendingDepletionTransition = null;
            }

            TestController.pendingLogoutMessage =
                "Your session ended because your points were fully consumed.\n\n" +
                "Current points: " + currentPointsValue + "\n" +
                "Points used this session: " + pointsUsed + "\n\n" +
                "Please recharge to start a new session.";
            Platform.runLater(() -> {
                // Do not show if admin already terminated
                if (adminTerminated) return;

                showToast(
                    "Session Ending – Points Consumed",
                    "Your points have been fully consumed.\n" +
                    "Your session will now end.",
                    PixelMotion.ToastType.WARN
                );

                PauseTransition delay = new PauseTransition(javafx.util.Duration.millis(2100));
                pendingDepletionTransition = delay;
                delay.setOnFinished(event -> {
                    pendingDepletionTransition = null;
                    try {
                        forceLogout();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                });
                delay.play();
            });
        }
    }

    private void deductPointsFromDatabase(int pointsToDeduct) {
        if (memberId <= 0 || pointsToDeduct <= 0) return;
        new Thread(() -> {
            try (Connection con = new DatabaseConnection().connectDB()) {
                con.setAutoCommit(false);
                try {
                    String selectSql = "SELECT point FROM internet_cafe.member WHERE member_id = ? FOR UPDATE";
                    int currentDbPoints = 0;
                    try (PreparedStatement selectStmt = con.prepareStatement(selectSql)) {
                        selectStmt.setInt(1, memberId);
                        ResultSet rs = selectStmt.executeQuery();
                        if (rs.next()) {
                            currentDbPoints = rs.getInt("point");
                        }
                    }
                    int newDbPoints = Math.max(0, currentDbPoints - pointsToDeduct);
                    String updateSql = "UPDATE internet_cafe.member SET point = ? WHERE member_id = ?";
                    try (PreparedStatement updateStmt = con.prepareStatement(updateSql)) {
                        updateStmt.setInt(1, newDbPoints);
                        updateStmt.setInt(2, memberId);
                        int updated = updateStmt.executeUpdate();
                        System.out.println("DATABASE UPDATE: Points from " + currentDbPoints + " to " + newDbPoints);
                    }
                    String updateSessionSql = "UPDATE internet_cafe.session SET session_total_cost = COALESCE(session_total_cost, 0) + ? WHERE session_id = ?";
                    try (PreparedStatement sessionStmt = con.prepareStatement(updateSessionSql)) {
                        sessionStmt.setInt(1, pointsToDeduct);
                        sessionStmt.setInt(2, sessionId);
                        sessionStmt.executeUpdate();
                    }
                    con.commit();
                    this.currentPoints = newDbPoints;
                    this.initialPoints = newDbPoints;
                    if (server != null) {
                        try {
                            server.notifyPointsDeducted(clientName, pointsToDeduct, newDbPoints);
                            System.out.println("Notified server about point deduction");
                        } catch (RemoteException ex) {
                            System.err.println("Failed to notify server: " + ex.getMessage());
                        }
                    }
                    if (controller != null) {
                        Platform.runLater(() -> {
                            controller.updatePointsDisplay(newDbPoints);
                            controller.refreshTotalLifetimeSpent();
                        });
                    }
                } catch (SQLException e) {
                    con.rollback();
                    System.err.println("Error deducting points: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    con.setAutoCommit(true);
                }
            } catch (SQLException e) {
                System.err.println("Database connection error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void showToast(String title, String message, PixelMotion.ToastType type) {
        javafx.scene.Node anchor = null;
        if (controller != null) {
            anchor = controller.getMainBorderPane();
        }
        if (anchor == null && testController != null) {
            anchor = testController.getToastAnchor();
        }
        if (anchor != null) {
            PixelMotion.toastGlitch(anchor, title, message, type);
        }
    }

    @Override
    public void shutdownPC() throws RemoteException {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            String command;
            if (os.contains("win")) {
                command = "shutdown -s -t 0";
            } else if (os.contains("mac")) {
                command = "sudo shutdown -h now";
            } else if (os.contains("nix") || os.contains("nux")) {
                command = "shutdown -h now";
            } else {
                System.err.println("Unsupported OS for shutdown: " + os);
                return;
            }
            Runtime.getRuntime().exec(command);
            System.out.println("Shutdown command sent: " + command);
        } catch (Exception e) {
            System.err.println("Failed to execute shutdown command: " + e.getMessage());
            throw new RemoteException("Shutdown failed", e);
        }
    }
}