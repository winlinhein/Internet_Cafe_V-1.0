package member_controllers;

import admin_controllers.InventoryCallback;
import admin_controllers.ServerInterface;
import animation.AnimationUtil;
import database.DatabaseConnection;
import java.io.IOException;
import java.net.URL;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import models.CartItem;
import models.CartManager;

public class FoodorderController implements Initializable, InitializableController, InventoryCallback {

    @FXML private Button cartBtn;
    @FXML private TilePane foodGrid;
    @FXML private Button noodleBtn;
    @FXML private Button snacksBtn;
    @FXML private Button sodaBtn;
    @FXML private Button AllBtn;
    @FXML private TextField searchField;
    @FXML private Label foodPageTitle;
    @FXML private Label orderBoostBadge;
    @FXML private AnchorPane cartPane;
    @FXML private javafx.scene.layout.StackPane foodLoadingOverlay;

    private final Map<Node, Animation> foodOrderHoverAnim = new IdentityHashMap<>();
    private final List<ProductCardEntry> productEntries = new ArrayList<>();
    private InventoryCallback callbackStub;
    private static final DropShadow FOOD_CART_HOVER_GLOW = new DropShadow(28, Color.color(0, 0, 0, 0.48));
    private static final DropShadow FOOD_CHIP_HOVER_GLOW = new DropShadow(16, Color.color(0.55, 0.62, 1, 0.42));

    static {
        FOOD_CART_HOVER_GLOW.setOffsetY(12);
        FOOD_CHIP_HOVER_GLOW.setOffsetY(5);
    }

    private boolean isCartOpen = false;
    private String currentType = "";
    private Task<List<ProductData>> currentTask;
    public static FoodorderController instance;
    private final ExecutorService loaderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "food-products-loader");
        t.setDaemon(true);
        return t;
    });
    private PauseTransition searchDebounce;

    private Connection sharedConnection;
    private ServerInterface server;
    private ClientImpl client;
    private boolean productsLoaded;
    private boolean active = true;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        instance = this;
        configureGrid();
        setActive(AllBtn);
        searchDebounce = new PauseTransition(Duration.millis(200));
        searchField.textProperty().addListener((obs, oldVal, newVal) -> {
            searchDebounce.stop();
            searchDebounce.setOnFinished(e -> applyCurrentFilter());
            searchDebounce.playFromStart();
        });
        installFoodOrderPageHovers();
    }

    @Override
    public void setDatabaseConnection(Connection con) {
        boolean connectionChanged = sharedConnection != con;
        this.sharedConnection = con;
        if (!productsLoaded || connectionChanged) {
            refreshProducts();
        } else {
            applyCurrentFilter();
        }
    }

    @Override
    public void setClient(ClientImpl client) {
        this.client = client;
    }

    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
        registerInventoryCallback();
    }

    @FXML void showAll(ActionEvent e) { currentType = ""; setActive(AllBtn); applyCurrentFilter(); }
    @FXML void showCupNoodles(ActionEvent e) { currentType = "instance noodle"; setActive(noodleBtn); applyCurrentFilter(); }
    @FXML void showSnacks(ActionEvent e) { currentType = "Snack"; setActive(snacksBtn); applyCurrentFilter(); }
    @FXML void showSoda(ActionEvent e) { currentType = "Drink"; setActive(sodaBtn); applyCurrentFilter(); }

    @FXML
    private void CartButton() {
        if (!isCartOpen) {
            openCart();
        } else {
            closeCart();
        }
    }

    private void openCart() {
        isCartOpen = true;
        cartPane.setVisible(true);
        cartPane.setManaged(true);

        if (cartPane.getChildren().isEmpty()) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/member_view/CartView.fxml"));
                Pane view = loader.load();
                CartViewController cartController = loader.getController();

                if (client != null) {
                    cartController.setSessionId(client.getSessionId());
                    cartController.setClient(client);
                }
                cartController.setSharedConnection(sharedConnection);
                cartController.setServer(server);

                cartPane.getChildren().add(view);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            if (CartViewController.instance != null) {
                CartViewController.instance.setSharedConnection(sharedConnection);
                CartViewController.instance.refresh();
            }
        }

        cartPane.setTranslateX(350);
        cartPane.setOpacity(0);
        cartPane.setScaleX(0.95);
        cartPane.setScaleY(0.95);

        TranslateTransition slide = new TranslateTransition(Duration.millis(350), cartPane);
        slide.setToX(0);

        FadeTransition fade = new FadeTransition(Duration.millis(350), cartPane);
        fade.setToValue(1);

        ScaleTransition scale = new ScaleTransition(Duration.millis(350), cartPane);
        scale.setToX(1);
        scale.setToY(1);

        new ParallelTransition(slide, fade, scale).play();
    }

    private void closeCart() {
        isCartOpen = false;

        TranslateTransition slide = new TranslateTransition(Duration.millis(300), cartPane);
        slide.setToX(350);

        FadeTransition fade = new FadeTransition(Duration.millis(300), cartPane);
        fade.setToValue(0);

        ScaleTransition scale = new ScaleTransition(Duration.millis(300), cartPane);
        scale.setToX(0.95);
        scale.setToY(0.95);

        ParallelTransition pt = new ParallelTransition(slide, fade, scale);
        pt.setOnFinished(e -> {
            cartPane.setVisible(false);
            cartPane.setManaged(false);
        });
        pt.play();
    }

    private void installFoodOrderPageHovers() {
        for (Button chip : List.of(AllBtn, sodaBtn, snacksBtn, noodleBtn)) {
            attachHoverPop(chip, 1.045, -3, 160, 210, FOOD_CHIP_HOVER_GLOW);
        }
        attachHoverPop(cartBtn, 1.05, -4, 180, 220, FOOD_CART_HOVER_GLOW);
        attachHoverPop(foodPageTitle, 1.012, -2, 240, 300, null);
        attachHoverPop(orderBoostBadge, 1.07, -3, 200, 260, null);
    }

    private void attachHoverPop(Node node, double scaleTo, double liftY, int msIn, int msOut, Effect hoverEffect) {
        if (node == null) return;
        node.setScaleX(1);
        node.setScaleY(1);
        node.setTranslateY(0);

        node.setOnMouseEntered(e -> {
            stopFoodOrderHover(node);
            if (hoverEffect != null) node.setEffect(hoverEffect);
            ScaleTransition st = new ScaleTransition(Duration.millis(msIn), node);
            st.setToX(scaleTo);
            st.setToY(scaleTo);
            st.setInterpolator(Interpolator.EASE_OUT);
            TranslateTransition tt = new TranslateTransition(Duration.millis(msIn), node);
            tt.setToY(liftY);
            tt.setInterpolator(Interpolator.EASE_OUT);
            ParallelTransition pt = new ParallelTransition(st, tt);
            foodOrderHoverAnim.put(node, pt);
            pt.play();
        });

        node.setOnMouseExited(e -> {
            stopFoodOrderHover(node);
            if (hoverEffect != null) node.setEffect(null);
            ScaleTransition st = new ScaleTransition(Duration.millis(msOut), node);
            st.setToX(1);
            st.setToY(1);
            st.setInterpolator(Interpolator.EASE_BOTH);
            TranslateTransition tt = new TranslateTransition(Duration.millis(msOut), node);
            tt.setToY(0);
            tt.setInterpolator(Interpolator.EASE_BOTH);
            ParallelTransition pt = new ParallelTransition(st, tt);
            foodOrderHoverAnim.put(node, pt);
            pt.play();
        });
    }

    private void stopFoodOrderHover(Node node) {
        Animation animation = foodOrderHoverAnim.remove(node);
        if (animation != null) animation.stop();
    }

    public void refreshProducts() {
        if (!active) return;
        reloadProductsFromDatabase();
    }

    public boolean isCartPanelOpen() {
        return isCartOpen;
    }

    public void reduceDisplayedStock(List<CartItem> purchasedItems) {
        if (purchasedItems == null || purchasedItems.isEmpty()) return;
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> reduceDisplayedStock(purchasedItems));
            return;
        }
        for (ProductCardEntry entry : productEntries) {
            String name = entry.controller.getProductName();
            if (name == null) continue;
            for (CartItem ci : purchasedItems) {
                if (ci != null && name.equals(ci.getName())) {
                    entry.controller.reduceDisplayedStock(ci.getQuantity());
                    entry.data.stockQty = Math.max(0, entry.data.stockQty - ci.getQuantity());
                }
            }
        }
    }

    private void configureGrid() {
        foodGrid.setTileAlignment(Pos.TOP_LEFT);
        foodGrid.setHgap(20);
        foodGrid.setVgap(39);
        foodGrid.setPadding(new Insets(28, 10, 38, 10));
    }

    private void setActive(Button btn) {
        List.of(AllBtn, sodaBtn, snacksBtn, noodleBtn).forEach(b -> b.getStyleClass().remove("active"));
        if (btn != null && !btn.getStyleClass().contains("active")) {
            btn.getStyleClass().add("active");
        }
    }

    private void reloadProductsFromDatabase() {
        if (!active) return;
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
        }
        setLoadingOverlayVisible(true);

        Task<List<ProductData>> task = new Task<>() {
            @Override
            protected List<ProductData> call() throws Exception {
                List<ProductData> list = new ArrayList<>();
                try {
                    if (sharedConnection == null || sharedConnection.isClosed()) {
                        sharedConnection = DatabaseConnection.connectDB();
                    }
                    String sql = "SELECT product_name, unit_price, image, stock_qty, product_type " +
                                 "FROM internet_cafe.product ORDER BY product_name";
                    try (PreparedStatement ps = sharedConnection.prepareStatement(sql);
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next() && !isCancelled()) {
                            list.add(new ProductData(
                                rs.getString("product_name"),
                                rs.getDouble("unit_price"),
                                rs.getString("image"),
                                rs.getInt("stock_qty"),
                                rs.getString("product_type")
                            ));
                        }
                    }
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
                return list;
            }
        };

        currentTask = task;
        task.setOnSucceeded(e -> {
            if (task != currentTask) return;
            setLoadingOverlayVisible(false);
            buildProductCards(task.getValue());
        });
        task.setOnCancelled(e -> setLoadingOverlayVisible(false));
        task.setOnFailed(e -> {
            setLoadingOverlayVisible(false);
            Throwable ex = task.getException();
            if (ex != null) ex.printStackTrace();
            buildProductCards(List.of());
        });

        if (loaderExecutor.isShutdown() || loaderExecutor.isTerminated()) {
            System.err.println("Cannot reload products – executor is shut down.");
            setLoadingOverlayVisible(false);
            return;
        }
        loaderExecutor.execute(task);
    }

    private void buildProductCards(List<ProductData> products) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> buildProductCards(products));
            return;
        }

        foodGrid.getChildren().clear();
        productEntries.clear();

        for (ProductData product : products) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/member_view/FoodCard.fxml"));
                VBox card = loader.load();

                FoodCardController controller = loader.getController();
                controller.setServer(this.server);
                controller.setDataAsync(product.name, product.price, product.image, product.stockQty);
                controller.setAddToCartFeedback(this::playCartAddedFeedback);

                card.setUserData(controller);
                productEntries.add(new ProductCardEntry(product, card, controller));
                foodGrid.getChildren().add(card);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        productsLoaded = true;
        applyCurrentFilter();
        replayEntranceAnimations();
    }

    private void applyCurrentFilter() {
        applyFilter(currentType, searchField != null ? searchField.getText() : null);
    }

    private void applyFilter(String type, String keyword) {
        if (productEntries.isEmpty()) return;
        String normalizedType = type == null ? "" : type.trim();
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        for (ProductCardEntry entry : productEntries) {
            boolean matchesType = normalizedType.isEmpty()
                || normalizedType.equalsIgnoreCase(entry.data.productType);
            boolean matchesKeyword = normalizedKeyword.isEmpty()
                || entry.data.name.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
            boolean visible = matchesType && matchesKeyword;
            entry.card.setVisible(visible);
            entry.card.setManaged(visible);
            entry.card.setMouseTransparent(!visible);
        }
    }

    private void setLoadingOverlayVisible(boolean show) {
        if (foodLoadingOverlay == null) return;
        foodLoadingOverlay.setVisible(show);
        foodLoadingOverlay.setManaged(show);
        foodLoadingOverlay.setMouseTransparent(!show);
    }

    private void playCartAddedFeedback() {
        if (cartBtn == null) return;
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::playCartAddedFeedback);
            return;
        }
        if (CartViewController.instance != null) {
            if (isCartPanelOpen()) {
                CartViewController.instance.playItemAddedCue();
                CartViewController.instance.refresh();
            } else {
                CartViewController.instance.refreshTotalsOnly();
            }
        }
        foodOrderHoverAnim.remove(cartBtn);
        Effect prior = cartBtn.getEffect();
        DropShadow okGlow = new DropShadow(22, Color.color(0.35, 0.95, 0.65, 0.55));
        okGlow.setOffsetY(6);
        cartBtn.setEffect(okGlow);
        ScaleTransition bump = new ScaleTransition(Duration.millis(160), cartBtn);
        bump.setFromX(1);
        bump.setFromY(1);
        bump.setToX(1.09);
        bump.setToY(1.09);
        bump.setInterpolator(Interpolator.EASE_OUT);
        ScaleTransition settle = new ScaleTransition(Duration.millis(200), cartBtn);
        settle.setToX(1);
        settle.setToY(1);
        settle.setInterpolator(Interpolator.SPLINE(0.25, 0.1, 0.25, 1.0));
        SequentialTransition seq = new SequentialTransition(bump, settle);
        seq.setOnFinished(e -> cartBtn.setEffect(prior));
        seq.play();
    }

    private void registerInventoryCallback() {
        if (server == null || callbackStub != null) return;
        try {
            callbackStub = (InventoryCallback) java.rmi.server.UnicastRemoteObject.exportObject(this, 0);
            server.registerInventoryCallback(callbackStub);
        } catch (RemoteException e) {
            System.err.println("Failed to register inventory callback: " + e.getMessage());
        }
    }

    private void unregisterInventoryCallback() {
        if (callbackStub != null && server != null) {
            try {
                server.unregisterInventoryCallback(callbackStub);
                java.rmi.server.UnicastRemoteObject.unexportObject(callbackStub, true);
                callbackStub = null;
            } catch (RemoteException e) {
                System.err.println("Failed to unregister inventory callback: " + e.getMessage());
            }
        }
    }

    @Override
    public void onInventoryChanged() throws RemoteException {
        Platform.runLater(this::refreshProducts);
    }

    public void cleanup() {
        active = false;
        unregisterInventoryCallback();
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
        }
        if (CartViewController.instance != null) {
            CartViewController.instance.cleanup();
        }
        loaderExecutor.shutdownNow();
        CartManager.clearCart();
    }

    // ─────── Helper classes ───────
    static class ProductData {
        final String name;
        final double price;
        final String image;
        int stockQty;
        final String productType;

        ProductData(String name, double price, String image, int stockQty, String productType) {
            this.name = name;
            this.price = price;
            this.image = image;
            this.stockQty = stockQty;
            this.productType = productType;
        }
    }

    static class ProductCardEntry {
        final ProductData data;
        final VBox card;
        final FoodCardController controller;

        ProductCardEntry(ProductData data, VBox card, FoodCardController controller) {
            this.data = data;
            this.card = card;
            this.controller = controller;
        }
    }

    @Override
    public void replayEntranceAnimations() {
        if (foodPageTitle != null && foodPageTitle.getOpacity() < 1.0) {
            AnimationUtil.neonCardEntrance(foodPageTitle, "#ff9a58", 0.04);
        }
        int i = 0;
        for (ProductCardEntry entry : productEntries) {
            if (entry.card.isVisible()) {
                AnimationUtil.neonCardEntrance(entry.card, "#2dd4ff", 0.10 + i * 0.05);
                i++;
            }
        }
    }

    @Override
    public void preHideForEntrance() {
        if (foodPageTitle != null) foodPageTitle.setOpacity(0);
        for (ProductCardEntry entry : productEntries) {
            if (entry.card.isVisible()) entry.card.setOpacity(0);
        }
    }
}