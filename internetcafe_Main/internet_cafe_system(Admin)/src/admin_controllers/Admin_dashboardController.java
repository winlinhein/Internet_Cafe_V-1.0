package admin_controllers;

import animation.PixelMotion;
import database.DatabaseConnection;
import static admin_controllers.Admin_Main.stage;
import java.io.IOException;
import java.net.URL;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import member_controllers.ClientInterface;
import java.rmi.server.UnicastRemoteObject;

public class Admin_dashboardController implements Initializable, InitializableController, AdminCallback {
    private static final DateTimeFormatter OVERVIEW_DAY_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter OVERVIEW_MONTH_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy");
    
    @FXML private BorderPane root;
    @FXML private TextField searchField;
    @FXML private Button btnNotifications;
    @FXML private StackPane notifRoot;
    @FXML private Label notifBadge;
    @FXML private ImageView notifBellImage;
    @FXML private Button btnSettings;
    @FXML private Button btnLogout;
    @FXML private VBox sidebar;
    @FXML private Button btnToggleSidebar;
    @FXML private Label lblStatus, lblShift, lblClock, lblPointSaleToday, lblPointSalethismonth;
    @FXML private StackPane contentStack;
    @FXML private VBox pageOverview;
    @FXML private Label lblOverviewTitle;
    @FXML private VBox cardActive, cardIncomeDay, cardIncomeMonth;
    
    @FXML private VBox panelTopSell, panelPcStatus;
    
    @FXML private Label lblActivePcs, lblIncomeToday, lblIncomeMonth;
    @FXML private StackPane topSellChartHolder, pcActivityChartHolder;
    @FXML private VBox pageCustomers;
    @FXML private ComboBox<String> cmbRevenueRange;
    @FXML private DatePicker mainDatePicker;
    @FXML private PieChart pieProductsSoldMonth;
    @FXML private VBox pieLegendBox;
    @FXML private Label lblTodayTitle, lblMonthTitle;

    private ScheduledExecutorService pollingExecutor;

    private ServerInterface server;
    private ClientInterface client;
    private Connection con;
    private Registry registry;
    private AdminCallback adminCallbackStub;
    private final ObservableList<String> notifications = FXCollections.observableArrayList();
    private int unreadNotificationCount = 0;
    private NotificationPopupController currentPopupController;
    private Node currentOverlayWrapper;
    private static Admin_dashboardController instance;
    private static final String NOTIF_BELL_GIF = "/images/notification-bell-1.gif";
    private static final String NOTIF_BELL_PNG = "/images/notification-bell-1.png";
    private Image staticBellImage;
    private boolean callbackRegistered = false;
     
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        instance = this;
        if (pageOverview != null) {
            pageOverview.setVisible(true);
            pageOverview.setManaged(true);
        }
        
        DatabaseConnection db = new DatabaseConnection();
        try {
            con = db.connectDB();
            System.out.println("Database Connection Established");
            loadDashboardDataAsync();
        } catch (Exception ex) {
            System.err.println("Connection Failed: " + ex.getMessage());
            ex.printStackTrace();
        }

         startRMIServer();
        registerAdminCallback();
        showOverview();
        initNotificationBell();
        initMainDatePicker();

        Platform.runLater(() ->
            PixelMotion.applyDashboard(root, sidebar, btnToggleSidebar, contentStack));
    }
    
    public static Connection getSharedConnection() {
        return instance != null ? instance.con : null;
    }
    
    private synchronized Connection getValidConnection() throws SQLException {
        if (con == null || con.isClosed()) {
            con = new DatabaseConnection().connectDB();
        }
        return con;
    }

    private void loadDashboardDataAsync() {
        Task<Void> loadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                int activePcs = fetchActivePcCountFromDB();
                double todayIncome = fetchTodayIncome();
                double monthIncome = fetchMonthIncome();
                double monthpointuse = fetchMonthpointuse();
                double todaypointuse = fetchTodaypointuse();
                
                Platform.runLater(() -> {
                    PixelMotion.animateCountUp(lblActivePcs, activePcs, "", "");
                    PixelMotion.animateCountUp(lblIncomeToday, todayIncome, " P", "%.0f");
                    PixelMotion.animateCountUp(lblIncomeMonth, monthIncome, " P", "%.0f");
                    lblPointSalethismonth.setText(String.format("%.0f MMK", monthpointuse * 600));
                    lblPointSaleToday.setText(String.format("%.0f MMK", todaypointuse * 600));

                    setupRevenueRangeCombo();
                    updateRevenueChart();
                    updateProductsSoldThisMonthPie();
                });
                
                startActivePcPolling();
                return null;
            }
        };
        new Thread(loadTask).start();
    }

    private void switchScene(String fxmlPath) throws IOException {
        Object previousCtrl = Admin_Main.getActiveController();
        if (previousCtrl instanceof AdminCallback && previousCtrl != this) {
            if (previousCtrl instanceof CustomersController) {
                ((CustomersController) previousCtrl).unregisterCallback();
            } else if (previousCtrl instanceof Admin_dashboardController) {
                ((Admin_dashboardController) previousCtrl).unregisterCallback();
            }
            // PCViewController does not have callbacks, so it is intentionally skipped
        }

        FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
        Parent pageRoot = loader.load();

        Object controller = loader.getController();
        if (controller instanceof InitializableController) {
            InitializableController ic = (InitializableController) controller;
            ic.setServer(server);
            ic.setClient(client);
            ic.setDatabaseConnection(con);

            if (controller instanceof PCViewController && server instanceof ServerImpl) {
                ((ServerImpl) server).setPCController((PCViewController) controller);
                System.out.println("Dashboard: Linked PCViewController with ServerImpl");
            }
        }

        PixelMotion.switchSceneAnimated(contentStack, pageRoot);

        Admin_Main.setActiveController(controller);
    }

    @FXML private void goCustomers(ActionEvent event) throws IOException { switchScene("/views/customer.fxml"); }
    @FXML private void goPCs(ActionEvent event) throws IOException { switchScene("/views/pc_view.fxml"); }
    @FXML private void goGames(ActionEvent event) throws IOException { switchScene("/views/game.fxml"); }
    @FXML private void goInventory(ActionEvent event) throws IOException { switchScene("/views/inventory.fxml"); }
    @FXML void goStaff(ActionEvent event) throws IOException { switchScene("/views/staff.fxml"); }
    @FXML private void goOverview(ActionEvent event) { showOverview(); }
    
    @FXML
    void handleSessionHistoryAction(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/session_history.fxml"));
            Parent pageRoot = loader.load();

            Object controller = loader.getController();
            if (controller instanceof InitializableController) {
                InitializableController ic = (InitializableController) controller;
                ic.setServer(server);
                ic.setClient(client);
                ic.setDatabaseConnection(con);
            }

            PixelMotion.switchSceneAnimated(contentStack, pageRoot);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public StackPane getContentStack() {
        return contentStack;
    }

    @FXML private void onLogout(ActionEvent event) throws IOException {
        handleStop();
        database.DatabaseConnection.disconnect();
        Parent loginRoot = FXMLLoader.load(getClass().getResource("/views/admin_login.fxml"));
        stage.getScene().setRoot(loginRoot);
        stage.setFullScreen(false);
    }

    @FXML private void toggleSidebar(ActionEvent event) {
        PixelMotion.toggleSidebar(sidebar);
    }

    private void showOverview() {
        if (pageOverview != null) {
            pageOverview.setManaged(true);
            PixelMotion.switchSceneAnimated(contentStack, pageOverview);

            updateActivePcCountAsync();
            updateTodayIncomeAsync();
            updateMonthIncomeAsync();

            Platform.runLater(() -> {
                updateRevenueChart();
                updateProductsSoldThisMonthPie();
            });
        }
    }

    @FXML void handleMyProfile(ActionEvent event) {
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
    void handleNoti(ActionEvent event) {
        if (isNotificationPopupOpen()) {
            closeNotificationPopup();
            return;
        }
        openNotificationPopup();
    }

    private void startRMIServerInBackground() {
        Task<Void> rmiTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                startRMIServer();
                Platform.runLater(() -> registerAdminCallback());
                return null;
            }
        };
        rmiTask.setOnFailed(e -> {
            rmiTask.getException().printStackTrace();
            Platform.runLater(() -> showError("Server Error", "Failed to start RMI server"));
        });
        new Thread(rmiTask, "rmi-startup").start();
    }

    private void startRMIServer() {
        try {
            if (server == null) {
                server = new ServerImpl();
                if (server instanceof ServerImpl && con != null) {
                    ((ServerImpl) server).setDatabaseConnection(con);
                    System.out.println("Database connection passed to ServerImpl");
                }
            }

            try {
                registry = LocateRegistry.getRegistry(1100);
                registry.list();
            } catch (RemoteException e) {
                registry = LocateRegistry.createRegistry(1100);
            }
            
            registry.rebind("Client", server);
            System.out.println("RMI Server started and bound to 'Client'");
        } catch (Exception e) {
            System.err.println("RMI Start Error: " + e.getMessage());
            e.printStackTrace();
        }
    }   
   
    private void handleStop() {
        try {
            stopActivePcPolling();
            unregisterCallback();

            if (server != null) {
                try {
                    java.rmi.server.UnicastRemoteObject.unexportObject(server, true);
                } catch (java.rmi.NoSuchObjectException ignored) {}
                server = null; 
            }

            if (registry != null) {
                try {
                    java.rmi.server.UnicastRemoteObject.unexportObject(registry, true);
                } catch (java.rmi.NoSuchObjectException ignored) {}
                registry = null;
            }
            
            if (con != null && !con.isClosed()) {
                try (PreparedStatement pst = con.prepareStatement("UPDATE internet_cafe.computer SET status = 'offline' WHERE computer_id > 0")) {
                    pst.executeUpdate();
                }
                con.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void unregisterCallback() {
        stopActivePcPolling();   // shut down the polling executor when we leave the dashboard
        if (callbackRegistered && server != null) {
            try {
                server.unregisterAdminCallback(adminCallbackStub);
                java.rmi.server.UnicastRemoteObject.unexportObject(adminCallbackStub, true);
                callbackRegistered = false;
                System.out.println("Admin dashboard unregistered from callbacks");
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override public void setServer(ServerInterface server) { this.server = server; }
    @Override public void setClient(ClientInterface client) { this.client = client; }
    @Override public void setDatabaseConnection(Connection con) { this.con = con; }

    private int fetchActivePcCountFromDB() {
        String query = "SELECT COUNT(*) FROM internet_cafe.computer WHERE status IN ('online', 'active')";
        try {
            Connection conn = getValidConnection();
            synchronized (conn) {
                try (PreparedStatement pst = conn.prepareStatement(query);
                    ResultSet rs = pst.executeQuery()) {
                    if (rs.next()) return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching active PC count: " + e.getMessage());
        }
        return 0;
    }

    private double fetchTodayIncome() {
        String query = "SELECT SUM(session_total_cost) FROM internet_cafe.session WHERE DATE(session_date) = CURDATE()";
        try {
            Connection conn = getValidConnection();
            synchronized (conn) {
                try (PreparedStatement ps = conn.prepareStatement(query);
                    ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble(1);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }

    private double fetchMonthIncome() {
        String query = "SELECT SUM(session_total_cost) FROM internet_cafe.session " +
                    "WHERE session_date >= DATE_FORMAT(CURDATE(), '%Y-%m-01') " +
                    "AND session_date < DATE_FORMAT(CURDATE(), '%Y-%m-01') + INTERVAL 1 MONTH";
        try {
            Connection conn = getValidConnection();
            synchronized (conn) {
                try (PreparedStatement ps = conn.prepareStatement(query);
                    ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble(1);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }

    private double fetchTodaypointuse() {
        String query = "SELECT SUM(amount) FROM internet_cafe.point_sale WHERE DATE(date) = CURDATE()";
        try {
            Connection conn = getValidConnection();
            synchronized (conn) {
                try (PreparedStatement ps = conn.prepareStatement(query);
                    ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble(1);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }
    
    private double fetchMonthpointuse() {
        String query = "SELECT SUM(amount) FROM internet_cafe.point_sale " +
                    "WHERE date >= DATE_FORMAT(CURDATE(), '%Y-%m-01') " +
                    "AND date < DATE_FORMAT(CURDATE(), '%Y-%m-01') + INTERVAL 1 MONTH";
        try {
            Connection conn = getValidConnection();
            synchronized (conn) {
                try (PreparedStatement ps = conn.prepareStatement(query);
                    ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getDouble(1);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }

    private void updateActivePcCountAsync() {
        Task<Integer> task = new Task<>() {
            @Override protected Integer call() { return fetchActivePcCountFromDB(); }
        };
        task.setOnSucceeded(e -> {
            PixelMotion.animateCountUp(lblActivePcs, task.getValue(), "", "");
            PixelMotion.flashStatCard(cardActive);
        });
        new Thread(task).start();
    }

    private void updateTodayIncomeAsync() {
        Task<Double> task = new Task<>() {
            @Override protected Double call() { return fetchTodayIncome(); }
        };
        task.setOnSucceeded(e -> {
            PixelMotion.animateCountUp(lblIncomeToday, task.getValue(), " P", "%.0f");
            PixelMotion.flashStatCard(cardIncomeDay);
        });
        new Thread(task).start();
    }

    private void updateMonthIncomeAsync() {
        Task<Double> task = new Task<>() {
            @Override protected Double call() { return fetchMonthIncome(); }
        };
        task.setOnSucceeded(e -> {
            PixelMotion.animateCountUp(lblIncomeMonth, task.getValue(), " P", "%.0f");
            PixelMotion.flashStatCard(cardIncomeMonth);
        });
        new Thread(task).start();
    }

    private void updateTodaypointuseAsync() {
        Task<Double> task = new Task<>() {
            @Override protected Double call() { return fetchTodaypointuse(); }
        };
        task.setOnSucceeded(e -> lblPointSaleToday.setText(String.format("%.0f MMK", (double) task.getValue() * 600)));
        new Thread(task).start();
    }
     
    private void updateMonthpointuseAsync() {
        Task<Double> task = new Task<>() {
            @Override protected Double call() { return fetchMonthpointuse(); }
        };
        task.setOnSucceeded(e -> lblPointSalethismonth.setText(String.format("%.0f MMK", (double) task.getValue() * 600)));
        new Thread(task).start();
    }
    
    private void initMainDatePicker() {
        if (mainDatePicker != null) {
            mainDatePicker.setValue(LocalDate.now());
             updateOverviewTitles(LocalDate.now());
            AdminDatePickerUtils.configurePastAndPresentOnly(
                    mainDatePicker,
                    getClass().getResource("/css/pixel_admin_reactive.css").toExternalForm()
            );
            mainDatePicker.setOnAction(e -> {
                LocalDate selectedDate = mainDatePicker.getValue();
                if (selectedDate != null) {
                    updateDataForSelectedDate(selectedDate);
                }
            });
        }
    }
    
    private void updateDataForSelectedDate(LocalDate selectedDate) {
        Task<Void> updateTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                double todayIncome = fetchTodayIncomeForDate(selectedDate);
                double todayPointSale = fetchTodayPointSaleForDate(selectedDate);
                
                double monthIncome = fetchMonthIncomeForDate(selectedDate);
                double monthPointSale = fetchMonthPointSaleForDate(selectedDate);
                
                Platform.runLater(() -> {
                    PixelMotion.animateCountUp(lblIncomeToday, todayIncome, " P", "%.0f");
                    lblPointSaleToday.setText(String.format("%.0f MMK", todayPointSale * 600));
                    PixelMotion.animateCountUp(lblIncomeMonth, monthIncome, " P", "%.0f");
                    lblPointSalethismonth.setText(String.format("%.0f MMK", monthPointSale * 600));

                    PixelMotion.flashStatCard(cardIncomeDay);
                    PixelMotion.flashStatCard(cardIncomeMonth);

                     updateOverviewTitles(selectedDate);

                    updateRevenueChart();
                    updateProductsSoldThisMonthPie();
                });
                
                return null;
            }
        };
        new Thread(updateTask).start();
    }
    
    private void updateOverviewTitles(LocalDate selectedDate) {
        if (selectedDate == null) {
            return;
        }

        LocalDate today = LocalDate.now();
        if (selectedDate.equals(today)) {
            lblTodayTitle.setText("Today");
            lblMonthTitle.setText("This month");
            return;
        }

        lblTodayTitle.setText(selectedDate.format(OVERVIEW_DAY_FORMATTER));
        lblMonthTitle.setText(selectedDate.format(OVERVIEW_MONTH_FORMATTER));
    }
    private double fetchMonthIncomeForDate(LocalDate selectedDate) {
        String query = "SELECT SUM(session_total_cost) FROM internet_cafe.session " +
                    "WHERE session_date >= DATE_FORMAT(?, '%Y-%m-01') " +
                    "AND session_date < DATE_FORMAT(?, '%Y-%m-01') + INTERVAL 1 MONTH";
        try {
            Connection conn = getValidConnection();
            synchronized (conn) {
                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    ps.setDate(1, Date.valueOf(selectedDate));
                    ps.setDate(2, Date.valueOf(selectedDate));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return rs.getDouble(1);
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }
    
    private double fetchMonthPointSaleForDate(LocalDate selectedDate) {
        String query = "SELECT SUM(amount) FROM internet_cafe.point_sale " +
                    "WHERE date >= DATE_FORMAT(?, '%Y-%m-01') " +
                    "AND date < DATE_FORMAT(?, '%Y-%m-01') + INTERVAL 1 MONTH";
        try {
            Connection conn = getValidConnection();
            synchronized (conn) {
                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    ps.setDate(1, Date.valueOf(selectedDate));
                    ps.setDate(2, Date.valueOf(selectedDate));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return rs.getDouble(1);
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }
    
    private double fetchTodayIncomeForDate(LocalDate selectedDate) {
        String query = "SELECT SUM(session_total_cost) FROM internet_cafe.session WHERE DATE(session_date) = ?";
        try {
            Connection conn = getValidConnection();
            synchronized (conn) {
                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    ps.setDate(1, Date.valueOf(selectedDate));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return rs.getDouble(1);
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }
    
    private double fetchTodayPointSaleForDate(LocalDate selectedDate) {
        String query = "SELECT SUM(amount) FROM internet_cafe.point_sale WHERE DATE(date) = ?";
        try {
            Connection conn = getValidConnection();
            synchronized (conn) {
                try (PreparedStatement ps = conn.prepareStatement(query)) {
                    ps.setDate(1, Date.valueOf(selectedDate));
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return rs.getDouble(1);
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }

    private void startActivePcPolling() {
        stopActivePcPolling();
        pollingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PC-Polling");
            t.setDaemon(true);
            return t;
        });
        pollingExecutor.scheduleAtFixedRate(() -> {
            int count = fetchActivePcCountFromDB();
            Platform.runLater(() -> lblActivePcs.setText(String.valueOf(count)));
        }, 0, 3, TimeUnit.SECONDS);
    }

    private void stopActivePcPolling() {
        if (pollingExecutor != null && !pollingExecutor.isShutdown()) {
            pollingExecutor.shutdownNow();
            pollingExecutor = null;
        }
    }

    private void setupRevenueRangeCombo() {
        if (cmbRevenueRange == null) return;
        cmbRevenueRange.getItems().setAll("Weekly", "Monthly");
        cmbRevenueRange.getSelectionModel().select("Weekly");
        cmbRevenueRange.setOnAction(e -> updateRevenueChart());
    }

    private void updateRevenueChart() {
        String selected = (cmbRevenueRange != null) ? cmbRevenueRange.getSelectionModel().getSelectedItem() : "Weekly";
        if ("Monthly".equals(selected)) {
            updateMonthlyChart();
        } else {
            updateWeeklyChart();
        }
    }

    private void updateWeeklyChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Last 7 Days");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Income (P)");
        LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Last 7 days Revenue Overview");
        lineChart.setCreateSymbols(true);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Total Income");

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);
        LocalDate endExclusive = today.plusDays(1);
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("MM-dd");

        String query = "SELECT DATE(session_date) as daily_date, SUM(session_total_cost) as total " +
                    "FROM internet_cafe.session WHERE session_date >= ? AND session_date < ? " +
                    "GROUP BY daily_date";

        Map<LocalDate, Double> totalsByDate = new HashMap<>();
        try {
            Connection conn = getValidConnection();
            synchronized (conn) {
                try (PreparedStatement pst = conn.prepareStatement(query)) {
                    pst.setDate(1, Date.valueOf(start));
                    pst.setDate(2, Date.valueOf(endExclusive));
                    try (ResultSet rs = pst.executeQuery()) {
                        while (rs.next()) {
                            LocalDate d = rs.getDate("daily_date").toLocalDate();
                            totalsByDate.put(d, rs.getDouble("total"));
                        }
                    }
                }
            }
            for (int i = 0; i < 7; i++) {
                LocalDate d = start.plusDays(i);
                series.getData().add(new XYChart.Data<>(d.format(labelFmt), totalsByDate.getOrDefault(d, 0.0)));
            }
            lineChart.getData().add(series);
            topSellChartHolder.getChildren().setAll(lineChart);
            PixelMotion.animateOverviewChart(lineChart, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateMonthlyChart() {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("Last 30 Days");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Income (P)");
        LineChart<String, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("Last 30 days Revenue Overview");
        lineChart.setCreateSymbols(true);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Total Income");

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(29);
        LocalDate endExclusive = today.plusDays(1);
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("MM-dd");

        String query = "SELECT DATE(session_date) as daily_date, SUM(session_total_cost) as total " +
                    "FROM internet_cafe.session WHERE session_date >= ? AND session_date < ? " +
                    "GROUP BY daily_date";

        Map<LocalDate, Double> totalsByDate = new HashMap<>();
        try {
            Connection conn = getValidConnection();
            synchronized (conn) {
                try (PreparedStatement pst = conn.prepareStatement(query)) {
                    pst.setDate(1, Date.valueOf(start));
                    pst.setDate(2, Date.valueOf(endExclusive));
                    try (ResultSet rs = pst.executeQuery()) {
                        while (rs.next()) {
                            LocalDate d = rs.getDate("daily_date").toLocalDate();
                            totalsByDate.put(d, rs.getDouble("total"));
                        }
                    }
                }
            }
            for (int i = 0; i < 30; i++) {
                LocalDate d = start.plusDays(i);
                series.getData().add(new XYChart.Data<>(d.format(labelFmt), totalsByDate.getOrDefault(d, 0.0)));
            }
            lineChart.getData().add(series);
            topSellChartHolder.getChildren().setAll(lineChart);
            PixelMotion.animateOverviewChart(lineChart, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateProductsSoldThisMonthPie() {
        if (pieProductsSoldMonth == null || con == null) return;
        pieProductsSoldMonth.getData().clear();
        if (pieLegendBox != null) pieLegendBox.getChildren().clear();

        String query = """
            SELECT p.product_name, SUM(sd.qty) AS total_sales
            FROM internet_cafe.product p
            JOIN internet_cafe.sale_detail sd ON p.product_id = sd.product_id
            JOIN internet_cafe.sale s ON sd.sale_id = s.sale_id
            WHERE s.sale_date >= DATE_FORMAT(CURRENT_DATE, '%Y-%m-01')
              AND s.sale_date < DATE_FORMAT(CURRENT_DATE, '%Y-%m-01') + INTERVAL 1 MONTH
            GROUP BY p.product_name
            ORDER BY total_sales DESC
            LIMIT 5
            """;

        try {
            Connection conn = getValidConnection();
            List<String> productNames = new ArrayList<>();
            List<Double> quantities = new ArrayList<>();
            double grandTotal = 0.0;

            synchronized (conn) {
                try (PreparedStatement pst = conn.prepareStatement(query);
                     ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("product_name");
                        double qty = rs.getDouble("total_sales");
                        if (qty > 0) {
                            productNames.add(name);
                            quantities.add(qty);
                            grandTotal += qty;
                        }
                    }
                }
            }

            ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
            if (quantities.isEmpty()) {
                data.add(new PieChart.Data("100%", 1));
                pieProductsSoldMonth.setData(data);
                PixelMotion.animateOverviewChart(pieProductsSoldMonth, true);
                if (pieLegendBox != null) {
                    Label empty = new Label("No Sales");
                    empty.setStyle("-fx-text-fill: white;");
                    pieLegendBox.getChildren().add(empty);
                }
                return;
            }

            final String[] palette = {"#ff8a5b", "#5ad8fa", "#7cff7a", "#ffd166", "#c084fc", "#ff6b9f", "#00e5a8", "#9ad0f5", "#f4a261", "#e76f51", "#a8dadc", "#bdb2ff"};
            final List<String> legendLines = new ArrayList<>();
            for (int i = 0; i < quantities.size(); i++) {
                double qty = quantities.get(i);
                double pct = (qty / grandTotal) * 100.0;
                data.add(new PieChart.Data(String.format("%.1f%%", pct), qty));
                legendLines.add(productNames.get(i) + " - " + String.format("%.1f%%", pct));
            }

            pieProductsSoldMonth.setData(data);
            pieProductsSoldMonth.setLabelsVisible(true);
            PixelMotion.animateOverviewChart(pieProductsSoldMonth, true);

            Platform.runLater(() -> {
                if (pieLegendBox == null) return;
                for (Node node : pieProductsSoldMonth.lookupAll(".chart-pie-label")) {
                    if (node instanceof Text) ((Text) node).setFill(Color.WHITE);
                }
                pieLegendBox.getChildren().clear();
                for (int i = 0; i < data.size(); i++) {
                    String color = palette[i % palette.length];
                    PieChart.Data slice = data.get(i);
                    if (slice.getNode() != null) slice.getNode().setStyle("-fx-pie-color: " + color + ";");
                    Pane swatch = new Pane();
                    swatch.setPrefSize(10, 10);
                    swatch.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 5;");
                    Label legendText = new Label(legendLines.get(i));
                    legendText.setStyle("-fx-text-fill: white; -fx-font-size: 11;");
                    pieLegendBox.getChildren().add(new HBox(8, swatch, legendText));
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            pieProductsSoldMonth.getData().clear();
            if (pieLegendBox != null) pieLegendBox.getChildren().clear();
        }
    }
    
    private void registerAdminCallback() {
        if (server == null || callbackRegistered) return;
        try {
            adminCallbackStub = (AdminCallback) UnicastRemoteObject.exportObject(this, 0);
            server.registerAdminCallback(adminCallbackStub);
            callbackRegistered = true;
            System.out.println("Admin dashboard registered for callbacks");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void onMemberUpdated(int memberId, int newPoints, String newName) throws RemoteException {
    }

    @Override
    public void onMemberTimeTick(int memberId, int remainingPoints, int elapsedSeconds) throws RemoteException {
    }

    @Override
    public void onLoginRequest(String pcName) throws RemoteException {
        Platform.runLater(() -> {
            String message = pcName + " has requested login access.";
            addNotification("Request", message);
            showPopupIfNeeded();
        });
    }
    
    @Override
    public void onMemberPurchase(int saleId, String memberName, double amountSpent, String pcName) throws RemoteException {
        Platform.runLater(() -> {
            String notification = String.format("SALE|%d|%s|%s|%.2f", saleId, memberName, pcName, amountSpent);
            addNotification("Purchase", notification);
            showPopupIfNeeded();
        });
    }
    
    private void addNotification(String tag, String message) {
        String fullEntry = tag + "|" + message;
        if (notifications.size() >= 500) {
            notifications.remove(notifications.size() - 1);
        }
        notifications.add(0, fullEntry);
        unreadNotificationCount++;
        setNotificationBellAnimated(true);
        updateNotificationBadge();
    
        if (currentPopupController != null) {
            currentPopupController.setData(unreadNotificationCount, new ArrayList<>(notifications));
        }
    }

    private void initNotificationBell() {
        try {
            URL pngUrl = getClass().getResource(NOTIF_BELL_PNG);
            if (pngUrl != null) {
                staticBellImage = new Image(pngUrl.toExternalForm(), false);
            }
        } catch (Exception ignored) {}
        updateNotificationBadge();
    }

    private void setNotificationBellAnimated(boolean animated) {
        if (notifBellImage == null) {
            return;
        }
        try {
            if (animated) {
                URL gifUrl = getClass().getResource(NOTIF_BELL_GIF);
                if (gifUrl != null) {
                    notifBellImage.setImage(new Image(gifUrl.toExternalForm(), false));
                }
            } else if (staticBellImage != null) {
                notifBellImage.setImage(staticBellImage);
            } else {
                URL pngUrl = getClass().getResource(NOTIF_BELL_PNG);
                if (pngUrl != null) {
                    notifBellImage.setImage(new Image(pngUrl.toExternalForm(), false));
                }
            }
        } catch (Exception ignored) {}
    }

    private void updateNotificationBadge() {
        if (notifBadge != null) {
            notifBadge.setText(unreadNotificationCount > 99 ? "99+" : String.valueOf(unreadNotificationCount));
            notifBadge.setVisible(unreadNotificationCount > 0);
        }
        setNotificationBellAnimated(unreadNotificationCount > 0);
    }

    private boolean isNotificationPopupOpen() {
        if (currentOverlayWrapper != null) {
            if (currentOverlayWrapper.getParent() == contentStack && currentOverlayWrapper.isVisible()) {
                return true;
            }
            currentOverlayWrapper = null;
        }
        if (currentPopupController != null && currentPopupController.getRoot() != null
                && currentPopupController.getRoot().getScene() != null) {
            return true;
        }
        currentPopupController = null;
        return false;
    }

    private void closeNotificationPopup() {
        if (currentPopupController != null && currentPopupController.getRoot() != null) {
            PixelMotion.closeOverlayFrom(currentPopupController.getRoot());
            return;
        }
        if (currentOverlayWrapper != null) {
            PixelMotion.closeOverlayFrom(currentOverlayWrapper);
        }
    }

    private void openNotificationPopup() {
        if (contentStack == null || isNotificationPopupOpen()) {
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/notification_popup.fxml"));
            Parent popupRoot = loader.load();
            currentPopupController = loader.getController();
            currentPopupController.setContentStack(contentStack);
            currentPopupController.setData(unreadNotificationCount, new ArrayList<>(notifications));

            Runnable afterClosed = () -> {
                currentOverlayWrapper = null;
                if (currentPopupController != null) {
                    List<String> updated = currentPopupController.getBoundNotifications();
                    notifications.setAll(updated);
                    unreadNotificationCount = updated.size();
                    updateNotificationBadge();
                }
                currentPopupController = null;
            };

            currentPopupController.setActions(
                null,
                () -> {
                    notifications.clear();
                    unreadNotificationCount = 0;
                    updateNotificationBadge();
                    if (currentPopupController != null) {
                        currentPopupController.setData(0, new ArrayList<>());
                    }
                }
            );

            currentPopupController.setCountChangedHandler(newCount -> {
                unreadNotificationCount = newCount;
                updateNotificationBadge();
            });

            int beforeCount = contentStack.getChildren().size();
            PixelMotion.showNotificationFromRight(contentStack, popupRoot, afterClosed);
            int afterCount = contentStack.getChildren().size();
            if (afterCount > beforeCount) {
                currentOverlayWrapper = contentStack.getChildren().get(afterCount - 1);
            }
        } catch (IOException e) {
            e.printStackTrace();
            currentPopupController = null;
            currentOverlayWrapper = null;
        }
    }
    
    private void showPopupIfNeeded() {
        if (currentPopupController != null) {
            currentPopupController.setData(unreadNotificationCount, new ArrayList<>(notifications));
            return;
        }
        openNotificationPopup();
    }

    @Override
    public void onMemberDataChanged(int memberId) throws RemoteException {
        Platform.runLater(() -> {
            updateActivePcCountAsync();
            updateTodayIncomeAsync();
            updateMonthIncomeAsync();
            updateTodaypointuseAsync();
            updateMonthpointuseAsync();

            if (pageOverview != null && pageOverview.isVisible()) {
                updateProductsSoldThisMonthPie();
            }
        });
    }

    @Override
    public void onMemberTierChanged(int memberId, String newTier) throws RemoteException {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    private void showError(String title, String message) {
        Platform.runLater(() ->
            PixelMotion.toastGlitch(searchField, title,
                message == null ? "Unknown error" : message,
                PixelMotion.ToastType.ERROR));
    }
}