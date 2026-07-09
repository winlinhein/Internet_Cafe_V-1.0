package admin_controllers;

import animation.PixelMotion;

import database.DatabaseConnection;
import static admin_controllers.Admin_Main.stage;
import java.io.IOException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import member_controllers.ClientInterface;
import java.rmi.registry.Registry;

public class PCViewController implements Initializable, InitializableController {

    @FXML private BorderPane root;
    @FXML private TextField searchField;
    @FXML private Button btnNotifications;
    @FXML private Label notifBadge;
    @FXML private Button btnSettings;
    @FXML private Button btnLogout;
    @FXML private VBox sidebar;
    @FXML private Label lblStatus;
    @FXML private Label lblShift;
    @FXML private Label lblClock;
    @FXML private FlowPane pcFlow;
    @FXML private StackPane contentStack;

    private Connection con;
    private ServerInterface server;
    private ClientInterface client;
    private Registry registry;

    private java.util.Map<String, PcCardController> cardControllers = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (root != null) {
            PixelMotion.applyToLater(root);
        }

        if (searchField != null) {
            searchField.textProperty().addListener((obs, old, newVal) -> filterPCs(newVal));
        }

        if (con != null) {
            System.out.println("Database connection already available in PCViewController");
            Platform.runLater(() -> loadPC());
        } else {
            System.out.println("PCViewController: Waiting for database connection to be injected...");
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
                System.out.println("PCViewController: Linked PCViewController with ServerImpl");
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
    private void goGames(ActionEvent event) throws IOException {
        switchScene("/views/game.fxml");
    }

    @FXML
    private void goInventory(ActionEvent event) throws IOException {
        switchScene("/views/inventory.fxml");
    }

    @FXML
    void goStaff(ActionEvent event) throws IOException {
        switchScene("/views/staff.fxml");
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
                    System.out.println("Server unexported from PCViewController");
                } catch (java.rmi.NoSuchObjectException e) {
                    System.out.println("Server was already unexported.");
                }
                server = null; 
            }

            if (registry != null) {
                try {
                    java.rmi.server.UnicastRemoteObject.unexportObject(registry, true);
                    System.out.println("Registry unexported from PCViewController");
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
                    System.out.println("Database connection closed from PCViewController");
                } catch (Exception e) {
                    System.err.println("Error closing database connection: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error during cleanup: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void toggleSidebar(ActionEvent event) {
    }

    @FXML
    private void onRefresh(ActionEvent event) {
        loadPC();
    }

    @FXML
    private void onAddPC(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/add_pc_modal.fxml"));
            Parent addRoot = loader.load();

            AddPcModalController pcController = loader.getController();
            pcController.setParentController(this);
            
            if (pcController instanceof InitializableController && con != null) {
                ((InitializableController) pcController).setDatabaseConnection(con);
                ((InitializableController) pcController).setServer(server);
                ((InitializableController) pcController).setClient(client);
            }

            PixelMotion.showOverlayInStack(contentStack, addRoot, false, this::loadPC);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void loadPC() {
        if (con == null) {
            System.err.println("Database connection is null, cannot load PCs");
            return;
        }

        for (PcCardController c : cardControllers.values()) {
            if (c != null) c.cleanup();
        }
        cardControllers.clear();
        Platform.runLater(() -> pcFlow.getChildren().clear());

        final ServerInterface     capturedServer = server;
        final ClientInterface     capturedClient = client;
        final Connection          capturedCon    = con;

        new Thread(() -> {
            String sql    = "SELECT * FROM internet_cafe.computer";
            URL    cardUrl = getClass().getResource("/views/pc_card.fxml");

            java.util.List<VBox> cardList    = new java.util.ArrayList<>();
            java.util.Map<String, PcCardController> newControllers =
                    new java.util.LinkedHashMap<>();

            try (PreparedStatement pst = capturedCon.prepareStatement(sql);
                 ResultSet rs = pst.executeQuery()) {

                while (rs.next()) {
                    PC pc = new PC(
                            rs.getInt("computer_id"),
                            rs.getString("model"),
                            rs.getString("status")
                    );

                    FXMLLoader loader = new FXMLLoader(cardUrl);
                    VBox card = loader.load();
                    PcCardController cardController = loader.getController();
                    cardController.setParentController(this);
                    cardController.setData(pc);
                    card.setUserData(cardController);   // needed for search filter

                    newControllers.put(pc.getModel(), cardController);
                    cardList.add(card);
                }

                Platform.runLater(() -> {
                    pcFlow.getChildren().setAll(cardList);
                    cardControllers.putAll(newControllers);

                    for (PcCardController cc : newControllers.values()) {
                        cc.setServer(capturedServer);
                        cc.setClient(capturedClient);
                        cc.setDatabaseConnection(capturedCon);
                    }

                    for (int i = 0; i < cardList.size(); i++) {
                        PixelMotion.playPcGridCardSpawn(cardList.get(i), i);
                    }
                    System.out.println("Loaded " + cardList.size() + " PCs");

                    if (searchField != null && !searchField.getText().trim().isEmpty()) {
                        filterPCs(searchField.getText());
                    }
                });

            } catch (Exception e) {
                System.err.println("Error loading PCs: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void filterPCs(String keyword) {
        if (pcFlow == null) return;
        final String lowerKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        for (javafx.scene.Node node : pcFlow.getChildren()) {
            if (node instanceof VBox card) {
                Object userData = card.getUserData();
                String model = "";
                if (userData instanceof PcCardController) {
                    model = ((PcCardController) userData).getModel();
                }
                boolean matches = lowerKeyword.isEmpty() || (model != null && model.toLowerCase().contains(lowerKeyword));
                card.setManaged(matches);
                card.setVisible(matches);
            }
        }
    }

    private void loadCardUI(URL url, PC pc) {
        try {
            FXMLLoader loader = new FXMLLoader(url);
            VBox card = loader.load();

            PcCardController cardController = loader.getController();
            cardController.setParentController(this);
            cardController.setData(pc);
            card.setUserData(cardController);

            if (cardController instanceof InitializableController) {
                ((InitializableController) cardController).setServer(server);
                ((InitializableController) cardController).setClient(client);
                ((InitializableController) cardController).setDatabaseConnection(con);
            }

            cardControllers.put(pc.getModel(), cardController);
            pcFlow.getChildren().add(card);
        } catch (IOException ex) {
            System.err.println("Error loading card for PC " + pc.getModel() + ": " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    public void updateCardUser(String pcName, String userName) {
        Platform.runLater(() -> {
            PcCardController controller = cardControllers.get(pcName);
            if (controller != null) {
                controller.updateUserLabel(userName);
                System.out.println("Updated card for " + pcName + " with user: " + userName);
            } else {
                System.err.println("Card controller not found for PC: " + pcName);
            }
        });
    }
    
    public void updateCardElapsed(String pcName, int elapsedSeconds) {
        Platform.runLater(() -> {
            PcCardController controller = cardControllers.get(pcName);
            if (controller != null) {
                controller.updateElapsedTime(elapsedSeconds);
                System.out.println("PCViewController: updated elapsed for " + pcName + " -> " + elapsedSeconds + "s");
            } else {
                System.err.println("PCViewController: card controller not found for elapsed update: " + pcName);
            }
        });
    }
    
    public void allowClientLogin(PC pc) {
        if (server != null && pc != null) {
            try {
                server.allowSpecificClient(pc.getModel());
                System.out.println("Allowed client login for PC: " + pc.getModel());
            } catch (RemoteException e) {
                System.err.println("Failed to allow client login for " + pc.getModel() + ": " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("Cannot allow login: server=" + (server != null) + ", pc=" + (pc != null));
        }
    }
    
    public void allowClientLogout(PC pc) {
        if (server != null && pc != null) {
            try {
                server.forceLogoutClient(pc.getModel());
                System.out.println("Force client logout for PC: " + pc.getModel());
            } catch (RemoteException e) {
                System.err.println("Failed to force client logout for " + pc.getModel() + ": " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.out.println("Cannot allow login: server=" + (server != null) + ", pc=" + (pc != null));
        }
    }
    
    public void cleanup() {
        if (con != null) {
            try {
                con.close();
                System.out.println("Database connection closed from PCViewController cleanup");
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        cardControllers.clear();
    }

    @Override
    public void setServer(ServerInterface server) { 
        this.server = server;
        System.out.println("PCViewController: Server injected");
        if (server instanceof ServerImpl) {
            ((ServerImpl) server).setPCController(this);
            System.out.println("PCViewController: Linked with ServerImpl");
        }
        for (PcCardController cardController : cardControllers.values()) {
            if (cardController != null) {
                cardController.setServer(server);
            }
        }
    }

    @Override
    public void setClient(ClientInterface client) { 
        this.client = client;
        System.out.println("PCViewController: Client injected");
        for (PcCardController cardController : cardControllers.values()) {
            if (cardController != null) {
                cardController.setClient(client);
            }
        }
    }
    
    @Override
    public void setDatabaseConnection(Connection con) {
        this.con = con;
        System.out.println("PCViewController: Database connection injected");
        for (PcCardController cardController : cardControllers.values()) {
            if (cardController != null) {
                cardController.setDatabaseConnection(con);
            }
        }
        if (con != null) {
            Platform.runLater(this::loadPC);
        }
    }

    public StackPane getContentStack() {
        return contentStack;
    }

    public ServerInterface getServer() {
        return this.server;
    }

    public ClientInterface getClient() {
        return this.client;
    }

    public Connection getDatabaseConnection() {
        return this.con;
    }

    public void unregisterCallback() {
    }
}