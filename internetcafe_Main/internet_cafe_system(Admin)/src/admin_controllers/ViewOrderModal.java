package admin_controllers;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ResourceBundle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import animation.PixelMotion;
import java.util.TimeZone;

public class ViewOrderModal implements Initializable {

    @FXML private Label orderViewId;
    @FXML private Label orderViewStatus;
    @FXML private Label orderViewDate;
    @FXML private Label orderViewCustomer;
    @FXML private Label orderViewItemCount;
    @FXML private Label orderViewTotalValue;
    @FXML private TableView<OrderItemRowData> orderItemsTable;
    @FXML private TableColumn<OrderItemRowData, Integer> orderNo;
    @FXML private TableColumn<OrderItemRowData, String> orderItem;
    @FXML private TableColumn<OrderItemRowData, Integer> orderQty;
    @FXML private TableColumn<OrderItemRowData, Double> orderUnitprize;
    @FXML private TableColumn<OrderItemRowData, Double> orderTotalprize;
    @FXML private Button closeButton;
    @FXML private Button completeButton;

    private Connection connection;
    private int saleId;
    private Runnable onComplete;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        orderNo.setCellValueFactory(new PropertyValueFactory<>("rowNumber"));
        orderItem.setCellValueFactory(new PropertyValueFactory<>("productName"));
        orderQty.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        orderUnitprize.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        orderTotalprize.setCellValueFactory(new PropertyValueFactory<>("totalPrice"));

        orderUnitprize.setCellFactory(tc -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty || price == null ? "" : String.format("P %.2f", price));
            }
        });
        orderTotalprize.setCellFactory(tc -> new javafx.scene.control.TableCell<>() {
            @Override
            protected void updateItem(Double price, boolean empty) {
                super.updateItem(price, empty);
                setText(empty || price == null ? "" : String.format("P %.2f", price));
            }
        });
    }

    public void setSaleId(int saleId, Connection con, Runnable onComplete) {
        this.saleId = saleId;
        this.connection = con;
        this.onComplete = onComplete;
        loadSaleData();
    }

    private void loadSaleData() {
        if (connection == null) {
            System.err.println("ViewOrderModal: Database connection is null");
            return;
        }

        String saleQuery = """
            SELECT s.sale_date, s.sale_total_cost, s.status,
                   m.member_name, c.model AS pc_name
            FROM internet_cafe.sale s
            LEFT JOIN internet_cafe.session sess ON s.session_id = sess.session_id
            LEFT JOIN internet_cafe.member m ON sess.member_id = m.member_id
            LEFT JOIN internet_cafe.computer c ON sess.computer_id = c.computer_id
            WHERE s.sale_id = ?
            """;

        String detailsQuery = """
            SELECT sd.qty, p.unit_price, p.product_name
            FROM internet_cafe.sale_detail sd
            JOIN internet_cafe.product p ON sd.product_id = p.product_id
            WHERE sd.sale_id = ?
            ORDER BY sd.sale_detail_id
            """;

        try (PreparedStatement saleStmt = connection.prepareStatement(saleQuery);
             PreparedStatement detailsStmt = connection.prepareStatement(detailsQuery)) {

            saleStmt.setInt(1, saleId);
            ResultSet saleRs = saleStmt.executeQuery();
            if (saleRs.next()) {
                orderViewId.setText("ORD-" + saleId);

                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd");
                orderViewDate.setText(sdf.format(saleRs.getTimestamp("sale_date")));

                String memberName = saleRs.getString("member_name");
                String pcName = saleRs.getString("pc_name");
                String customerText = (memberName != null ? memberName : "Guest")
                        + (pcName != null && !pcName.isEmpty() ? " · " + pcName : "");
                orderViewCustomer.setText(customerText);

                double total = saleRs.getDouble("sale_total_cost");
                orderViewTotalValue.setText(String.format("P %.2f", total));

                String status = saleRs.getString("status");
                applyOrderStatus(status);
            }
            saleRs.close();

            detailsStmt.setInt(1, saleId);
            ResultSet detailsRs = detailsStmt.executeQuery();
            ObservableList<OrderItemRowData> items = FXCollections.observableArrayList();
            int rowNum = 1;
            while (detailsRs.next()) {
                items.add(new OrderItemRowData(
                        rowNum++,
                        detailsRs.getString("product_name"),
                        detailsRs.getInt("qty"),
                        detailsRs.getDouble("unit_price")
                ));
            }
            orderItemsTable.setItems(items);
            if (orderViewItemCount != null) {
                int size = items.size();
                orderViewItemCount.setText(size == 1 ? "1 item" : size + " items");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleClose(ActionEvent event) {
        PixelMotion.closeOverlayFrom(closeButton);
    }

    @FXML
    private void handleComplete(ActionEvent event) {
        String update = "UPDATE internet_cafe.sale SET status = 'completed' WHERE sale_id = ?";
        try (PreparedStatement pst = connection.prepareStatement(update)) {
            pst.setInt(1, saleId);
            pst.executeUpdate();
            applyOrderStatus("completed");
            if (onComplete != null) onComplete.run();
            PixelMotion.closeOverlayFrom(completeButton);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void applyOrderStatus(String rawStatus) {
        String normalized = normalizeStatus(rawStatus);
        orderViewStatus.setText(prettyStatus(normalized));

        orderViewStatus.getStyleClass().removeAll(
                "order-status-pending",
                "order-status-processing",
                "order-status-completed",
                "order-status-cancelled",
                "order-status-unknown"
        );
        orderViewStatus.getStyleClass().add("order-status-" + normalized);

        completeButton.getStyleClass().removeAll(
                "order-complete-btn-ready",
                "order-complete-btn-done",
                "order-complete-btn-cancelled"
        );

        switch (normalized) {
            case "completed":
                completeButton.setText("Completed");
                completeButton.setDisable(true);
                completeButton.getStyleClass().add("order-complete-btn-done");
                break;
            case "cancelled":
                completeButton.setText("Cancelled");
                completeButton.setDisable(true);
                completeButton.getStyleClass().add("order-complete-btn-cancelled");
                break;
            default:
                completeButton.setText("Mark Complete");
                completeButton.setDisable(false);
                completeButton.getStyleClass().add("order-complete-btn-ready");
                break;
        }
    }

    private String normalizeStatus(String rawStatus) {
        if (rawStatus == null) return "unknown";
        String status = rawStatus.trim().toLowerCase();
        if (status.isEmpty()) return "unknown";
        if (status.equals("completed") || status.equals("complete") || status.equals("done")) return "completed";
        if (status.equals("pending") || status.equals("new") || status.equals("waiting")) return "pending";
        if (status.equals("processing") || status.equals("in progress") || status.equals("in_progress")
                || status.equals("inprogress") || status.equals("preparing")) return "processing";
        if (status.equals("cancelled") || status.equals("canceled")) return "cancelled";
        return "unknown";
    }

    private String prettyStatus(String normalizedStatus) {
        switch (normalizedStatus) {
            case "completed": return "Completed";
            case "pending": return "Pending";
            case "processing": return "Processing";
            case "cancelled": return "Cancelled";
            default: return "Unknown";
        }
    }

    public static class OrderItemRowData {
        private final int rowNumber;
        private final String productName;
        private final int quantity;
        private final double unitPrice;

        public OrderItemRowData(int rowNumber, String productName, int quantity, double unitPrice) {
            this.rowNumber = rowNumber;
            this.productName = productName;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public int getRowNumber() { return rowNumber; }
        public String getProductName() { return productName; }
        public int getQuantity() { return quantity; }
        public double getUnitPrice() { return unitPrice; }
        public double getTotalPrice() { return unitPrice * quantity; }
    }
}
