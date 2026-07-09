package admin_controllers;

import animation.PixelMotion;

import database.DatabaseConnection;
import static admin_controllers.Admin_Main.stage;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import member_controllers.ClientInterface;
import java.rmi.registry.Registry;

public class StaffController implements Initializable, InitializableController {

    @FXML private BorderPane root;
    @FXML private TextField topSearchField;
    @FXML private Button btnNotifications;
    @FXML private Label notifBadge;
    @FXML private Button btnSettings;
    @FXML private Button btnLogout;
    @FXML private VBox sidebar;
    @FXML private Label lblStatus;
    @FXML private Label lblShift;
    @FXML private Label lblClock;
    @FXML private TextField staffSearchField;
    @FXML private FlowPane staffFlow;
    @FXML private Button addBtn;
    @FXML private javafx.scene.layout.StackPane contentStack;
    @FXML private Label totalStaffLabel;
    @FXML private Label filteredStaffLabel;
    @FXML private Label privilegedStaffLabel;
    @FXML private Label contactCoverageLabel;

    private Connection con;
    private ServerInterface server;
    private ClientInterface client;
    private Registry registry;
    private String super_admin = Admin_Login.admin_name;
    private boolean searchListenerAttached = false;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (con != null) {
            System.out.println("Database connection already available in StaffController");
            Platform.runLater(() -> {
                try {
                    initializeStaffView();
                } catch (SQLException e) {
                    e.printStackTrace();
                    showError("Database Error", e.getMessage());
                }
            });
        } else {
            System.out.println("StaffController: Waiting for database connection to be injected...");
        }
    }

    private void switchScene(String fxmlPath) throws IOException {
        Object prevCtrl = Admin_Main.getActiveController();
        if (prevCtrl instanceof AdminCallback && prevCtrl != this) {
            if (prevCtrl instanceof CustomersController) {
                ((CustomersController) prevCtrl).unregisterCallback();
            } else if (prevCtrl instanceof Admin_dashboardController) {
                ((Admin_dashboardController) prevCtrl).unregisterCallback();
            }
        }
        if (prevCtrl instanceof InventoryController && prevCtrl != this) {
            ((InventoryController) prevCtrl).unregisterCallback();
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        Parent rootNode = loader.load();
        PixelMotion.applyTo(rootNode);

        Object controller = loader.getController();
        if (controller instanceof InitializableController) {
            InitializableController ic = (InitializableController) controller;
            ic.setServer(server);
            ic.setClient(client);
            ic.setDatabaseConnection(con);

            if (controller instanceof PCViewController && server instanceof ServerImpl) {
                ((ServerImpl) server).setPCController((PCViewController) controller);
                System.out.println("StaffController: Linked PCViewController with ServerImpl");
            }
        }

        if (stage.getScene() == null) {
            stage.setScene(new Scene(rootNode));
        } else {
            stage.getScene().setRoot(rootNode);
        }
        stage.setFullScreen(true);

        Admin_Main.setActiveController(controller);
    }

    @FXML
    private void goOverview(ActionEvent event) throws IOException {
        switchScene("/views/admin_dashboard.fxml");
    }

    @FXML
    private void goCustomers(ActionEvent event) throws IOException {
        switchScene("/views/customer.fxml");
    }

    @FXML
    private void goPCs(ActionEvent event) throws IOException {
        switchScene("/views/pc_view.fxml");
    }

    @FXML
    private void goGames(ActionEvent event) throws IOException {
        switchScene("/views/game.fxml");
    }

    @FXML
    void goInventory(ActionEvent event) throws IOException {
        switchScene("/views/inventory.fxml");
    }

    @FXML
    void handleMyProfile(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/MyProfile.fxml"));
            Parent profileRoot = loader.load();

            Object ctrl = loader.getController();
            if (ctrl instanceof InitializableController) {
                ((InitializableController) ctrl).setServer(server);
                ((InitializableController) ctrl).setClient(client);
                ((InitializableController) ctrl).setDatabaseConnection(con);
            }

            PixelMotion.showOverlayInStack(contentStack, profileRoot, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onLogout(ActionEvent event) throws IOException {
        handleStop();
        database.DatabaseConnection.disconnect();
        Parent loginRoot = FXMLLoader.load(getClass().getResource("/views/admin_login.fxml"));
        stage.getScene().setRoot(loginRoot);
        stage.setFullScreen(false);
    }
    
    private void handleStop() {
        try {
            if (server != null) {
                try {
                    java.rmi.server.UnicastRemoteObject.unexportObject(server, true);
                    System.out.println("Server unexported from StaffController");
                } catch (java.rmi.NoSuchObjectException e) {
                    System.out.println("Server was already unexported.");
                }
                server = null; 
            }

            if (registry != null) {
                try {
                    java.rmi.server.UnicastRemoteObject.unexportObject(registry, true);
                    System.out.println("Registry unexported from StaffController");
                } catch (java.rmi.NoSuchObjectException e) {
                    System.out.println("Registry was already unexported.");
                }
                registry = null;
            }
            
            if (con != null && !con.isClosed()) {
                try (PreparedStatement pst = con.prepareStatement("UPDATE internet_cafe.computer SET status = 'offline' WHERE computer_id > 0")) {
                    int updated = pst.executeUpdate();
                    System.out.println("Updated " + updated + " PCs to offline status");
                } catch (Exception e) {
                    System.err.println("Error updating PC status: " + e.getMessage());
                }
            }
            
            if (con != null && !con.isClosed()) {
                try {
                    con.close();
                    System.out.println("Database connection closed from StaffController");
                } catch (Exception e) {
                    System.err.println("Error closing database connection: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error during cleanup: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void loadMember(String query) {
        if (con == null) {
            System.err.println("Database connection is null, cannot load staff members");
            return;
        }
        
        new Thread(() -> {
            try {
                int totalStaff = 0;
                int privilegedStaff = 0;
                int contactCoverage = 0;
                String summarySql = "SELECT COUNT(*) AS total_staff, " +
                        "SUM(CASE WHEN admin_id = 1 THEN 1 ELSE 0 END) AS privileged_staff, " +
                        "SUM(CASE WHEN TRIM(COALESCE(phone, '')) <> '' AND TRIM(COALESCE(email, '')) <> '' THEN 1 ELSE 0 END) AS contact_ready " +
                        "FROM internet_cafe.admin";
                try (PreparedStatement summaryStmt = con.prepareStatement(summarySql);
                     ResultSet summaryRs = summaryStmt.executeQuery()) {
                    if (summaryRs.next()) {
                        totalStaff = summaryRs.getInt("total_staff");
                        privilegedStaff = summaryRs.getInt("privileged_staff");
                        contactCoverage = summaryRs.getInt("contact_ready");
                    }
                }

                String sql = "SELECT * FROM internet_cafe.admin WHERE admin_name LIKE ?";
                try (PreparedStatement pst = con.prepareStatement(sql)) {
                    pst.setString(1, "%" + (query == null ? "" : query.trim()) + "%");
                    try (ResultSet rs = pst.executeQuery()) {
                        URL cardUrl = getClass().getResource("/views/staff_card.fxml");
                        java.util.List<VBox> cardList = new java.util.ArrayList<>();

                        while (rs.next()) {
                            Staff staff = new Staff(
                                rs.getInt("admin_id"),
                                rs.getString("admin_name"),
                                rs.getString("phone"),
                                rs.getString("email"),
                                rs.getString("passwords")
                            );

                            FXMLLoader loader = new FXMLLoader(cardUrl);
                            VBox card = loader.load();
                            StaffCardController cardController = loader.getController();
                            cardController.setParentController(this);
                            cardController.setData(staff);
                            
                            if (cardController instanceof InitializableController && con != null) {
                                ((InitializableController) cardController).setDatabaseConnection(con);
                            }
                            
                            cardList.add(card);
                        }
                        final int totalStaffValue = totalStaff;
                        final int privilegedStaffValue = privilegedStaff;
                        final int contactCoverageValue = contactCoverage;

                        Platform.runLater(() -> {
                            staffFlow.getChildren().setAll(cardList);
                            updateStaffSummary(totalStaffValue, cardList.size(), privilegedStaffValue, contactCoverageValue);
                            for (int i = 0; i < cardList.size(); i++) {
                                PixelMotion.playInventoryCardSpawn(cardList.get(i), i);
                            }
                            System.out.println("Loaded " + cardList.size() + " staff members");
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> showError("Load Error", e.getMessage()));
            }
        }).start();
    }

    @FXML
    private void openModal(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/add_staff.fxml"));
            Parent modalRoot = loader.load();

            AddStaffController addController = loader.getController();
            addController.setParentController(this);
            addController.setRefreshCallback(() -> loadMember(""));
            
            if (addController instanceof InitializableController) {
                ((InitializableController) addController).setServer(server);
                ((InitializableController) addController).setClient(client);
                ((InitializableController) addController).setDatabaseConnection(con);
            }

            PixelMotion.showOverlayInStack(contentStack, modalRoot, false);
        } catch (IOException e) {
            e.printStackTrace();
            showError("Modal Error", e.getMessage());
        }
    }

    private void checkAdmin() throws SQLException {
        if (con == null) {
            System.err.println("Cannot check admin: database connection is null");
            return;
        }
        
        int admin_id = 0;
        String sql = "SELECT admin_id from internet_cafe.admin WHERE admin_name=?";
        try (PreparedStatement pst = con.prepareStatement(sql)) {
            pst.setString(1, super_admin);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) admin_id = rs.getInt("admin_id");
            }
        }
        
        boolean isSuperAdmin = (admin_id == 1);
        Platform.runLater(() -> {
            addBtn.setDisable(!isSuperAdmin);
            addBtn.setVisible(isSuperAdmin);
            System.out.println("Admin check: Super admin = " + isSuperAdmin);
        });
    }
    
    private void showError(String title, String message) {
        Platform.runLater(() -> {
            javafx.scene.Node anchor = staffSearchField != null
                ? staffSearchField
                : (topSearchField != null ? topSearchField : root);
            PixelMotion.toastGlitch(anchor, title,
                message == null ? "Unknown error" : message,
                PixelMotion.ToastType.ERROR);
        });
    }

    @Override 
    public void setServer(ServerInterface server) { 
        this.server = server;
        System.out.println("StaffController: Server injected");
    }
    
    @Override 
    public void setClient(ClientInterface client) { 
        this.client = client;
        System.out.println("StaffController: Client injected");
    }
    
    public javafx.scene.layout.StackPane getContentStack() {
        return contentStack;
    }

    @Override
    public void setDatabaseConnection(Connection con) {
        this.con = con;
        System.out.println("StaffController: Database connection injected");
        if (con != null) {
            Platform.runLater(() -> {
                try {
                    initializeStaffView();
                } catch (SQLException e) {
                    e.printStackTrace();
                    showError("Database Error", e.getMessage());
                }
            });
        }
    }
    
    @FXML 
    private void toggleSidebar(ActionEvent event) { 
    }
    
    @FXML 
    private void handleSearchAction(KeyEvent event) { 
        if (!searchListenerAttached && staffSearchField != null && con != null) {
            loadMember(staffSearchField.getText()); 
        }
    }
    
    @FXML 
    private void onRefreshBtnClick(ActionEvent event) { 
        if (staffSearchField != null) {
            staffSearchField.clear();
        }
        loadMember(""); 
    }
    
    @FXML 
    private void onAddProduct(ActionEvent event) { 
        openModal(event); 
    }

    private void initializeStaffView() throws SQLException {
        checkAdmin();
        attachSearchListener();
        loadMember("");
    }

    private void attachSearchListener() {
        if (searchListenerAttached) {
            return;
        }

        TextField activeSearchField = staffSearchField != null ? staffSearchField : topSearchField;
        if (activeSearchField == null) {
            return;
        }

        activeSearchField.textProperty().addListener((observable, oldValue, newValue) -> loadMember(newValue));
        searchListenerAttached = true;
    }

    private void updateStaffSummary(int totalStaff, int filteredStaff, int privilegedStaff, int contactCoverage) {
        if (totalStaffLabel != null) {
            totalStaffLabel.setText(String.valueOf(totalStaff));
        }
        if (filteredStaffLabel != null) {
            filteredStaffLabel.setText(String.valueOf(filteredStaff));
        }
        if (privilegedStaffLabel != null) {
            privilegedStaffLabel.setText(String.valueOf(privilegedStaff));
        }
        if (contactCoverageLabel != null) {
            contactCoverageLabel.setText(totalStaff == 0
                ? "0%"
                : Math.round((contactCoverage * 100.0) / totalStaff) + "%");
        }
    }
}