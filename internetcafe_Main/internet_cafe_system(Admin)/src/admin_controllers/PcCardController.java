// PcCardController.java - live session/user sync after page reload
package admin_controllers;

import animation.PixelMotion;
import member_controllers.ClientInterface;
import java.io.IOException;
import java.net.URL;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.util.ResourceBundle;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class PcCardController implements Initializable, InitializableController {

    @FXML private VBox rootCard;
    @FXML private StackPane statusDot;
    @FXML private Label lblName;
    @FXML private Label lblBadge;
    @FXML private StackPane monitorPane;
    @FXML private Label lblZone;
    @FXML private Label lblUser;
    @FXML private Label lblSession;

    private PC currentPC;
    private PCViewController mainController;
    private volatile int elapsedSeconds = 0;
    private ServerInterface server;
    private ClientInterface client;
    private Timeline refreshTimer;
    private String currentUserName = "";
    private Connection con;

    public void setParentController(PCViewController controller) {
        this.mainController = controller;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (rootCard != null) PixelMotion.applyUltraCardHover(rootCard);
        rootCard.setClip(null);
        rootCard.setPickOnBounds(false);
        ensureRefreshTimerRunning();
    }

    private void ensureRefreshTimerRunning() {
        if (refreshTimer != null) {
            return;
        }
        refreshTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> refreshFromServer()));
        refreshTimer.setCycleCount(Timeline.INDEFINITE);
        refreshTimer.play();
    }

    private void refreshFromServer() {
        if (server != null && currentPC != null) {
            try {
                int serverElapsedSeconds = server.getElapsedTimeForPC(currentPC.getModel());
                String serverUserName = server.getCurrentUserForPC(currentPC.getModel());

                elapsedSeconds = serverElapsedSeconds;
                currentUserName = (serverUserName == null) ? "" : serverUserName;

                if (lblSession != null) {
                    lblSession.setText(formatElapsed(elapsedSeconds));
                }

                if (currentUserName != null && !currentUserName.isEmpty()
                        && !currentUserName.equals("Available")
                        && !currentUserName.equals("Ready")
                        && !currentUserName.equals("Offline")) {
                    if (lblUser != null) lblUser.setText(currentUserName);
                    applyBadgeStatus("Online");
                } else if (currentPC != null && "online".equalsIgnoreCase(currentPC.getStatus())) {
                    if (lblUser != null) lblUser.setText("No User");
                    applyBadgeStatus("Available");
                } else {
                    if (lblUser != null) lblUser.setText("No User");
                    applyBadgeStatus("Offline");
                }
            } catch (RemoteException e) {
                System.err.println("Error getting live PC data from server: " + e.getMessage());
            }
        }
    }

    private void stopRefreshTimer() {
        if (refreshTimer != null) {
            refreshTimer.stop();
            refreshTimer = null;
        }
    }

    /**
     * Sets the badge label text AND swaps CSS chip + dot classes so each
     * state gets its own distinct colour at runtime.
     *   Online    → cyan  (pcs-chip-online  / pcs-dot-online)
     *   Available → green (pcs-chip-idle    / pcs-dot-idle)
     *   Offline   → pink  (pcs-chip-offline / pcs-dot-offline)
     */
    private void applyBadgeStatus(String status) {
        if (lblBadge == null) return;
        lblBadge.setText(status);

        // Remove all state classes first
        lblBadge.getStyleClass().removeAll(
                "pcs-chip-online", "pcs-chip-idle", "pcs-chip-offline",
                "pcs-chip-boost",  "pcs-chip-warn");
        if (statusDot != null)
            statusDot.getStyleClass().removeAll(
                    "pcs-dot-online", "pcs-dot-idle", "pcs-dot-offline",
                    "pcs-dot-boost",  "pcs-dot-warn");

        switch (status) {
            case "Online" -> {
                lblBadge.getStyleClass().add("pcs-chip-online");
                if (statusDot != null) statusDot.getStyleClass().add("pcs-dot-online");
            }
            case "Available" -> {
                lblBadge.getStyleClass().add("pcs-chip-idle");
                if (statusDot != null) statusDot.getStyleClass().add("pcs-dot-idle");
            }
            default -> {   // Offline
                lblBadge.getStyleClass().add("pcs-chip-offline");
                if (statusDot != null) statusDot.getStyleClass().add("pcs-dot-offline");
            }
        }
    }

    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
        System.out.println("PcCardController: Server injected for " + (currentPC != null ? currentPC.getModel() : "unknown"));
        ensureRefreshTimerRunning();
        refreshFromServer();
    }

    @Override
    public void setClient(ClientInterface client) {
        this.client = client;
        System.out.println("PcCardController: Client injected for " + (currentPC != null ? currentPC.getModel() : "unknown"));
    }

    @Override
    public void setDatabaseConnection(Connection con) {
        this.con = con;
        System.out.println("PcCardController: Database connection injected for " + (currentPC != null ? currentPC.getModel() : "unknown"));
    }

    public void setData(PC pc) {
        this.currentPC = pc;
        if (lblName != null) lblName.setText(pc.getModel());

        // Normalize raw DB status ("active", "online", "offline") to display status.
        // "active"  → PC has a live user session  → show Online initially; the
        //             1-second refreshTimer will confirm/correct from server state.
        // "online"  → PC is registered, no user   → Available
        // anything else (incl. "offline")          → Offline
        String dbStatus = pc.getStatus();
        if ("active".equalsIgnoreCase(dbStatus)) {
            applyBadgeStatus("Online");
        } else if ("online".equalsIgnoreCase(dbStatus)) {
            applyBadgeStatus("Available");
        } else {
            applyBadgeStatus("Offline");
        }

        if (lblUser != null) lblUser.setText("No User");
        if (lblSession != null) lblSession.setText("00:00");
        refreshFromServer();
    }

    public void updateElapsedTime(int newElapsedSeconds) {
        this.elapsedSeconds = newElapsedSeconds;
        Platform.runLater(() -> {
            if (lblSession != null) {
                lblSession.setText(formatElapsed(elapsedSeconds));
            }
            if (currentUserName != null && !currentUserName.isEmpty()
                    && !currentUserName.equals("No User")
                    && !currentUserName.equals("Available")
                    && !currentUserName.equals("Ready")
                    && !currentUserName.equals("Offline")) {
                applyBadgeStatus("Online");
            }
        });
    }

    private String formatElapsed(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        if (hours > 0) return String.format("%02d:%02d:%02d", hours, minutes, secs);
        return String.format("%02d:%02d", minutes, secs);
    }

    @FXML
    private void openModal(MouseEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/pc_drawer.fxml"));
            Parent root = loader.load();

            PcDrawerController pcController = loader.getController();
            pcController.setParentController(this);
            pcController.setMainController(mainController);
            pcController.setClient(client);
            pcController.setDatabaseConnection(con);
            pcController.setElapsedTime(elapsedSeconds);    
            pcController.setUserName(currentUserName);
            pcController.initData(currentPC);
            pcController.setServer(server);

            StackPane hostStack = mainController != null ? mainController.getContentStack() : null;
            if (hostStack != null) {
                PixelMotion.showOverlayInStack(hostStack, root, false);
            } else {
                PixelMotion.playWindowIntro(root, false);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updateUserLabel(String userName) {
        this.currentUserName = userName;
        Platform.runLater(() -> {
            if (userName != null && !userName.isEmpty() && !userName.equals("Available")
                    && !userName.equals("Ready") && !userName.equals("Offline")) {
                if (lblUser != null) lblUser.setText(userName);
                applyBadgeStatus("Online");
            } else if (userName != null && (userName.equals("Available") || userName.equals("Ready"))) {
                if (lblUser != null) lblUser.setText("No User");
                applyBadgeStatus("Available");
                elapsedSeconds = 0;
                if (lblSession != null) lblSession.setText("00:00");
            } else if (userName != null && userName.equals("Offline")) {
                if (lblUser != null) lblUser.setText("No User");
                applyBadgeStatus("Offline");
                elapsedSeconds = 0;
                if (lblSession != null) lblSession.setText("00:00");
            } else {
                if (lblUser != null) lblUser.setText("No User");
            }
        });
    }

    public String getCurrentUser() {
        return currentUserName;
    }

    public void cleanup() {
        stopRefreshTimer();
    }

    @FXML
    private void handlePCCardClick(MouseEvent event) {
        try {
            openModal(event);
        } catch (Exception e) {
            System.err.println("Error handling PC card click: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public String getModel() {
    return currentPC != null ? currentPC.getModel() : "";
    }
}
