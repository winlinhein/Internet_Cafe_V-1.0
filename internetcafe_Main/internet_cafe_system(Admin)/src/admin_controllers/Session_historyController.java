package admin_controllers;

import database.DatabaseConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ResourceBundle;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import member_controllers.ClientInterface;

public class Session_historyController implements Initializable, InitializableController {

    @FXML
    private BorderPane root;
    @FXML
    private StackPane contentStack;
    @FXML
    private Button refreshButton;
    @FXML
    private TextField searchField;
    @FXML
    private DatePicker fromDatePicker;
    @FXML
    private DatePicker toDatePicker;
    @FXML
    private Button filterButton;
    @FXML
    private Button clearButton;
    @FXML
    private TableView<SessionRow> sessionTable;
    @FXML
    private TableColumn<SessionRow, String> sessionIdColumn;
    @FXML
    private TableColumn<SessionRow, String> usernameColumn;
    @FXML
    private TableColumn<SessionRow, String> pcColumn;
    @FXML
    private TableColumn<SessionRow, String> dateColumn;
    @FXML
    private TableColumn<SessionRow, String> durationColumn;
    @FXML
    private TableColumn<SessionRow, String> costColumn;
    @FXML
    private TableColumn<SessionRow, String> boughtProductsColumn;

    private final ObservableList<SessionRow> masterSessions = FXCollections.observableArrayList();
    private FilteredList<SessionRow> filteredSessions;
    private Connection con;
    private ServerInterface server;
    private ClientInterface client;
    private String saleDetailQtyColumn;
    private boolean isDateFilterActive = false;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String SESSIONS_QUERY_TEMPLATE = """
            SELECT
                s.session_id,
                COALESCE(m.member_name, 'Unknown') AS username,
                CASE
                    WHEN c.model IS NOT NULL AND TRIM(c.model) <> '' THEN c.model
                    ELSE CONCAT('PC-', LPAD(s.computer_id, 2, '0'))
                END AS pc_display,
                DATE(s.session_date) AS session_day,
                s.total_time,
                COALESCE(s.session_total_cost, 0) AS session_total_cost,
                COALESCE(products.bought_products, 'No product') AS bought_products
            FROM internet_cafe.session s
            LEFT JOIN internet_cafe.member m
                ON m.member_id = s.member_id
            LEFT JOIN internet_cafe.computer c
                ON c.computer_id = s.computer_id
            LEFT JOIN (
                SELECT
                    session_products.session_id,
                    GROUP_CONCAT(
                        CONCAT(p.product_name, ' x', session_products.total_qty)
                        ORDER BY p.product_name
                        SEPARATOR ', '
                    ) AS bought_products
                FROM (
                    SELECT
                        sa.session_id,
                        sd.product_id,
                        SUM(sd.%s) AS total_qty
                    FROM internet_cafe.sale sa
                    INNER JOIN internet_cafe.sale_detail sd
                        ON sd.sale_id = sa.sale_id
                    GROUP BY sa.session_id, sd.product_id
                ) session_products
                INNER JOIN internet_cafe.product p
                    ON p.product_id = session_products.product_id
                GROUP BY session_products.session_id
            ) products
                ON products.session_id = s.session_id
            %s
            ORDER BY s.session_date DESC, s.session_id DESC %s
                                                          
            """;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        configureTableColumns();
        setupFiltering();
        setupActions();
        refreshData();

        String popupCss = getClass().getResource("/css/pixel_admin_reactive.css").toExternalForm();
        AdminDatePickerUtils.configurePastAndPresentOnly(fromDatePicker, popupCss);
        AdminDatePickerUtils.configurePastAndPresentOnly(toDatePicker, popupCss);

        fromDatePicker.valueProperty().addListener((obs, oldFrom, newFrom) -> {
            applyToDateConstraints();
            if (newFrom != null && toDatePicker.getValue() != null) {
                if (toDatePicker.getValue().isBefore(newFrom)) {
                    toDatePicker.setValue(null);
                }
            }
        });

        toDatePicker.valueProperty().addListener((obs, oldTo, newTo) -> {
            applyFromDateConstraints();
            if (newTo != null && fromDatePicker.getValue() != null) {
                if (fromDatePicker.getValue().isAfter(newTo)) {
                    fromDatePicker.setValue(null);
                }
            }
        });
    }

    private void applyFromDateConstraints() {
        LocalDate to = toDatePicker.getValue();
        LocalDate today = LocalDate.now();

        fromDatePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) return;

                if (date.isAfter(today)) {
                    setDisable(true);
                    setStyle("-fx-background-color: #2a2a2a;");
                } else if (to != null && date.isAfter(to)) {
                    setDisable(true);
                    setStyle("-fx-background-color: #2a2a2a;");
                } else {
                    setDisable(false);
                    setStyle("");
                }
            }
        });
    }

    private void applyToDateConstraints() {
        LocalDate from = fromDatePicker.getValue();
        LocalDate today = LocalDate.now();

        toDatePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) return;

                if (date.isAfter(today)) {
                    setDisable(true);
                    setStyle("-fx-background-color: #2a2a2a;");
                } else if (from != null && date.isBefore(from)) {
                    setDisable(true);
                    setStyle("-fx-background-color: #2a2a2a;");
                } else {
                    setDisable(false);
                    setStyle("");
                }
            }
        });
    }

    private void configureTableColumns() {
        sessionIdColumn.setCellValueFactory(data -> data.getValue().sessionIdProperty());
        usernameColumn.setCellValueFactory(data -> data.getValue().usernameProperty());
        pcColumn.setCellValueFactory(data -> data.getValue().pcProperty());
        dateColumn.setCellValueFactory(data -> data.getValue().dateProperty());
        durationColumn.setCellValueFactory(data -> data.getValue().durationProperty());
        costColumn.setCellValueFactory(data -> data.getValue().costProperty());
        boughtProductsColumn.setCellValueFactory(data -> data.getValue().boughtProductsProperty());
    }

    private void setupFiltering() {
        filteredSessions = new FilteredList<>(masterSessions, item -> true);
        SortedList<SessionRow> sorted = new SortedList<>(filteredSessions);
        sorted.comparatorProperty().bind(sessionTable.comparatorProperty());
        sessionTable.setItems(sorted);
        applyFilters();
    }

    private void setupActions() {
        refreshButton.setOnAction(event -> refreshData());
        filterButton.setOnAction(event -> applyDateFilter());
        clearButton.setOnAction(event -> clearFilters());
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters());
    }

    private void refreshData() {
        Task<ObservableList<SessionRow>> task = new Task<>() {
            @Override
            protected ObservableList<SessionRow> call() throws Exception {
                return fetchSessionsFromDatabase();
            }
        };

        task.setOnSucceeded(event -> {
            masterSessions.setAll(task.getValue());
            applyFilters();
        });

        task.setOnFailed(event -> {
            Throwable error = task.getException();
            System.err.println("Failed to load session history: " + (error != null ? error.getMessage() : "Unknown error"));
            if (error != null) {
                error.printStackTrace();
            }
            masterSessions.clear();
            applyFilters();
        });

        Thread loadThread = new Thread(task, "session-history-loader");
        loadThread.setDaemon(true);
        loadThread.start();
    }

    private void clearFilters() {
        searchField.clear();
        fromDatePicker.setValue(null);
        toDatePicker.setValue(null);
        isDateFilterActive = false;
        refreshData();
    }

    private void applyDateFilter() {
        LocalDate fromDate = fromDatePicker.getValue();
        LocalDate toDate = toDatePicker.getValue();
        
        if (fromDate != null || toDate != null) {
            isDateFilterActive = true;
            refreshData();
        } else {
            applyFilters();
        }
    }

    private void applyFilters() {
        final String keyword = searchField.getText() == null
                ? ""
                : searchField.getText().trim().toLowerCase(Locale.ROOT);

        filteredSessions.setPredicate(session -> {
            boolean matchesKeyword = keyword.isEmpty()
                    || containsIgnoreCase(session.getSessionId(), keyword)
                    || containsIgnoreCase(session.getUsername(), keyword)
                    || containsIgnoreCase(session.getPc(), keyword)
                    || containsIgnoreCase(session.getBoughtProducts(), keyword);

            return matchesKeyword;
        });
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private ObservableList<SessionRow> fetchSessionsFromDatabase() throws SQLException {
        ObservableList<SessionRow> sessions = FXCollections.observableArrayList();
        Connection connection = getValidConnection();

        synchronized (connection) {
            String qtyColumn = resolveSaleDetailQtyColumn(connection);
            
            String whereClause = "";
            String limitClause = "LIMIT 100";
            
            if (isDateFilterActive) {
                LocalDate fromDate = fromDatePicker.getValue();
                LocalDate toDate = toDatePicker.getValue();
                
                if (fromDate != null || toDate != null) {
                    whereClause = "WHERE ";
                    if (fromDate != null && toDate != null) {
                        whereClause += "DATE(s.session_date) BETWEEN ? AND ?";
                    } else if (fromDate != null) {
                        whereClause += "DATE(s.session_date) >= ?";
                    } else {
                        whereClause += "DATE(s.session_date) <= ?";
                    }
                    limitClause = "";
                }
            }
            
            String sessionsQuery = String.format(Locale.ROOT, SESSIONS_QUERY_TEMPLATE, qtyColumn, whereClause, limitClause);
            
            try (PreparedStatement pst = connection.prepareStatement(sessionsQuery)) {
                if (isDateFilterActive && !whereClause.isEmpty()) {
                    LocalDate fromDate = fromDatePicker.getValue();
                    LocalDate toDate = toDatePicker.getValue();
                    
                    if (fromDate != null && toDate != null) {
                        pst.setDate(1, Date.valueOf(fromDate));
                        pst.setDate(2, Date.valueOf(toDate));
                    } else if (fromDate != null) {
                        pst.setDate(1, Date.valueOf(fromDate));
                    } else {
                        pst.setDate(1, Date.valueOf(toDate));
                    }
                }
                
                try (ResultSet rs = pst.executeQuery()) {
                    while (rs.next()) {
                        sessions.add(mapRow(rs));
                    }
                }
            }
        }
        return sessions;
    }

    private String resolveSaleDetailQtyColumn(Connection connection) {
        if (saleDetailQtyColumn != null) {
            return saleDetailQtyColumn;
        }

        String metadataQuery = """
                SELECT COLUMN_NAME
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = 'internet_cafe'
                  AND TABLE_NAME = 'sale_detail'
                  AND COLUMN_NAME IN ('qty', 'quantity')
                ORDER BY CASE WHEN COLUMN_NAME = 'qty' THEN 0 ELSE 1 END
                LIMIT 1
                """;

        try (PreparedStatement pst = connection.prepareStatement(metadataQuery);
             ResultSet rs = pst.executeQuery()) {
            if (rs.next()) {
                saleDetailQtyColumn = rs.getString("COLUMN_NAME");
            }
        } catch (SQLException e) {
            System.err.println("Could not resolve sale_detail quantity column, defaulting to qty: " + e.getMessage());
        }

        if (saleDetailQtyColumn == null || saleDetailQtyColumn.isBlank()) {
            saleDetailQtyColumn = "qty";
        }
        return saleDetailQtyColumn;
    }

    private SessionRow mapRow(ResultSet rs) throws SQLException {
        int rawSessionId = rs.getInt("session_id");
        String sessionIdText = "S-" + rawSessionId;
        String username = rs.getString("username");
        String pc = rs.getString("pc_display");

        Date sqlSessionDate = rs.getDate("session_day");
        LocalDate sessionDate = sqlSessionDate != null ? sqlSessionDate.toLocalDate() : null;
        String dateText = sessionDate != null ? DATE_FORMATTER.format(sessionDate) : "-";

        String durationText = formatDuration(rs.getString("total_time"));

        long cost = rs.getLong("session_total_cost");
        String costText = String.format("P %,d", cost);

        String boughtProducts = rs.getString("bought_products");
        if (boughtProducts == null || boughtProducts.isBlank()) {
            boughtProducts = "No product";
        }

        return new SessionRow(
                sessionIdText,
                username,
                pc,
                dateText,
                durationText,
                costText,
                boughtProducts,
                sessionDate
        );
    }

    private String formatDuration(String totalTimeText) {
        if (totalTimeText == null || totalTimeText.isBlank()) {
            return "0h 00m";
        }

        String normalized = totalTimeText.trim();
        if (normalized.contains(" ")) {
            normalized = normalized.substring(normalized.lastIndexOf(' ') + 1);
        }

        String[] parts = normalized.split(":");
        if (parts.length >= 2) {
            int hours = parseIntSafe(parts[0]);
            int minutes = parseIntSafe(parts[1]);
            return String.format("%dh %02dm", Math.max(0, hours), Math.max(0, minutes));
        }
        return normalized;
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private synchronized Connection getValidConnection() throws SQLException {
        if (con == null || con.isClosed()) {
            con = DatabaseConnection.connectDB();
        }
        if (con == null) {
            throw new SQLException("Database connection is not available.");
        }
        return con;
    }

    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
    }

    @Override
    public void setClient(ClientInterface client) {
        this.client = client;
    }

    @Override
    public void setDatabaseConnection(Connection con) {
        this.con = con;
        refreshData();
    }

    public static class SessionRow {

        private final StringProperty sessionId;
        private final StringProperty username;
        private final StringProperty pc;
        private final StringProperty date;
        private final StringProperty duration;
        private final StringProperty cost;
        private final StringProperty boughtProducts;
        private final LocalDate sessionDate;

        public SessionRow(
                String sessionId,
                String username,
                String pc,
                String date,
                String duration,
                String cost,
                String boughtProducts,
                LocalDate sessionDate
        ) {
            this.sessionId = new SimpleStringProperty(sessionId);
            this.username = new SimpleStringProperty(username);
            this.pc = new SimpleStringProperty(pc);
            this.date = new SimpleStringProperty(date);
            this.duration = new SimpleStringProperty(duration);
            this.cost = new SimpleStringProperty(cost);
            this.boughtProducts = new SimpleStringProperty(boughtProducts);
            this.sessionDate = sessionDate;
        }

        public String getSessionId() {
            return sessionId.get();
        }

        public StringProperty sessionIdProperty() {
            return sessionId;
        }

        public String getUsername() {
            return username.get();
        }

        public StringProperty usernameProperty() {
            return username;
        }

        public String getPc() {
            return pc.get();
        }

        public StringProperty pcProperty() {
            return pc;
        }

        public StringProperty dateProperty() {
            return date;
        }

        public StringProperty durationProperty() {
            return duration;
        }

        public StringProperty costProperty() {
            return cost;
        }

        public String getBoughtProducts() {
            return boughtProducts.get();
        }

        public StringProperty boughtProductsProperty() {
            return boughtProducts;
        }

        public LocalDate getSessionDate() {
            return sessionDate;
        }
    }
}