package member_controllers;

import database.DatabaseConnection;
import admin_controllers.ServerInterface;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import models.CartItem;
import models.CartManager;

public class CartItemController implements Initializable {

    @FXML private Label nameLabel;
    @FXML private Label priceLabel;
    @FXML private Label qtyLabel;
    @FXML private Label subtotalLabel;
    @FXML private ImageView imageView;
    private CartItem currentItem;
    @FXML private Button decreaseQty;
    @FXML private Button increaseQty;

    private Connection sharedConnection;
    private ServerInterface server;                         // NEW
    private static final Map<String, byte[]> imageCache = new ConcurrentHashMap<>();  // NEW

    public void setSharedConnection(Connection con) {
        this.sharedConnection = con;
    }

    public void setServer(ServerInterface server) {         // NEW
        this.server = server;
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Initialization if needed
    }

    public void setData(models.CartItem item) {
        this.currentItem = item;

        int liveStock = fetchStockQty(item.getName());
        if (liveStock >= 0) {
            item.setStockQty(liveStock);
        }
        if (item.getQuantity() > item.getStockQty()) {
            item.setQuantity(Math.max(0, item.getStockQty()));
        }
        if (item.getQuantity() <= 0) {
            CartManager.removeItem(item);
            if (CartViewController.instance != null) {
                CartViewController.instance.refresh();
            }
            return;
        }

        double subtotal = item.getPrice() * item.getQuantity();
        subtotalLabel.setText("Item SubTotal : " + subtotal + " P");
        nameLabel.setText(item.getName());
        priceLabel.setText(item.getPrice() + " P");
        qtyLabel.setText(String.valueOf(item.getQuantity()));

        // --- IMAGE LOADING: use server, fallback to classpath ---
        if (item.getImagePath() != null && !item.getImagePath().isEmpty()) {
            loadImageFromServer(item.getImagePath());
        } else {
            imageView.setImage(null);
        }

        syncIncreaseButton();
    }

    private void loadImageFromServer(String imageName) {   // NEW METHOD (same logic as FoodCardController)
        byte[] cached = imageCache.get(imageName);
        if (cached != null) {
            Platform.runLater(() -> imageView.setImage(new Image(new ByteArrayInputStream(cached))));
            return;
        }

        if (server == null) {
            // fallback: try classpath
            try {
                InputStream is = getClass().getResourceAsStream("/foodimages/" + imageName);
                if (is != null) {
                    imageView.setImage(new Image(is));
                } else {
                    imageView.setImage(null);
                }
            } catch (Exception e) {
                imageView.setImage(null);
            }
            return;
        }

        new Thread(() -> {
            try {
                byte[] imageBytes = server.getImageBytes(imageName);
                if (imageBytes != null && imageBytes.length > 0) {
                    imageCache.put(imageName, imageBytes);
                    Platform.runLater(() -> imageView.setImage(new Image(new ByteArrayInputStream(imageBytes))));
                } else {
                    Platform.runLater(() -> imageView.setImage(null));
                }
            } catch (RemoteException e) {
                e.printStackTrace();
                Platform.runLater(() -> imageView.setImage(null));
            }
        }).start();
    }

    private void syncIncreaseButton() {
        if (increaseQty == null || currentItem == null) return;
        increaseQty.setDisable(currentItem.getQuantity() >= currentItem.getStockQty());
    }

    private int fetchStockQty(String productName) {
        if (productName == null) return -1;
        try {
            if (sharedConnection == null || sharedConnection.isClosed()) return -1;
            String sql = "SELECT stock_qty FROM internet_cafe.product WHERE product_name = ? LIMIT 1";
            try (PreparedStatement ps = sharedConnection.prepareStatement(sql)) {
                ps.setString(1, productName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("stock_qty");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    private void updateLineUi() {
        if (currentItem == null || qtyLabel == null || subtotalLabel == null) return;
        qtyLabel.setText(String.valueOf(currentItem.getQuantity()));
        double subtotal = currentItem.getPrice() * currentItem.getQuantity();
        subtotalLabel.setText("Item SubTotal : " + subtotal + " P");
        syncIncreaseButton();
    }

    @FXML
    void increaseQty() {
        if (currentItem == null) return;

        if (currentItem.getQuantity() >= currentItem.getStockQty()) {
            syncIncreaseButton();
            return;
        }

        currentItem.setQuantity(currentItem.getQuantity() + 1);
        updateLineUi();

        if (CartViewController.instance != null) {
            CartViewController.instance.refreshTotalsOnly();
        }
    }

    @FXML
    void decreaseQty() {
        if (currentItem == null) return;

        int qty = currentItem.getQuantity();

        if (qty > 1) {
            currentItem.setQuantity(qty - 1);
            updateLineUi();
            if (CartViewController.instance != null) {
                CartViewController.instance.refreshTotalsOnly();
            }
        } else {
            CartManager.getCartList().remove(currentItem);
            if (CartViewController.instance != null) {
                CartViewController.instance.refresh();
            }
        }
    }
}