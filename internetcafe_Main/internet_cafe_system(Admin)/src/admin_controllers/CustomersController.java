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
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import member_controllers.ClientInterface;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;

public class CustomersController implements Initializable, InitializableController, AdminCallback {

    @FXML private Button btnLogout, btnNotifications, btnSettings;
    @FXML private StackPane contentStack;
    @FXML private FlowPane customerFlow;
    @FXML private Label lblClock, lblShift, lblStatus, notifBadge;
    @FXML private BorderPane root;
    @FXML private TextField searchField;
    @FXML private VBox sidebar;
    @FXML private TextField memberSearchField;

    private Connection con;
    private ServerInterface server;
    private ClientInterface client;
    private Registry registry;
    private final Map<Integer, Long> lastRefreshTime = new ConcurrentHashMap<>();
    private static final long REFRESH_DEBOUNCE_MS = 100;
    private final Map<Integer, Integer> lastKnownPoints = new ConcurrentHashMap<>();

    private final Map<Integer, CustomersCardController> cardMap = new ConcurrentHashMap<>();
    private boolean callbackRegistered = false;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (con != null) {
            System.out.println("CustomersController: DB connection already available");
            Platform.runLater(this::loadMember);
        } else {
            System.out.println("CustomersController: Waiting for DB connection...");
        }

        TextField activeSearch = memberSearchField != null ? memberSearchField : searchField;
        if (activeSearch != null) {
            activeSearch.textProperty().addListener((obs, old, newVal) -> filterMembers(newVal));
        }
    }

    @Override
    public void onMemberUpdated(int memberId, int newPoints, String newName) {
        Platform.runLater(() -> {
            CustomersCardController card = cardMap.get(memberId);
            if (card != null) {
                if (newPoints >= 0) card.setPointsFromAdmin(newPoints);
                if (newName != null) card.updateName(newName);
            }
        });
    }

    @Override
    public void onMemberTimeTick(int memberId, int remainingPoints, int elapsedSeconds) {
        Platform.runLater(() -> {
            CustomersCardController card = cardMap.get(memberId);
            if (card != null && remainingPoints >= 0) {
                card.updatePoints(remainingPoints);
                Integer lastPoints = lastKnownPoints.get(memberId);
                if (lastPoints != null && remainingPoints < lastPoints) {
                    card.refreshTotalSpent();
                    System.out.println("Points dropped for member " + memberId + ", refreshing total spent");
                }
                lastKnownPoints.put(memberId, remainingPoints);
            }
        });
    }

    @Override
    public void onMemberDataChanged(int memberId) throws RemoteException {
        Platform.runLater(() -> {
            CustomersCardController card = cardMap.get(memberId);
            if (card != null) {
                Long lastTime = lastRefreshTime.get(memberId);
                long now = System.currentTimeMillis();
                if (lastTime == null || now - lastTime > REFRESH_DEBOUNCE_MS) {
                    lastRefreshTime.put(memberId, now);
                    card.refreshTotalSpent();
                } else {
                    System.out.println("Debounced refresh for member " + memberId);
                }
            }
        });
    }

    @Override
    public void onMemberPurchase(int saleId, String memberName, double amountSpent, String pcName) throws RemoteException {
        Platform.runLater(() -> {
            for (CustomersCardController card : cardMap.values()) {
                if (memberName.equals(card.getMemberName())) {
                    int memberId = card.getMemberId();
                    lastRefreshTime.remove(memberId);
                    new Thread(() -> {
                        try {
                            int livePoints = server.getLiveRemainingPoints(memberId);
                            Platform.runLater(() -> {
                                if (livePoints >= 0) card.setPointsFromAdmin(livePoints);
                                card.refreshTotalSpent();
                            });
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }).start();
                    break;
                }
            }
        });
    }

    @Override
    public void onLoginRequest(String pcName) throws RemoteException {
    }

    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
        System.out.println("CustomersController: Server injected");
        registerAdminCallback();
    }

    @Override
    public void setClient(ClientInterface client) {
        this.client = client;
        System.out.println("CustomersController: Client injected");
    }

    @Override
    public void setDatabaseConnection(Connection con) {
        this.con = con;
        if (con != null) {
            Platform.runLater(() -> {
                loadMember();
                ensureRegistered();
            });
        }
    }

    public void loadMember() {
        if (con == null) {
            System.err.println("Cannot load members: DB connection is null");
            return;
        }

        new Thread(() -> {
            cardMap.clear();
            lastRefreshTime.clear();
            lastKnownPoints.clear();
            String sql = "SELECT m.member_id, m.member_name, m.member_type_id, m.phone, m.email, m.password, m.point " +
                         "FROM internet_cafe.member m";

            try (PreparedStatement pst = con.prepareStatement(sql);
                 ResultSet rs = pst.executeQuery()) {

                URL cardUrl = getClass().getResource("/views/customer_card.fxml");
                java.util.List<StackPane> cardList = new java.util.ArrayList<>();

                while (rs.next()) {
                    int memberId      = rs.getInt("member_id");
                    String memberName = rs.getString("member_name");
                    int memberTypeId  = rs.getInt("member_type_id");
                    String phone      = rs.getString("phone");
                    String email      = rs.getString("email");
                    String password   = rs.getString("password");
                    double dbPoints   = rs.getDouble("point");

                    int livePoints = -1;
                    try {
                        livePoints = server.getLiveRemainingPoints(memberId);
                    } catch (RemoteException ex) {
                        System.err.println("Failed to fetch live points for member " + memberId);
                    }

                    double displayPoints = (livePoints >= 0) ? livePoints : Math.max(0, dbPoints);

                    Customer customer = new Customer(
                            memberId, memberName, memberTypeId,
                            phone, email, password, displayPoints);

                    FXMLLoader loader = new FXMLLoader(cardUrl);
                    StackPane card = loader.load();
                    CustomersCardController cardController = loader.getController();
                    cardController.setData(customer);
                    cardController.setParentController(this);
                    card.setUserData(cardController);

                    if (cardController instanceof InitializableController) {
                        ((InitializableController) cardController).setServer(server);
                        ((InitializableController) cardController).setClient(client);
                        ((InitializableController) cardController).setDatabaseConnection(con);
                    }

                    cardMap.put(memberId, cardController);
                    cardList.add(card);
                }

                Platform.runLater(() -> {
                    customerFlow.getChildren().setAll(cardList);
                    for (int i = 0; i < cardList.size(); i++) {
                        PixelMotion.playInventoryCardSpawn(cardList.get(i), i);
                    }
                    System.out.println("Loaded " + cardList.size() + " customers");

                    TextField activeSearch = memberSearchField != null ? memberSearchField : searchField;
                    if (activeSearch != null && !activeSearch.getText().trim().isEmpty()) {
                        filterMembers(activeSearch.getText());
                    }
                });

            } catch (Exception e) {
                System.err.println("Error loading members: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> showError("Load Error", e.getMessage()));
            }
        }).start();
    }

    private void filterMembers(String keyword) {
        if (customerFlow == null) return;
        final String lowerKeyword = keyword == null ? "" : keyword.trim().toLowerCase();
        for (javafx.scene.Node node : customerFlow.getChildren()) {
            if (node instanceof StackPane card) {
                Object userData = card.getUserData();
                String name = "";
                if (userData instanceof CustomersCardController) {
                    name = ((CustomersCardController) userData).getMemberName();
                }
                boolean matches = lowerKeyword.isEmpty() || (name != null && name.toLowerCase().contains(lowerKeyword));
                card.setManaged(matches);
                card.setVisible(matches);
            }
        }
    }

    private void registerAdminCallback() {
        if (!callbackRegistered && server != null) {
            try {
                server.unregisterAdminCallback(this);
                server.registerAdminCallback(this);
                callbackRegistered = true;
                System.out.println("CustomersController registered as AdminCallback");
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public void unregisterCallback() {
        if (callbackRegistered && server != null) {
            try {
                server.unregisterAdminCallback(this);
                callbackRegistered = false;
                System.out.println("CustomersController unregistered from admin callbacks");
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void ensureRegistered() {
        registerAdminCallback();
    }

    public void cleanup() {
        unregisterCallback();
    }

    private void showError(String title, String message) {
        Platform.runLater(() ->
            PixelMotion.toastGlitch(searchField, title,
                message == null ? "Unknown error" : message,
                PixelMotion.ToastType.ERROR));
    }

    private void switchScene(String fxmlPath) throws IOException {
        Object prevCtrl = Admin_Main.getActiveController();
        if (prevCtrl instanceof AdminCallback && prevCtrl != this) {
            if (prevCtrl instanceof CustomersController) {
                ((CustomersController) prevCtrl).unregisterCallback();
            } else if (prevCtrl instanceof Admin_dashboardController) {
                ((Admin_dashboardController) prevCtrl).unregisterCallback();
            } else if (prevCtrl instanceof PCViewController) {
                ((PCViewController) prevCtrl).unregisterCallback();
            }
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
            }
        }

        if (stage.getScene() == null) {
            stage.setScene(new Scene(rootNode));
        } else {
            stage.getScene().setRoot(rootNode);
        }
        stage.setFullScreen(true);

        this.unregisterCallback();
        Admin_Main.setActiveController(controller);
    }

    @FXML void goGames(ActionEvent event) throws IOException { switchScene("/views/game.fxml"); }
    @FXML void goInventory(ActionEvent event) throws IOException { switchScene("/views/inventory.fxml"); }
    @FXML void goOverview(ActionEvent event) throws IOException { switchScene("/views/admin_dashboard.fxml"); }
    @FXML void goPCs(ActionEvent event) throws IOException { switchScene("/views/pc_view.fxml"); }
    @FXML void goStaff(ActionEvent event) throws IOException { switchScene("/views/staff.fxml"); }

    @FXML
    void onLogout(ActionEvent event) throws IOException {
        cleanup();
        handleStop();
        database.DatabaseConnection.disconnect();
        Parent loginRoot = FXMLLoader.load(getClass().getResource("/views/admin_login.fxml"));
        stage.getScene().setRoot(loginRoot);
        stage.setFullScreen(false);
    }

    private void handleStop() {
        try {
            if (server != null) {
                try { java.rmi.server.UnicastRemoteObject.unexportObject(server, true); } catch (Exception e) {}
                server = null;
            }
            if (registry != null) {
                try { java.rmi.server.UnicastRemoteObject.unexportObject(registry, true); } catch (Exception e) {}
                registry = null;
            }
            if (con != null && !con.isClosed()) {
                try (PreparedStatement pst = con.prepareStatement(
                        "UPDATE internet_cafe.computer SET status = 'offline' WHERE computer_id > 0")) {
                    pst.executeUpdate();
                }
                con.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onAddCustomer(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/add_customer.fxml"));
            Parent addRoot = loader.load();

            AddCustomerController addController = loader.getController();
            addController.setParentController(this);
            addController.setRefreshCallback(() -> {
                Thread thread = new Thread(this::loadMember);
                thread.setDaemon(true);
                thread.start();
            });

            if (addController instanceof InitializableController) {
                ((InitializableController) addController).setServer(server);
                ((InitializableController) addController).setClient(client);
                ((InitializableController) addController).setDatabaseConnection(con);
            }

            PixelMotion.showOverlayInStack(contentStack, addRoot, false);
        } catch (IOException e) {
            e.printStackTrace();
            showError("Modal Error", "Could not open add customer form.");
        }
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
            showError("Modal Error", "Could not open profile.");
        }
    }

    @FXML void onAddMember(ActionEvent event) { onAddCustomer(event); }

    @FXML
    void onRefresh(ActionEvent event) {
        loadMember();
        ensureRegistered();
    }

    @FXML void toggleSidebar(ActionEvent event) {
    }

    public StackPane getContentStack() {
        return contentStack;
    }
    
    @Override
    public void onMemberTierChanged(int memberId, String newTier) {
        Platform.runLater(() -> {
            CustomersCardController card = cardMap.get(memberId);
            if (card != null) {
                card.updateTier(newTier);
            }
        });
    }
}