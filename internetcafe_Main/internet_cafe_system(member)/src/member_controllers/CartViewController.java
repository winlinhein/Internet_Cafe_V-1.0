package member_controllers;

import admin_controllers.ServerInterface;
import animation.PixelMotion;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import models.CartItem;
import models.CartManager;

public class CartViewController implements Initializable {

    @FXML private VBox cartVBox;
    public static CartViewController instance;
    @FXML private Label totalItemsLabel;
    @FXML private Label totalLabel;
    @FXML private Button placeorder;

    private Connection sharedConnection;
    private int sessionId;
    private ClientImpl client;
    private ServerInterface server;
    private boolean isProcessingOrder = false;
    private final ExecutorService orderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "member-place-order");
        t.setDaemon(true);
        return t;
    });

    public void setServer(ServerInterface server) {
        this.server = server;
    }

    public void setSharedConnection(Connection con) {
        this.sharedConnection = con;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        instance = this;
        loadCartItems();
    }

    public void refresh() {
        loadCartItems();
    }

    public void refreshTotalsOnly() {
        calculateTotal();
        updateTotalItems();
    }

    public void playItemAddedCue() {
        if (totalItemsLabel == null) {
            return;
        }
        ScaleTransition pulse = new ScaleTransition(Duration.millis(140), totalItemsLabel);
        pulse.setFromX(1);
        pulse.setFromY(1);
        pulse.setToX(1.18);
        pulse.setToY(1.18);
        pulse.setInterpolator(Interpolator.EASE_OUT);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(2);
        pulse.play();
    }

    private void loadCartItems() {
        cartVBox.getChildren().clear();

        for (CartItem item : CartManager.getCartList()) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/member_view/CartItem.fxml"));
                Parent node = loader.load();
                CartItemController controller = loader.getController();
                controller.setSharedConnection(sharedConnection);
                controller.setServer(server);   // <-- KEY FIX: pass server for image loading
                controller.setData(item);
                cartVBox.getChildren().add(node);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        calculateTotal();
        updateTotalItems();
    }

    private void updateTotalItems() {
        int total = 0;
        for (CartItem item : CartManager.getCartList()) {
            total += item.getQuantity();
        }
        totalItemsLabel.setText("Items: " + total);
    }

    private void calculateTotal() {
        double total = 0;
        for (CartItem item : CartManager.getCartList()) {
            total += item.getPrice() * item.getQuantity();
        }
        totalLabel.setText(String.valueOf(total) + " ");
    }

    @FXML
    void removelastItem(ActionEvent event) {
        CartManager.removeLastItem();
        if (CartViewController.instance != null) {
            CartViewController.instance.refresh();
        }
    }

    @FXML
    void placeOrder(ActionEvent event) {
        List<CartItem> cartItems = CartManager.getCartList();
        if (cartItems.isEmpty()) {
            System.out.println("Cart is empty!");
            return;
        }

        if (sessionId <= 0) {
            System.err.println("No active session!");
            return;
        }

        if (client == null) {
            System.err.println("Client reference not set!");
            return;
        }

        try {
            if (sharedConnection == null || sharedConnection.isClosed()) {
                System.err.println("Shared connection is not available!");
                return;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return;
        }

        double totalCost = cartItems.stream()
                .mapToDouble(item -> item.getPrice() * item.getQuantity())
                .sum();
        int pointsRequired = (int) Math.round(totalCost);
        int currentPoints = client.getCurrentPoints();

        if (currentPoints < pointsRequired) {
            String message = String.format(
                "Insufficient points!\n\nRequired: %d points\nYour balance: %d points\nShortage: %d points\n\n" +
                "1 point = 600MMK in orders\nPlease top up your account before ordering.",
                pointsRequired, currentPoints, pointsRequired - currentPoints
            );
            showError("Insufficient Points", message);
            return;
        }

        if (isProcessingOrder) {
            System.err.println("Order already processing!");
            return;
        }

        List<CartItem> orderItems = new ArrayList<>(cartItems);
        setOrderProcessing(true);

        client.setSuppressPointsUpdateToast(true);

        orderExecutor.execute(() -> processOrder(orderItems, totalCost, pointsRequired));
    }

    private void processOrder(List<CartItem> cartItems, double totalCost, int pointsRequired) {
        try {
            sharedConnection.setAutoCommit(false);

            String insertSaleSQL = "INSERT INTO internet_cafe.sale (session_id, sale_date, sale_total_cost, status) VALUES (?, CURDATE(), ?, 'pending')";
            int saleId;
            try (PreparedStatement ps = sharedConnection.prepareStatement(insertSaleSQL, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, sessionId);
                ps.setDouble(2, totalCost);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        saleId = rs.getInt(1);
                    } else {
                        throw new SQLException("Failed to retrieve sale_id.");
                    }
                }
            }

            String insertDetailSQL = "INSERT INTO internet_cafe.sale_detail (sale_id, product_id, qty) VALUES (?, ?, ?)";
            String updateStockSQL = "UPDATE internet_cafe.product SET stock_qty = stock_qty - ? WHERE product_id = ? AND stock_qty >= ?";
            String getProductIdSQL = "SELECT product_id FROM internet_cafe.product WHERE product_name = ?";

            try (PreparedStatement detailStmt = sharedConnection.prepareStatement(insertDetailSQL);
                 PreparedStatement stockStmt = sharedConnection.prepareStatement(updateStockSQL);
                 PreparedStatement prodIdStmt = sharedConnection.prepareStatement(getProductIdSQL)) {

                for (CartItem item : cartItems) {
                    prodIdStmt.setString(1, item.getName());
                    try (ResultSet rs = prodIdStmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Product not found: " + item.getName());
                        }
                        int productId = rs.getInt("product_id");

                        stockStmt.setInt(1, item.getQuantity());
                        stockStmt.setInt(2, productId);
                        stockStmt.setInt(3, item.getQuantity());
                        int updated = stockStmt.executeUpdate();
                        if (updated == 0) {
                            throw new SQLException("Insufficient stock for: " + item.getName());
                        }

                        detailStmt.setInt(1, saleId);
                        detailStmt.setInt(2, productId);
                        detailStmt.setInt(3, item.getQuantity());
                        detailStmt.executeUpdate();
                    }
                }
            }

            String updateSessionSQL = "UPDATE internet_cafe.session SET session_total_cost = COALESCE(session_total_cost, 0) + ? WHERE session_id = ?";
            try (PreparedStatement ps = sharedConnection.prepareStatement(updateSessionSQL)) {
                ps.setDouble(1, totalCost);
                ps.setInt(2, sessionId);
                ps.executeUpdate();
            }

            sharedConnection.commit();

            if (server != null) {
                server.deductPointsForSale(client.getClientName(), pointsRequired);
                server.notifySaleCompleted(client.getClientName(), saleId, totalCost);
                server.notifyInventoryChanged();
            }

            Platform.runLater(() -> {
                if (FoodorderController.instance != null) {
                    FoodorderController.instance.refreshProducts();
                    FoodorderController.instance.reduceDisplayedStock(cartItems);
                }

                CartManager.clearCart();
                refresh();

                int remainingPoints = client.getCurrentPoints();
                showSuccess("Order Placed", String.format(
                    "Order total: %.2f (%d points)\n\nPoints remaining: %d",
                    totalCost, pointsRequired, remainingPoints
                ));

                client.setSuppressPointsUpdateToast(false);
                setOrderProcessing(false);
            });

        } catch (SQLException | RemoteException e) {
            try {
                sharedConnection.rollback();
            } catch (SQLException rollbackError) {
                rollbackError.printStackTrace();
            }
            e.printStackTrace();
            Platform.runLater(() -> {
                showError("Order Failed", e.getMessage());
                client.setSuppressPointsUpdateToast(false);
                setOrderProcessing(false);
            });
        } finally {
            try {
                sharedConnection.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void showSuccess(String title, String message) {
        Platform.runLater(() -> {
            PixelMotion.toastGlitch(resolveToastAnchor(), title, message, PixelMotion.ToastType.OK);
        });
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            PixelMotion.toastGlitch(resolveToastAnchor(), title, message, PixelMotion.ToastType.ERROR);
        });
    }

    public void setSessionId(int sessionId) {
        this.sessionId = sessionId;
    }

    public void setClient(ClientImpl client) {
        this.client = client;
    }

    private Node resolveToastAnchor() {
        if (placeorder != null) {
            return placeorder;
        }
        if (cartVBox != null) {
            return cartVBox;
        }
        return totalLabel;
    }

    private void setOrderProcessing(boolean processing) {
        isProcessingOrder = processing;
        if (placeorder != null) {
            placeorder.setDisable(processing);
            placeorder.setText(processing ? "Processing..." : "Place Order");
        }
    }
    
    public void cleanup() {
        orderExecutor.shutdownNow();
    }
}