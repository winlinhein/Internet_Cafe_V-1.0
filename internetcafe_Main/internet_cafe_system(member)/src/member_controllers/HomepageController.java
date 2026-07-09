package member_controllers;

import animation.AnimationUtil;
import animation.PixelMotion;
import java.io.IOException;
import java.net.URL;
import java.sql.Connection;
import java.util.ResourceBundle;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;
import member_controllers.InitializableController;
import admin_controllers.ServerInterface;

public class HomepageController implements Initializable, InitializableController {

    @FXML private Label myPoint;
    @FXML private Label reamainingTime;
    @FXML private Label pcName;
    @FXML private Label totalPointSpent;
    @FXML private Label memberName;
    @FXML private Label memberType;
    @FXML private Label rateInfo; // Optional: Add this to FXML if you want to display rate info
    @FXML private HBox balanceCard;
    @FXML private HBox remainingTimeCard;
    @FXML private HBox currentStationCard;
    @FXML private HBox totalPointSpentCard;

    private ClientImpl client;
    private Connection databaseConnection;
    private ServerInterface server;
    private HomeController homeController;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        pcName.setText(TestController.clientName);
        AnimationUtil.neonCardEntrance(balanceCard, "#ba66ff", 0.08);
        AnimationUtil.neonCardEntrance(remainingTimeCard, "#59a6ff", 0.18);
        AnimationUtil.neonCardEntrance(currentStationCard, "#39ddff", 0.28);
        AnimationUtil.neonCardEntrance(totalPointSpentCard, "#ff9a58", 0.38);

        installMetricCardHover(balanceCard, "#ba66ff");
        installMetricCardHover(remainingTimeCard, "#59a6ff");
        installMetricCardHover(currentStationCard, "#39ddff");
        installMetricCardHover(totalPointSpentCard, "#ff9a58");
        
        // Set initial rate info if label exists
        if (rateInfo != null && client != null) {
            int rateMinutes = client.getSecondsPerPoint() / 60;
            rateInfo.setText("Rate: 1 point per " + rateMinutes + " minutes");
        }
    }

    // ---------- InitializableController implementation ----------
    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
    }

    @Override
    public void setClient(ClientImpl client) {
        this.client = client;
        if (client != null) {
            refreshAllData();
            updateRateInfo();
        }
    }

    @Override
    public void setDatabaseConnection(Connection con) {
        this.databaseConnection = con;
    }

    // Called by HomeController to establish a back-reference for updates
    public void setHomeController(HomeController homeController) {
        this.homeController = homeController;
        if (homeController != null) {
            int points = homeController.currentPointsProperty().get();
            updatePoints(points);
            updateMemberInfo(homeController.getMemberName(), homeController.getMemberTier());
            updateRateInfo();
        }
    }

    // ---------- Public update methods (called by HomeController) ----------
    public void updatePoints(int points) {
    Platform.runLater(() -> {
        myPoint.setText(String.valueOf(points)+" P");
        if (homeController != null) {
            int lifetime = homeController.getTotalLifetimeSpent();
            totalPointSpent.setText(String.valueOf(lifetime)+" P");  // FIXED
            updateRemainingTimeFromPoints(points);
        }
    });
}

    public void updateElapsedTime(int elapsedSeconds) {
    Platform.runLater(() -> {
        if (homeController != null) {
            int lifetime = homeController.getTotalLifetimeSpent();
            totalPointSpent.setText(String.valueOf(lifetime));  // FIXED
            int currentPoints = homeController.currentPointsProperty().get();
            updateRemainingTimeFromPoints(currentPoints);
        }
    });
}
    
    private void updateRemainingTimeFromPoints(int points) {
        if (homeController == null) return;
        
        // Get the dynamic seconds per point from the client
        int secondsPerPoint = 360; // default
        if (client != null) {
            secondsPerPoint = client.getSecondsPerPoint();
        } else if (homeController != null && homeController.getClient() != null) {
            secondsPerPoint = homeController.getClient().getSecondsPerPoint();
        }
        
        int remainingSeconds = points * secondsPerPoint;
        int hours = remainingSeconds / 3600;
        int minutes = (remainingSeconds % 3600) / 60;
        reamainingTime.setText(String.format("%dh %dm", hours, minutes));
    }
    
    private void updateRateInfo() {
        Platform.runLater(() -> {
            if (rateInfo != null && client != null) {
                int rateMinutes = client.getSecondsPerPoint() / 60;
                String tier = client.getCurrentTier();
                String tierDisplay = (tier != null && !tier.isEmpty()) ? tier.toUpperCase() : "NORMAL";
                rateInfo.setText(tierDisplay + " Rate: 1 point per " + rateMinutes + " minutes");
                System.out.println("Rate info updated: " + rateInfo.getText());
            }
        });
    }

    public void updateMemberInfo(String name, String tier) {
        Platform.runLater(() -> {
            if (name != null) memberName.setText(name);
            if (tier != null && !tier.isEmpty()) {
                memberType.setText(tier);
                // Update rate info when tier changes
                updateRateInfo();
            }
        });
    }

    // Refresh all data from the client (called after client is set)
    private void refreshAllData() {
        if (client == null) return;
        Platform.runLater(() -> {
            updateMemberInfo(client.getCurrentMemberName(), client.getCurrentTier());
            updatePoints(client.getCurrentPoints());
            updateRateInfo();
            // Elapsed time will be updated via timer callbacks
        });
    }

    // ---------- Navigation ----------
    @FXML
    private void ViewGames(ActionEvent event) {
        if (homeController != null) {
            try {
                homeController.setCenterContent("Gamepage.fxml");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
     @FXML
    void ViewsFood(ActionEvent event) {
         if (homeController != null) {
            try {
                homeController.setCenterContent("foodorder.fxml");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    void updateTotalSpent(int total) {
    Platform.runLater(() -> {
        totalPointSpent.setText(String.valueOf(total)+" P");  // FIXED
    });
}

    private void installMetricCardHover(HBox card, String glowHex) {
        if (card == null) {
            return;
        }

        Color baseColor = Color.web(glowHex);
        DropShadow shadow = createCardShadow(baseColor, 0.22, 20, 8);
        card.setEffect(shadow);
        card.setCache(true);

        PixelMotion.installCardHover(card, null, null, 1.025, -5, 1.0);
        card.addEventHandler(MouseEvent.MOUSE_ENTERED, event -> animateCardGlow(card, shadow, baseColor, true));
        card.addEventHandler(MouseEvent.MOUSE_EXITED, event -> animateCardGlow(card, shadow, baseColor, false));
    }

    private DropShadow createCardShadow(Color baseColor, double opacity, double radius, double offsetY) {
        DropShadow shadow = new DropShadow();
        shadow.setColor(withOpacity(baseColor, opacity));
        shadow.setRadius(radius);
        shadow.setSpread(0.16);
        shadow.setOffsetX(0);
        shadow.setOffsetY(offsetY);
        return shadow;
    }

    private void animateCardGlow(HBox card, DropShadow shadow, Color baseColor, boolean hovered) {
        Timeline currentAnimation = (Timeline) card.getProperties().get("metricHoverGlow");
        if (currentAnimation != null) {
            currentAnimation.stop();
        }

        Timeline glowAnimation = new Timeline(
            new KeyFrame(
                Duration.millis(220),
                new KeyValue(shadow.colorProperty(), withOpacity(baseColor, hovered ? 0.34 : 0.22), Interpolator.EASE_BOTH),
                new KeyValue(shadow.radiusProperty(), hovered ? 32 : 20, Interpolator.EASE_BOTH),
                new KeyValue(shadow.spreadProperty(), hovered ? 0.28 : 0.16, Interpolator.EASE_BOTH),
                new KeyValue(shadow.offsetYProperty(), hovered ? 12 : 8, Interpolator.EASE_BOTH)
            )
        );

        card.getProperties().put("metricHoverGlow", glowAnimation);
        glowAnimation.play();
    }

    private Color withOpacity(Color color, double opacity) {
        return Color.color(color.getRed(), color.getGreen(), color.getBlue(), opacity);
    }

    @Override
    public void replayEntranceAnimations() {
        AnimationUtil.neonCardEntrance(balanceCard, "#ba66ff", 0.08);
        AnimationUtil.neonCardEntrance(remainingTimeCard, "#59a6ff", 0.18);
        AnimationUtil.neonCardEntrance(currentStationCard, "#39ddff", 0.28);
        AnimationUtil.neonCardEntrance(totalPointSpentCard, "#ff9a58", 0.38);
    }

    @Override
    public void preHideForEntrance() {
        balanceCard.setOpacity(0);
        remainingTimeCard.setOpacity(0);
        currentStationCard.setOpacity(0);
        totalPointSpentCard.setOpacity(0);
    }
}
