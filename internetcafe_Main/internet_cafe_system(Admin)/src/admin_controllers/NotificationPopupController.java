package admin_controllers;

import animation.PixelMotion;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javafx.scene.Parent;

public class NotificationPopupController {

    @FXML private StackPane root;
    @FXML private Region edgeGlow;
    @FXML private Region scanline;
    @FXML private VBox card;
    @FXML private Label badgeLabel;
    @FXML private Label subLabel;
    @FXML private VBox listBox;
    @FXML private ScrollPane scrollPane;
    @FXML private Button closeBtn;
    @FXML private Button clearBtn;
    private Runnable onCloseAction;

    private List<String> boundNotifications = new ArrayList<String>();
    private int currentCount;
    private IntConsumer onCountChanged;
    private StackPane contentStack;

    @FXML
    public void initialize() {
        if (closeBtn != null) {
            closeBtn.setVisible(true);
            closeBtn.setManaged(true);
        }
    }
    
    public void setContentStack(StackPane contentStack) {
        this.contentStack = contentStack;
    }

    public void setData(int notificationCount, List<String> notifications) {
        boundNotifications = notifications == null ? new ArrayList<String>() : notifications;
        currentCount = Math.max(0, Math.min(Math.max(notificationCount, 0), boundNotifications.size() == 0 ? Math.max(notificationCount, 0) : boundNotifications.size()));
        if (notifications == null) {
            currentCount = Math.max(notificationCount, 0);
        }
        refreshList();
    }

    public void setCountChangedHandler(IntConsumer onCountChanged) {
        this.onCountChanged = onCountChanged;
    }
    
    public List<String> getBoundNotifications() {
        return new ArrayList<>(boundNotifications);
    }

    private void refreshList() {
        int safeCount = Math.max(currentCount, 0);
        badgeLabel.setText(safeCount > 99 ? "99+" : String.valueOf(safeCount));
        subLabel.setText(safeCount > 0 ? "Live alerts for your cafe dashboard" : "No unread alerts right now");
        clearBtn.setDisable(safeCount <= 0);

        listBox.getChildren().clear();
        if (boundNotifications.isEmpty() || safeCount <= 0) {
            Label empty = new Label("All clear. New notifications will appear here.");
            empty.getStyleClass().add("notify-empty");
            empty.setWrapText(true);
            listBox.getChildren().add(empty);
            return;
        }

        int i = 0;
        List<String> snapshot = new ArrayList<String>(boundNotifications);
        for (String text : snapshot) {
            Node row = createNotificationCard(text, i);
            if (row != null) {
                listBox.getChildren().add(row);
            }
            i++;
        }
    }

    private Node createNotificationCard(String rawText, int index) {
        if (rawText.contains("|SALE|")) {
            return createSaleCard(rawText);
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/notification_item.fxml"));
            Node row = loader.load();
            NotificationItemController controller = loader.getController();
            controller.setData(extractMessage(rawText), buildTimeText(index), extractTag(rawText),
                          () -> dismissNotification(rawText, row));
            return row;
        } catch (IOException ex) {
            Label fallback = new Label(rawText == null ? "" : rawText);
            fallback.getStyleClass().add("notify-empty");
            fallback.setWrapText(true);
            return fallback;
        }
    }

    private Node createSaleCard(String rawText) {
        try {
            String saleData = rawText;
            if (rawText.startsWith("Purchase|")) {
                saleData = rawText.substring("Purchase|".length());
            }
            String[] parts = saleData.split("\\|", 6);
            if (parts.length < 5) return createFallbackCard(rawText);

            int saleId = Integer.parseInt(parts[1]);
            String memberName = parts[2];
            String pcName = parts[3];
            double amount = Double.parseDouble(parts[4]);
            int itemCount = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;
            String orderStatus = resolveSaleStatus(saleId);

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/order_item_row.fxml"));
            Node row = loader.load();
            OrderItemRow controller = loader.getController();

            controller.setSummaryData(memberName, pcName, itemCount, amount, orderStatus,
                () -> openOrderDetailModal(saleId),
                () -> dismissNotification(rawText, row)
            );
            row.setUserData(rawText);
            return row;
        } catch (Exception e) {
            e.printStackTrace();
            return createFallbackCard(rawText);
        }
    }

    private Node createFallbackCard(String rawText) {
        Label fallback = new Label(extractMessage(rawText));
        fallback.getStyleClass().add("notify-empty");
        fallback.setWrapText(true);
        return fallback;
    }

    private String resolveSaleStatus(int saleId) {
        Connection con = Admin_dashboardController.getSharedConnection();
        if (con == null) {
            return "pending";
        }

        String query = "SELECT status FROM internet_cafe.sale WHERE sale_id = ?";
        try (PreparedStatement pst = con.prepareStatement(query)) {
            pst.setInt(1, saleId);
            try (ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    String status = rs.getString("status");
                    return (status == null || status.isBlank()) ? "pending" : status;
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to resolve sale status for notification " + saleId + ": " + e.getMessage());
        }
        return "pending";
    }

    private String normalizeStatus(String rawStatus) {
        if (rawStatus == null) return "pending";
        String status = rawStatus.trim().toLowerCase();
        if (status.isEmpty()) return "pending";
        if (status.equals("completed") || status.equals("complete") || status.equals("done")) {
            return "completed";
        }
        return "pending";
    }

    private boolean isPendingSaleNotification(String rawText) {
        if (!rawText.contains("|SALE|")) return false;
        
        String saleData = rawText.startsWith("Purchase|") ? 
            rawText.substring("Purchase|".length()) : rawText;
        
        String[] parts = saleData.split("\\|", 6);
        if (parts.length < 5) return false;
        
        try {
            int saleId = Integer.parseInt(parts[1]);
            String status = resolveSaleStatus(saleId);
            return !"completed".equals(normalizeStatus(status));
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void openOrderDetailModal(int saleId) {
        System.out.println("*** Opening modal for saleId=" + saleId);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/view_order_modal.fxml"));
            Parent modalRoot = loader.load();
            ViewOrderModal controller = loader.getController();
            Connection con = Admin_dashboardController.getSharedConnection();
            if (con == null) {
                System.err.println("Database connection is null!");
                return;
            }
            controller.setSaleId(saleId, con, this::refreshList);

            StackPane host = contentStack;
            if (host == null) {
                host = findContentStack(root);
            }
            if (host != null) {
                PixelMotion.showOverlayInStack(host, modalRoot, false);
            } else {
                PixelMotion.playWindowIntro(modalRoot, false);
            }
        } catch (Exception e) {
            System.err.println("Failed to open order modal:");
            e.printStackTrace();
        }
    }

    private StackPane findContentStack(javafx.scene.Node start) {
        javafx.scene.Node current = start;
        while (current != null) {
            if (current instanceof StackPane sp && sp.getScene() != null) return sp;
            current = current.getParent();
        }
        return null;
    }

    private void dismissNotification(String rawText, Node row) {
        listBox.getChildren().remove(row);
        boundNotifications.remove(rawText);
        currentCount = Math.max(0, Math.min(currentCount - 1, boundNotifications.size()));
        if (onCountChanged != null) {
            onCountChanged.accept(currentCount);
        }
        if (boundNotifications.isEmpty() || currentCount <= 0) {
            currentCount = 0;
            refreshList();
        }
    }

    private String extractMessage(String rawText) {
        String safe = rawText == null ? "" : rawText.trim();
        int split = safe.indexOf("|");
        if (split >= 0 && split < safe.length() - 1) {
            return safe.substring(split + 1).trim();
        }
        return safe;
    }

    private String extractTag(String rawText) {
        String safe = rawText == null ? "" : rawText.trim();
        int split = safe.indexOf("|");
        if (split > 0) {
            return safe.substring(0, split).trim();
        }
        String lower = safe.toLowerCase();
        if (lower.contains("ends") || lower.contains("requested") || lower.contains("ready")) {
            return "Alert";
        }
        if (lower.contains("arrived") || lower.contains("new")) {
            return "New";
        }
        return "Live";
    }

    private String buildTimeText(int index) {
        return index == 0 ? "JUST NOW" : (2 + index * 3) + " MIN AGO";
    }

    public void setActions(Runnable onClose, Runnable onClear) {
        this.onCloseAction = onClose;
        if (closeBtn != null) {
            closeBtn.setOnAction(e -> {
                if (this.onCloseAction != null) {
                    this.onCloseAction.run();
                }
                animation.PixelMotion.closeOverlayFrom(root);
            });
        }
        if (clearBtn != null) {
            clearBtn.setOnAction(e -> { 
                List<String> toKeep = new ArrayList<>();
                for (String notification : boundNotifications) {
                    if (notification.contains("|SALE|") && isPendingSaleNotification(notification)) {
                        toKeep.add(notification);
                    }
                }
                boundNotifications.clear();
                boundNotifications.addAll(toKeep);
                currentCount = boundNotifications.size();
                
                if (onCountChanged != null) {
                    onCountChanged.accept(currentCount);
                }
                refreshList();
                System.out.println("Cleared notifications, kept " + toKeep.size() + " pending orders");
            });
        }
    }

    @FXML
    void handleCloseAction(ActionEvent event) {
        animation.PixelMotion.closeOverlayFrom(root);
    }

    public StackPane getRoot() { return root; }
    public VBox getCard() { return card; }
    public Region getEdgeGlow() { return edgeGlow; }
    public Region getScanline() { return scanline; }
    public Label getBadgeLabel() { return badgeLabel; }
    public VBox getListBox() { return listBox; }
    
}