package member_controllers;

import animation.AnimationUtil;
import animation.PixelMotion;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import admin_controllers.ServerInterface;

public class ProfilePageController implements InitializableController {
    private static final String PLAY_TIME_COLOR = "#2dd4ff";
    private static final String FOOD_AND_DRINKS_COLOR = "#8b5cf6";
    private static final String FALLBACK_CHART_COLOR = "#f59e0b";

    private ServerInterface server;
    private ClientImpl client;
    private Connection databaseConnection;

    @FXML private VBox profileCard;
    @FXML private VBox accountDetailsCard;
    @FXML private VBox spendingCard;
    @FXML private VBox dailyCard;

    @FXML private Label nameLabel;
    @FXML private Label memberIdLabel;
    @FXML private Label emailLabel;
    @FXML private Label phoneLabel;
    @FXML private Label balenceLabel;
    @FXML private Label memberTypeLabel;
    @FXML private Label nameLabel1;

    @FXML private PieChart spendingPieChart;
    @FXML private BarChart<String, Number> dailySpendingBarChart;
    @FXML private CategoryAxis dateAxis;
    @FXML private NumberAxis amountAxis;
    @FXML private Label monthlyTotalLabel;

    private String loggedInUsername;
    private HomeController homeController;
    private final ExecutorService profileLoader = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "member-profile-loader");
        t.setDaemon(true);
        return t;
    });
    private volatile int profileLoadVersion = 0;

    public ProfilePageController() {
        System.out.println("=== ProfilePageController CONSTRUCTOR CALLED ===");
    }

    public void setLoggedInUsername(String username) {
        this.loggedInUsername = username;
        loadUserProfile();
    }

    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
        System.out.println("ProfilePageController.setServer() called");
    }

    @Override
    public void setClient(ClientImpl client) {
        this.client = client;
        System.out.println("ProfilePageController.setClient() called");
    }

    @Override
    public void setDatabaseConnection(Connection con) {
        this.databaseConnection = con;
        System.out.println("ProfilePageController.setDatabaseConnection() called");
        if (loggedInUsername != null && !loggedInUsername.trim().isEmpty()) {
            System.out.println("Database connection set, loading profile for: " + loggedInUsername);
            loadUserProfile();
        } else {
            System.out.println("Database connection set but no username available");
        }
    }

    @FXML
    public void initialize() {
        AnimationUtil.neonCardEntrance(profileCard, "#ba66ff", 0.06);
        AnimationUtil.neonCardEntrance(accountDetailsCard, "#59a6ff", 0.16);
        AnimationUtil.neonCardEntrance(spendingCard, "#2dd4ff", 0.26);
        AnimationUtil.neonCardEntrance(dailyCard, "#8b5cf6", 0.36);

        System.out.println("=== ProfilePageController.initialize() START ===");
        System.out.println("UserSession.getCurrentUsername(): " + UserSession.getCurrentUsername());
        System.out.println("UserSession.isUserLoggedIn(): " + UserSession.isUserLoggedIn());

        if (loggedInUsername == null) {
            loggedInUsername = UserSession.getCurrentUsername();
            System.out.println("Got username from UserSession: '" + loggedInUsername + "'");
        } else {
            System.out.println("Username already set: '" + loggedInUsername + "'");
        }

        if (loggedInUsername != null && !loggedInUsername.trim().isEmpty()) {
            System.out.println("Username available, but waiting for database connection to load profile");
        } else {
            System.out.println("No username available for loading profile");
            setDefaultValues();
        }

        System.out.println("=== ProfilePageController.initialize() END ===");
    }

    private void loadUserProfile() {
        final String username = loggedInUsername != null ? loggedInUsername.trim() : "";
        final Connection connection = databaseConnection;
        final int requestVersion = ++profileLoadVersion;

        System.out.println("=== loadUserProfile() START ===");
        System.out.println("Username: " + username);
        System.out.println("Database connection null: " + (connection == null));

        if (username.isEmpty() || connection == null) {
            Platform.runLater(this::setDefaultValues);
            return;
        }

        profileLoader.execute(() -> {
            try {
                ProfileSnapshot snapshot = fetchProfileSnapshot(connection, username);
                Platform.runLater(() -> {
                    if (requestVersion != profileLoadVersion) {
                        return;
                    }
                    if (snapshot == null) {
                        setDefaultValues();
                    } else {
                        applyProfileSnapshot(snapshot);
                    }
                });
            } catch (SQLException e) {
                System.err.println("ERROR: SQL Exception while loading user profile: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    if (requestVersion == profileLoadVersion) {
                        setDefaultValues();
                    }
                });
            }
        });
    }

    private void setDefaultValues() {
        nameLabel.setText("N/A");
        nameLabel1.setText("N/A");
        memberIdLabel.setText("N/A");
        emailLabel.setText("N/A");
        phoneLabel.setText("N/A");
        updateBalanceLabel(0);
        memberTypeLabel.setText("N/A");
    }

    public String getLoggedInUsername() {
        return loggedInUsername;
    }

    public void refreshProfile() {
        System.out.println("Manually refreshing profile...");
        loadUserProfile();
    }

    private void loadChartData() {
        System.out.println("=== loadChartData() START ===");
        if (databaseConnection == null || loggedInUsername == null) {
            System.err.println("Cannot load charts: database connection or username is null");
            return;
        }
        try {
            int memberId = getMemberId(loggedInUsername);
            if (memberId == -1) {
                System.err.println("Could not find member ID for username: " + loggedInUsername);
                return;
            }
            loadPieChartData(memberId);
            loadBarChartData(memberId);
        } catch (Exception e) {
            System.err.println("Error loading chart data: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("=== loadChartData() END ===");
    }

    private int getMemberId(String username) {
        String sql = "SELECT member_id FROM internet_cafe.member WHERE member_name = ?";
        try (PreparedStatement stmt = databaseConnection.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("member_id");
            }
        } catch (SQLException e) {
            System.err.println("Error getting member ID: " + e.getMessage());
        }
        return -1;
    }

    private void loadPieChartData(int memberId) {
        String playSql = "SELECT COALESCE(SUM(session_total_cost), 0) AS total_play_cost " +
                        "FROM internet_cafe.session " +
                        "WHERE member_id = ?";
        String foodSql = "SELECT COALESCE(SUM(s.sale_total_cost), 0) AS total_food_cost " +
                        "FROM internet_cafe.sale s " +
                        "INNER JOIN internet_cafe.session ses ON s.session_id = ses.session_id " +
                        "WHERE ses.member_id = ?";

        try {
            double playCost = 0;
            double foodCost = 0;

            try (PreparedStatement playStmt = databaseConnection.prepareStatement(playSql)) {
                playStmt.setInt(1, memberId);
                ResultSet playRs = playStmt.executeQuery();
                if (playRs.next()) {
                    playCost = playRs.getDouble("total_play_cost");
                }
            }

            try (PreparedStatement foodStmt = databaseConnection.prepareStatement(foodSql)) {
                foodStmt.setInt(1, memberId);
                ResultSet foodRs = foodStmt.executeQuery();
                if (foodRs.next()) {
                    foodCost = foodRs.getDouble("total_food_cost");
                }
            }

            spendingPieChart.getData().clear();
            if (playCost > 0) {
                spendingPieChart.getData().add(new PieChart.Data("Play Time", playCost));
            }
            if (foodCost > 0) {
                spendingPieChart.getData().add(new PieChart.Data("Food & Drinks", foodCost));
            }
            applyPieChartStyling();
            PixelMotion.animateChartEntrance(spendingPieChart, true);
            System.out.println("Pie chart loaded: Play=" + playCost + ", Food=" + foodCost);
        } catch (SQLException e) {
            System.err.println("Error loading pie chart data: " + e.getMessage());
        }
    }

    private void loadBarChartData(int memberId) {
        String playSql = "SELECT DATE(session_date) AS activity_date, SUM(session_total_cost) AS play_cost " +
                        "FROM internet_cafe.session WHERE member_id = ? GROUP BY DATE(session_date)";
        String foodSql = "SELECT DATE(s.sale_date) AS activity_date, SUM(s.sale_total_cost) AS food_cost " +
                        "FROM internet_cafe.sale s " +
                        "INNER JOIN internet_cafe.session ses ON s.session_id = ses.session_id " +
                        "WHERE ses.member_id = ? GROUP BY DATE(s.sale_date)";

        try {
            dailySpendingBarChart.getData().clear();
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Daily Spending");

            Map<String, Double> dailyTotals = new HashMap<>();
            double monthlyTotal = 0;

            try (PreparedStatement playStmt = databaseConnection.prepareStatement(playSql)) {
                playStmt.setInt(1, memberId);
                ResultSet playRs = playStmt.executeQuery();
                while (playRs.next()) {
                    String date = playRs.getString("activity_date");
                    double playCost = playRs.getDouble("play_cost");
                    dailyTotals.merge(date, playCost, Double::sum);
                    monthlyTotal += playCost;
                }
            }

            try (PreparedStatement foodStmt = databaseConnection.prepareStatement(foodSql)) {
                foodStmt.setInt(1, memberId);
                ResultSet foodRs = foodStmt.executeQuery();
                while (foodRs.next()) {
                    String date = foodRs.getString("activity_date");
                    double foodCost = foodRs.getDouble("food_cost");
                    dailyTotals.merge(date, foodCost, Double::sum);
                    monthlyTotal += foodCost;
                }
            }

            // Always show the last 7 days for consistent bar width
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(6);
            DateTimeFormatter dbFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                String dbKey = date.format(dbFormatter);
                double totalCost = dailyTotals.getOrDefault(dbKey, 0.0);
                String formattedLabel = dbKey.substring(5, 10);
                series.getData().add(new XYChart.Data<>(formattedLabel, totalCost));
            }

            dailySpendingBarChart.getData().add(series);
            monthlyTotalLabel.setText(String.format("%.0f P", monthlyTotal));

            applyBarChartStyling();
            PixelMotion.animateChartEntrance(dailySpendingBarChart, false);
        } catch (SQLException e) {
            System.err.println("Error loading bar chart data: " + e.getMessage());
        }
    }

    private void applyPieChartStyling() {
        spendingPieChart.setStyle("-fx-background-color: transparent;");
        spendingPieChart.setLegendSide(javafx.geometry.Side.BOTTOM);
        spendingPieChart.setLabelsVisible(true);

        for (PieChart.Data data : spendingPieChart.getData()) {
            applyPieSliceColor(data);
        }

        Platform.runLater(this::refreshPieLegendColors);
    }

    private void applyPieSliceColor(PieChart.Data data) {
        String sliceColor = getPieSliceColor(data.getName());
        if (data.getNode() != null) {
            data.getNode().setStyle("-fx-pie-color: " + sliceColor + ";");
        }
        data.nodeProperty().addListener((observable, oldNode, newNode) -> {
            if (newNode != null) {
                newNode.setStyle("-fx-pie-color: " + sliceColor + ";");
                Platform.runLater(this::refreshPieLegendColors);
            }
        });
    }

    private void refreshPieLegendColors() {
        spendingPieChart.applyCss();
        spendingPieChart.layout();

        for (Node legendNode : spendingPieChart.lookupAll(".chart-legend-item")) {
            if (!(legendNode instanceof Label legendLabel)) {
                continue;
            }

            Node legendSymbol = legendLabel.getGraphic();
            if (legendSymbol == null) {
                continue;
            }

            legendSymbol.setStyle(
                "-fx-background-color: " + getPieSliceColor(legendLabel.getText()) + ";" +
                "-fx-background-radius: 999;"
            );
        }
    }

    private String getPieSliceColor(String sliceName) {
        if ("Play Time".equals(sliceName)) {
            return PLAY_TIME_COLOR;
        }
        if ("Food & Drinks".equals(sliceName)) {
            return FOOD_AND_DRINKS_COLOR;
        }
        return FALLBACK_CHART_COLOR;
    }

    private void applyBarChartStyling() {
        dailySpendingBarChart.setAnimated(false);
        dailySpendingBarChart.setVerticalGridLinesVisible(false);
        dailySpendingBarChart.setHorizontalGridLinesVisible(true);

        dailySpendingBarChart.setCategoryGap(25);
        dailySpendingBarChart.setBarGap(0);

        dateAxis.setAnimated(false);
        amountAxis.setAnimated(false);
        amountAxis.setTickUnit(1);
        amountAxis.setMinorTickVisible(false);

        dateAxis.setStyle("-fx-tick-label-fill: #94a3b8; -fx-label-fill: #cbd5e1;");
        amountAxis.setStyle("-fx-tick-label-fill: #94a3b8; -fx-label-fill: #cbd5e1;");

        for (XYChart.Series<String, Number> series : dailySpendingBarChart.getData()) {
            for (XYChart.Data<String, Number> data : series.getData()) {
                applyBarNodeStyle(data);
            }
        }
    }

    private void applyBarNodeStyle(XYChart.Data<String, Number> data) {
        String modernBarStyle =
            "-fx-background-color: linear-gradient(to top, rgba(11,18,43,0.92) 0%, rgba(24,215,255,0.95) 18%, rgba(125,92,255,0.98) 100%);" +
            "-fx-background-radius: 6 6 0 0;" +
            "-fx-effect: dropshadow(gaussian, rgba(24,215,255,0.38), 10, 0.2, 0, 4);";

        if (data.getNode() != null) {
            data.getNode().setStyle(modernBarStyle);
            return;
        }

        data.nodeProperty().addListener((observable, oldNode, newNode) -> {
            if (newNode != null) {
                newNode.setStyle(modernBarStyle);
            }
        });
    }

    public void testProfileWithUsername(String username) {
        System.out.println("Testing profile with username: " + username);
        this.loggedInUsername = username;
        loadUserProfile();
    }

    public void setHomeController(HomeController homeController) {
        this.homeController = homeController;
        updateBalanceLabel(homeController != null ? homeController.currentPointsProperty().get() : 0);
    }

    private void updateBalanceLabel(int fallbackPoints) {
        if (balenceLabel == null) {
            return;
        }

        if (balenceLabel.textProperty().isBound()) {
            balenceLabel.textProperty().unbind();
        }

        int displayPoints = homeController != null
            ? homeController.currentPointsProperty().get()
            : fallbackPoints;

        balenceLabel.setText(displayPoints + " P");

        if (homeController != null) {
            balenceLabel.textProperty().bind(
                homeController.currentPointsProperty().asString().concat(" P")
            );
        }
    }

    private ProfileSnapshot fetchProfileSnapshot(Connection connection, String username) throws SQLException {
        String profileSql = "SELECT m.member_id, m.member_name, m.email, m.phone, " +
                "m.point, mt.member_type_name " +
                "FROM internet_cafe.member m " +
                "INNER JOIN internet_cafe.member_type mt ON m.member_type_id = mt.member_type_id " +
                "WHERE m.member_name = ?";

        try (PreparedStatement preparedStatement = connection.prepareStatement(profileSql)) {
            preparedStatement.setString(1, username);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }

                ProfileSnapshot snapshot = new ProfileSnapshot();
                snapshot.memberId = resultSet.getInt("member_id");
                snapshot.memberName = resultSet.getString("member_name");
                snapshot.email = resultSet.getString("email");
                snapshot.phone = resultSet.getString("phone");
                snapshot.points = resultSet.getInt("point");
                snapshot.memberTypeName = resultSet.getString("member_type_name");

                fillPieData(connection, snapshot);
                fillBarData(connection, snapshot);
                return snapshot;
            }
        }
    }

    private void fillPieData(Connection connection, ProfileSnapshot snapshot) throws SQLException {
        String totalSql =
            "SELECT COALESCE(SUM(session_total_cost), 0) AS total_spent " +
            "FROM internet_cafe.session WHERE member_id = ?";

        String foodSql =
            "SELECT COALESCE(SUM(s.sale_total_cost), 0) AS total_food " +
            "FROM internet_cafe.sale s " +
            "INNER JOIN internet_cafe.session ses ON s.session_id = ses.session_id " +
            "WHERE ses.member_id = ?";

        try (PreparedStatement totalStmt = connection.prepareStatement(totalSql)) {
            totalStmt.setInt(1, snapshot.memberId);
            try (ResultSet totalRs = totalStmt.executeQuery()) {
                if (totalRs.next()) {
                    double totalSpent = totalRs.getDouble("total_spent");

                    double foodSpent = 0;
                    try (PreparedStatement foodStmt = connection.prepareStatement(foodSql)) {
                        foodStmt.setInt(1, snapshot.memberId);
                        try (ResultSet foodRs = foodStmt.executeQuery()) {
                            if (foodRs.next()) {
                                foodSpent = foodRs.getDouble("total_food");
                            }
                        }
                    }

                    snapshot.playCost = totalSpent - foodSpent;
                    snapshot.foodCost = foodSpent;
                }
            }
        }
    }

    private void fillBarData(Connection connection, ProfileSnapshot snapshot) throws SQLException {
        String playSql = "SELECT DATE(session_date) AS activity_date, SUM(session_total_cost) AS daily_cost " +
                        "FROM internet_cafe.session WHERE member_id = ? GROUP BY DATE(session_date)";
        String foodSql = "SELECT DATE(s.sale_date) AS activity_date, SUM(s.sale_total_cost) AS food_cost " +
                        "FROM internet_cafe.sale s INNER JOIN internet_cafe.session ses ON s.session_id = ses.session_id " +
                        "WHERE ses.member_id = ? GROUP BY DATE(s.sale_date)";

        Map<String, Double> dailyTotals = new HashMap<>();

        try (PreparedStatement playStmt = connection.prepareStatement(playSql)) {
            playStmt.setInt(1, snapshot.memberId);
            try (ResultSet playRs = playStmt.executeQuery()) {
                while (playRs.next()) {
                    String date = playRs.getString("activity_date");
                    double cost = playRs.getDouble("daily_cost");
                    dailyTotals.merge(date, cost, Double::sum);
                    snapshot.monthlyTotal += cost;
                }
            }
        }

        try (PreparedStatement foodStmt = connection.prepareStatement(foodSql)) {
            foodStmt.setInt(1, snapshot.memberId);
            try (ResultSet foodRs = foodStmt.executeQuery()) {
                while (foodRs.next()) {
                    String date = foodRs.getString("activity_date");
                    double cost = foodRs.getDouble("food_cost");
                    dailyTotals.merge(date, cost, Double::sum);
                    snapshot.monthlyTotal += cost;
                }
            }
        }

        // Always show the last 7 days for consistent bar width
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(6);
        DateTimeFormatter dbFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            String dbKey = date.format(dbFormatter);
            double totalCost = dailyTotals.getOrDefault(dbKey, 0.0);
            String formattedLabel = dbKey.substring(5, 10);
            snapshot.dailySpending.add(new DailySpendingPoint(formattedLabel, totalCost));
        }
    }

    private void applyProfileSnapshot(ProfileSnapshot snapshot) {
        nameLabel1.setText(snapshot.memberName);
        nameLabel.setText(snapshot.memberName);
        memberIdLabel.setText(String.valueOf(snapshot.memberId));
        emailLabel.setText(snapshot.email);
        phoneLabel.setText(snapshot.phone);
        updateBalanceLabel(snapshot.points);
        memberTypeLabel.setText(snapshot.memberTypeName);

        spendingPieChart.getData().clear();
        if (snapshot.playCost > 0) {
            spendingPieChart.getData().add(new PieChart.Data("Play Time", snapshot.playCost));
        }
        if (snapshot.foodCost > 0) {
            spendingPieChart.getData().add(new PieChart.Data("Food & Drinks", snapshot.foodCost));
        }
        applyPieChartStyling();
        PixelMotion.animateChartEntrance(spendingPieChart, true);

        dailySpendingBarChart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Daily Spending");
        for (DailySpendingPoint point : snapshot.dailySpending) {
            series.getData().add(new XYChart.Data<>(point.dateLabel, point.amount));
        }
        dailySpendingBarChart.getData().add(series);
        monthlyTotalLabel.setText(String.format("%.0f P", snapshot.monthlyTotal));

        applyBarChartStyling();
        PixelMotion.animateChartEntrance(dailySpendingBarChart, false);
    }

    private static final class ProfileSnapshot {
        private int memberId;
        private String memberName;
        private String email;
        private String phone;
        private int points;
        private String memberTypeName;
        private double playCost;
        private double foodCost;
        private double monthlyTotal;
        private final List<DailySpendingPoint> dailySpending = new ArrayList<>();
    }

    private static final class DailySpendingPoint {
        private final String dateLabel;
        private final double amount;

        private DailySpendingPoint(String dateLabel, double amount) {
            this.dateLabel = dateLabel;
            this.amount = amount;
        }
    }

    @Override
    public void replayEntranceAnimations() {
        AnimationUtil.neonCardEntrance(profileCard, "#ba66ff", 0.06);
        AnimationUtil.neonCardEntrance(accountDetailsCard, "#59a6ff", 0.16);
        AnimationUtil.neonCardEntrance(spendingCard, "#2dd4ff", 0.26);
        AnimationUtil.neonCardEntrance(dailyCard, "#8b5cf6", 0.36);
    }

    @Override
    public void preHideForEntrance() {
        profileCard.setOpacity(0);
        accountDetailsCard.setOpacity(0);
        spendingCard.setOpacity(0);
        dailyCard.setOpacity(0);
    }

    public void cleanup() {
        profileLoader.shutdownNow();
    }
}