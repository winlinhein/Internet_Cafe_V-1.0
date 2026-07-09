package member_controllers;

import admin_controllers.ServerInterface;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import models.CartItem;
import models.CartManager;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;

public class FoodCardController implements Initializable {

    @FXML private VBox root;
    @FXML private ImageView foodImage;
    @FXML private Label stockBadge;
    @FXML private Label foodName;
    @FXML private Label foodPrice;
    @FXML private Button minusBtn;
    @FXML private Label qtyLabel;
    @FXML private Button plusBtn;
    @FXML private Button orderBtn;

    private int qty = 1;
    private int stock = 0;
    private String imagePath;
    private Runnable addToCartFeedback;
    private boolean productLoaded;

    private static final Duration HOVER_IN = Duration.millis(200);
    private static final Duration HOVER_OUT = Duration.millis(220);
    private static final double HOVER_SCALE = 1.045;
    private static final double HOVER_LIFT = -10;
    private Animation hoverAnimation;

    private static final DropShadow SHADOW_REST = new DropShadow();
    private static final DropShadow SHADOW_HOVER = new DropShadow();

    static {
        SHADOW_REST.setRadius(28);
        SHADOW_REST.setSpread(0.18);
        SHADOW_REST.setOffsetY(14);
        SHADOW_REST.setColor(Color.color(0, 0, 0, 0.28));

        SHADOW_HOVER.setRadius(38);
        SHADOW_HOVER.setSpread(0.22);
        SHADOW_HOVER.setOffsetY(22);
        SHADOW_HOVER.setColor(Color.color(0, 0, 0, 0.4));
    }

    private ServerInterface server;

    public void setServer(ServerInterface server) {
        this.server = server;
    }

    private static final Map<String, byte[]> imageCache = new ConcurrentHashMap<>();

    public void setAddToCartFeedback(Runnable feedback) {
        this.addToCartFeedback = feedback;
    }

    public String getProductName() {
        return foodName != null ? foodName.getText() : null;
    }

    public void reduceDisplayedStock(int amount) {
        if (amount <= 0) return;
        stock = Math.max(0, stock - amount);
        stockBadge.setText("Stock: " + stock);
        if (qty > stock) {
            qty = Math.max(1, stock);
            qtyLabel.setText(String.valueOf(qty));
        }
        orderBtn.setDisable(stock <= 0);
        plusBtn.setDisable(stock <= 0);
        minusBtn.setDisable(stock <= 0);
        updateOutOfStockVisual();
        if (stock <= 0) {
            resetHoverVisual();
        }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        qtyLabel.setText(String.valueOf(qty));

        plusBtn.setOnAction(e -> { if (qty < stock) qty++; qtyLabel.setText(String.valueOf(qty)); });
        minusBtn.setOnAction(e -> { if (qty > 1) qty--; qtyLabel.setText(String.valueOf(qty)); });

        setupHoverPop();
    }

    private void setupHoverPop() {
        root.setScaleX(1);
        root.setScaleY(1);
        root.setTranslateY(0);
        root.setEffect(SHADOW_REST);

        root.setOnMouseEntered(e -> {
            if (!productLoaded || stock <= 0) return;
            playHoverIn();
        });
        root.setOnMouseExited(e -> {
            if (!productLoaded) return;
            playHoverOut();
        });
    }

    private void playHoverIn() {
        if (hoverAnimation != null) hoverAnimation.stop();
        root.setEffect(SHADOW_HOVER);
        ScaleTransition st = new ScaleTransition(HOVER_IN, root);
        st.setToX(HOVER_SCALE);
        st.setToY(HOVER_SCALE);
        st.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition tt = new TranslateTransition(HOVER_IN, root);
        tt.setToY(HOVER_LIFT);
        tt.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition pt = new ParallelTransition(st, tt);
        hoverAnimation = pt;
        pt.play();
    }

    private void playHoverOut() {
        if (hoverAnimation != null) hoverAnimation.stop();
        root.setEffect(SHADOW_REST);
        ScaleTransition st = new ScaleTransition(HOVER_OUT, root);
        st.setToX(1);
        st.setToY(1);
        st.setInterpolator(Interpolator.EASE_BOTH);

        TranslateTransition tt = new TranslateTransition(HOVER_OUT, root);
        tt.setToY(0);
        tt.setInterpolator(Interpolator.EASE_BOTH);

        ParallelTransition pt = new ParallelTransition(st, tt);
        hoverAnimation = pt;
        pt.play();
    }

    private void resetHoverVisual() {
        if (hoverAnimation != null) {
            hoverAnimation.stop();
            hoverAnimation = null;
        }
        if (root == null) return;
        root.setScaleX(1);
        root.setScaleY(1);
        root.setTranslateY(0);
        root.setEffect(SHADOW_REST);
    }

    @FXML
    void addtocart() {
        CartItem item = new CartItem(foodName.getText(),
                                     Double.parseDouble(foodPrice.getText().replace(" P","")),
                                     qty,
                                     imagePath,
                                     stock);
        CartManager.addItem(item);

        playAddToCartButtonFeedback();
        if (addToCartFeedback != null) {
            addToCartFeedback.run();
        }

        boolean shouldRefreshCartUi =
                CartViewController.instance != null
                && FoodorderController.instance != null
                && FoodorderController.instance.isCartPanelOpen();

        if (shouldRefreshCartUi) {
            PauseTransition wait = new PauseTransition(Duration.millis(360));
            wait.setOnFinished(ev -> CartViewController.instance.refresh());
            wait.play();
        }
    }

    private void playAddToCartButtonFeedback() {
        if (orderBtn == null) return;
        orderBtn.setScaleX(1);
        orderBtn.setScaleY(1);
        ScaleTransition press = new ScaleTransition(Duration.millis(70), orderBtn);
        press.setToX(0.93);
        press.setToY(0.93);
        press.setInterpolator(Interpolator.EASE_BOTH);
        ScaleTransition pop = new ScaleTransition(Duration.millis(130), orderBtn);
        pop.setToX(1.06);
        pop.setToY(1.06);
        pop.setInterpolator(Interpolator.EASE_OUT);
        ScaleTransition settle = new ScaleTransition(Duration.millis(150), orderBtn);
        settle.setToX(1);
        settle.setToY(1);
        settle.setInterpolator(Interpolator.EASE_OUT);
        new SequentialTransition(press, pop, settle).play();
    }

    public void setDataAsync(String name, double price, String imagePath, int stock) {
        productLoaded = true;
        this.stock = stock;
        this.imagePath = imagePath;

        foodName.setText(name);
        foodPrice.setText(price + " P");
        stockBadge.setText("Stock: " + stock);
        qty = 1;
        qtyLabel.setText("1");
        orderBtn.setDisable(stock <= 0);
        plusBtn.setDisable(stock <= 0);
        minusBtn.setDisable(stock <= 0);
        updateOutOfStockVisual();
        resetHoverVisual();

        if (imagePath != null && !imagePath.isEmpty()) {
            loadImageFromServer(imagePath);
        } else {
            foodImage.setImage(null);
        }
    }

    private void loadImageFromServer(String imageName) {
        byte[] cached = imageCache.get(imageName);
        if (cached != null) {
            Platform.runLater(() -> foodImage.setImage(new Image(new ByteArrayInputStream(cached))));
            return;
        }

        if (server == null) {
            try {
                Image img = new Image(getClass().getResource("/foodimages/" + imageName).toExternalForm(), true);
                foodImage.setImage(img);
            } catch (Exception e) {
                foodImage.setImage(null);
            }
            return;
        }

        new Thread(() -> {
            try {
                byte[] imageBytes = server.getImageBytes(imageName);
                if (imageBytes != null && imageBytes.length > 0) {
                    imageCache.put(imageName, imageBytes);
                    Platform.runLater(() -> foodImage.setImage(
                        new Image(new ByteArrayInputStream(imageBytes))));
                } else {
                    Platform.runLater(() -> foodImage.setImage(null));
                }
            } catch (RemoteException e) {
                e.printStackTrace();
                Platform.runLater(() -> foodImage.setImage(null));
            }
        }).start();
    }

    private void updateOutOfStockVisual() {
        if (root == null) return;
        root.getStyleClass().remove("food-card-out-of-stock");
        if (stock <= 0) {
            root.getStyleClass().add("food-card-out-of-stock");
        }
    }
}