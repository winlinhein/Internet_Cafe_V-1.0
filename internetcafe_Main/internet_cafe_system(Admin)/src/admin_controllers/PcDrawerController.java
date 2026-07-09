package admin_controllers;

import animation.PixelMotion;
import member_controllers.ClientInterface;
import java.net.URL;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.util.ResourceBundle;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.event.ActionEvent;

public class PcDrawerController implements Initializable, InitializableController {

    @FXML private VBox rootDrawer;
    @FXML private Label lblSelectedStatus;
    @FXML private Label lblSelectedPc;
    @FXML private Label lblSelectedUser;
    @FXML private Label lblSelectedSession;
    @FXML private Label lblSessionCurrentState;
    @FXML private Label lblSessionCurrentUser;
    @FXML private Button btnUnlock;
    @FXML private Button btnLock;
    @FXML private Button btnShutDown;

    private PC selectedPC;
    private PcCardController cardController;
    private PCViewController mainController;
    private ServerInterface server;
    private ClientInterface client;
    private Connection con;
    private int currentElapsedSeconds = 0;
    private String currentUserName = "";

    private Timeline displayUpdater;

    private static final String SESSION_STATE_RUNNING    = "Running";
    private static final String SESSION_STATE_WAITING    = "Waiting";
    private static final String SESSION_STATE_NOT_ACTIVE = "Not Active";
    private static final String STATUS_ONLINE = "Online";
    private static final String STATUS_AVAILABLE = "Available";
    private static final String STATUS_OFFLINE = "Offline";

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * True when the PC is registered with the system (DB status "online" OR
     * "active"). This is the single source of truth — never read
     * lblSelectedStatus.getText() for logic, because that label shows the raw
     * DB string and becomes stale as soon as the server state changes.
     */
    private boolean isPcRegistered() {
        if (selectedPC == null) return false;
        String s = selectedPC.getStatus();
        return "online".equalsIgnoreCase(s) || "active".equalsIgnoreCase(s);
    }

    /** True when the server has confirmed an active user on this PC. */
    private boolean hasActiveUser() {
        return currentUserName != null
                && !currentUserName.isEmpty()
                && !currentUserName.equals("Available")
                && !currentUserName.equals("Ready")
                && !currentUserName.equals("Offline");
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────

    public void setMainController(PCViewController controller)  { this.mainController = controller; }
    public void setParentController(PcCardController controller){ this.cardController  = controller; }

    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
        System.out.println("PcDrawerController: Server injected for "
                + (selectedPC != null ? selectedPC.getModel() : "unknown"));
        ensureDisplayUpdaterRunning();
        refreshFromServer();
    }

    @Override public void setClient(ClientInterface client)  { this.client = client; }
    @Override public void setDatabaseConnection(Connection c){ this.con    = c; }

    public void setUserName(String userName) {
        this.currentUserName = userName;
        refreshDisplay();
    }

    public void setElapsedTime(int elapsedSeconds) {
        this.currentElapsedSeconds = elapsedSeconds;
        updateSessionTimeDisplay();
        refreshDisplay();
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        PixelMotion.applyToLater(rootDrawer);
        updateButtonStates();
        ensureDisplayUpdaterRunning();
    }

    // ── timer ─────────────────────────────────────────────────────────────────

    private void ensureDisplayUpdaterRunning() {
        if (displayUpdater != null) return;
        displayUpdater = new Timeline(new KeyFrame(Duration.seconds(1), e -> refreshFromServer()));
        displayUpdater.setCycleCount(Timeline.INDEFINITE);
        displayUpdater.play();
    }

    private void stopDisplayUpdater() {
        if (displayUpdater != null) { displayUpdater.stop(); displayUpdater = null; }
    }

    // ── server refresh ────────────────────────────────────────────────────────

    private void refreshFromServer() {
        if (selectedPC == null) return;

        if (server != null) {
            try {
                int    serverElapsed  = server.getElapsedTimeForPC(selectedPC.getModel());
                String serverUserName = server.getCurrentUserForPC(selectedPC.getModel());

                currentElapsedSeconds = serverElapsed;
                currentUserName = (serverUserName == null) ? "" : serverUserName;

                // Keep selectedPC.status in sync with live server truth.
                // "active" = someone is logged in; "online" = registered, no user.
                if (hasActiveUser()) {
                    selectedPC.setStatus("active");
                } else if ("active".equalsIgnoreCase(selectedPC.getStatus())) {
                    selectedPC.setStatus("online"); // user just left
                }

                refreshDisplay();
                return;
            } catch (RemoteException e) {
                System.err.println("PcDrawerController: Error refreshing from server: " + e.getMessage());
            }
        }

        // Fallback: advance local clock only if we believe a session is running
        if (isPcRegistered() && hasActiveUser()) {
            currentElapsedSeconds++;
            updateSessionTimeDisplay();
            updateSessionState(SESSION_STATE_RUNNING);
        }
    }

    /**
     * Single method that updates ALL display elements from the current in-memory
     * state (selectedPC.getStatus() + currentUserName + currentElapsedSeconds).
     * All logic reads selectedPC.getStatus() — NOT lblSelectedStatus.getText() —
     * so it is always correct regardless of what the label shows.
     */
    private void refreshDisplay() {
        updateSelectedStatusDisplay();
        updateSessionTimeDisplay();
        updateUserInfoDisplay();
        updateSessionStateBasedOnStatus();
        updateButtonStates();
    }

    // ── display helpers ───────────────────────────────────────────────────────

    private void updateSelectedStatusDisplay() {
        if (lblSelectedStatus == null) return;

        String displayStatus = getDisplayStatus();
        lblSelectedStatus.setText(displayStatus);
        lblSelectedStatus.getStyleClass().removeAll(
                "pcs-chip-online", "pcs-chip-idle", "pcs-chip-offline");
        if (!lblSelectedStatus.getStyleClass().contains("pcs-status-chip")) {
            lblSelectedStatus.getStyleClass().add("pcs-status-chip");
        }

        switch (displayStatus) {
            case STATUS_ONLINE -> lblSelectedStatus.getStyleClass().add("pcs-chip-online");
            case STATUS_AVAILABLE -> lblSelectedStatus.getStyleClass().add("pcs-chip-idle");
            default -> lblSelectedStatus.getStyleClass().add("pcs-chip-offline");
        }
    }

    private String getDisplayStatus() {
        if (hasActiveUser()) {
            return STATUS_ONLINE;
        }
        if (isPcRegistered()) {
            return STATUS_AVAILABLE;
        }
        return STATUS_OFFLINE;
    }

    private void updateButtonStates() {
        boolean registered = isPcRegistered();
        boolean hasUser    = hasActiveUser();

        // Unlock = allow login  → only when registered AND no one logged in yet
        if (btnUnlock  != null) btnUnlock .setDisable(!registered ||  hasUser);
        // Lock   = force logout → only when someone IS logged in
        if (btnLock    != null) btnLock   .setDisable(!hasUser);
        // Shutdown              → whenever PC is registered
        if (btnShutDown!= null) btnShutDown.setDisable(!registered);
    }

    private void updateUserInfoDisplay() {
        if (lblSelectedUser != null) {
            if (hasActiveUser()) {
                lblSelectedUser.setText("User: " + currentUserName);
            } else if (isPcRegistered()) {
                lblSelectedUser.setText("User: Unknown");
            } else {
                lblSelectedUser.setText("No User");
            }
        }

        if (lblSessionCurrentUser != null) {
            if (hasActiveUser()) {
                lblSessionCurrentUser.setText(currentUserName);
            } else if (isPcRegistered()) {
                lblSessionCurrentUser.setText("Unknown User");
            } else {
                lblSessionCurrentUser.setText("None");
            }
        }
    }

    private void updateSessionStateBasedOnStatus() {
        // Use selectedPC.getStatus() — never lblSelectedStatus.getText() — so
        // that "active" (DB value for a running session) is handled correctly.
        if (isPcRegistered()) {
            if (hasActiveUser()) {
                updateSessionState(SESSION_STATE_RUNNING);
            } else {
                updateSessionState(SESSION_STATE_WAITING);
            }
        } else {
            updateSessionState(SESSION_STATE_NOT_ACTIVE);
        }
    }

    private void updateSessionState(String state) {
        if (lblSessionCurrentState == null) return;
        lblSessionCurrentState.setText(state);
        switch (state) {
            case SESSION_STATE_RUNNING:
                lblSessionCurrentState.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                break;
            case SESSION_STATE_WAITING:
                lblSessionCurrentState.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
                break;
            default: // NOT_ACTIVE
                lblSessionCurrentState.setStyle("-fx-text-fill: #95a5a6;");
                break;
        }
    }

    private void updateSessionTimeDisplay() {
        if (lblSelectedSession != null)
            lblSelectedSession.setText(formatElapsed(currentElapsedSeconds));
    }

    private String formatElapsed(int seconds) {
        int h = seconds / 3600, m = (seconds % 3600) / 60, s = seconds % 60;
        return h > 0 ? String.format("%02d:%02d:%02d", h, m, s)
                     : String.format("%02d:%02d", m, s);
    }

    // ── FXML actions ──────────────────────────────────────────────────────────

    @FXML
    private void handleUnlock(ActionEvent event) {
        if (selectedPC == null || mainController == null) return;
        if (isPcRegistered()) {
            mainController.allowClientLogin(selectedPC);
            close(event);
        }
    }

    @FXML
    private void handleLock(ActionEvent event) {
        if (selectedPC == null || mainController == null) return;
        if (isPcRegistered()) {
            mainController.allowClientLogout(selectedPC);
            close(event);
        }
    }

   @FXML
private void handleShutDown(ActionEvent event) {
    if (selectedPC == null || server == null) {
        System.err.println("Cannot shutdown: missing PC or server reference");
        return;
    }

    // Optional: ask for confirmation (you can add an Alert dialog here)
    // For now, just execute the shutdown.
    try {
        server.shutDownClient(selectedPC.getModel());
    } catch (RemoteException e) {
        System.err.println("Error during shutdown of " + selectedPC.getModel() + ": " + e.getMessage());
        // Optional: show an error toast in the admin UI
        PixelMotion.toastGlitch(btnShutDown, "Shutdown Failed",
                "Could not shut down " + selectedPC.getModel() + ": " + e.getMessage(),
                PixelMotion.ToastType.ERROR);
    }

    // Close the drawer
    close(event);
}

    @FXML
    void close(ActionEvent event) {
        stopDisplayUpdater();
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }

    @FXML
    private void closeEditPopup(ActionEvent event) {
        stopDisplayUpdater();
        PixelMotion.closeOverlayFrom((Node) event.getSource());
    }

    // ── public API called by PcCardController / PCViewController ─────────────

    public void initData(PC pc) {
        this.selectedPC = pc;
        if (lblSelectedPc     != null) lblSelectedPc    .setText(pc.getModel());

        refreshDisplay();          // initial paint from DB state
        ensureDisplayUpdaterRunning();
        refreshFromServer();       // immediately correct from live server state
    }

    public void updateUserInfo(String userName) {
        this.currentUserName = userName;
        refreshDisplay();
    }

    public void updateStatus(String status) {
        if (selectedPC != null) selectedPC.setStatus(status);

        if (!isPcRegistered()) {
            currentUserName = "";
            currentElapsedSeconds = 0;
        }
        refreshDisplay();
    }

    public void updateTimeFromServer(int elapsedSeconds) {
        this.currentElapsedSeconds = elapsedSeconds;
        updateSessionTimeDisplay();
        updateSessionStateBasedOnStatus();
    }

    public void cleanup() { stopDisplayUpdater(); }
}
