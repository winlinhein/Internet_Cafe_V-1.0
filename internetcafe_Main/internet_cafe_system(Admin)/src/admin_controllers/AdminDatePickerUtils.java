package admin_controllers;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Labeled;
import javafx.scene.input.MouseEvent;

public final class AdminDatePickerUtils {

    private static final DateTimeFormatter POPUP_MONTH_FORMAT =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault());
    private static final String NAV_HOOK_KEY = "admin-datepicker-nav-hooked";

    private AdminDatePickerUtils() {
    }

    public static void configurePastAndPresentOnly(DatePicker datePicker, String popupStylesheet) {
        if (datePicker == null) {
            return;
        }

        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);

                boolean blocked = empty || item == null || item.isAfter(LocalDate.now());
                setDisable(blocked);

                if (!getStyleClass().contains("date-cell-future-disabled")) {
                    getStyleClass().add("date-cell-future-disabled");
                }
            }
        });

        datePicker.valueProperty().addListener((obs, oldValue, newValue) -> {
            LocalDate today = LocalDate.now();
            if (newValue != null && newValue.isAfter(today)) {
                LocalDate fallback = oldValue != null && !oldValue.isAfter(today) ? oldValue : today;
                Platform.runLater(() -> datePicker.setValue(fallback));
            }
        });

        datePicker.showingProperty().addListener((obs, wasShowing, isShowing) -> {
            if (isShowing) {
                Platform.runLater(() -> {
                    attachPopupStylesheet(datePicker, popupStylesheet);
                    hookNavigationState(datePicker);
                    syncFutureNavigationState(datePicker);
                });
            }
        });
    }

    private static void attachPopupStylesheet(DatePicker datePicker, String popupStylesheet) {
        if (popupStylesheet == null || popupStylesheet.isBlank()) {
            return;
        }

        Node popup = datePicker.lookup(".date-picker-popup");
        if (popup != null && popup.getScene() != null
                && !popup.getScene().getStylesheets().contains(popupStylesheet)) {
            popup.getScene().getStylesheets().add(popupStylesheet);
        }
    }

    private static void syncFutureNavigationState(DatePicker datePicker) {
        YearMonth showingMonth = resolveDisplayedMonth(datePicker);
        YearMonth currentMonth = YearMonth.now();
        boolean blockForward = !showingMonth.isBefore(currentMonth);

        toggleButtons(datePicker, ".next-month-button", blockForward);
        toggleButtons(datePicker, ".next-year-button", blockForward);
        toggleButtons(datePicker, ".right-button", blockForward);
    }

    private static YearMonth resolveDisplayedMonth(DatePicker datePicker) {
        Node monthLabelNode = datePicker.lookup(".month-year-pane .label");
        if (monthLabelNode instanceof Labeled labeled) {
            String labelText = labeled.getText();
            if (labelText != null && !labelText.isBlank()) {
                try {
                    return YearMonth.parse(labelText.trim(), POPUP_MONTH_FORMAT);
                } catch (DateTimeParseException ignored) {
                }
            }
        }

        LocalDate fallback = datePicker.getValue() != null ? datePicker.getValue() : LocalDate.now();
        return YearMonth.from(fallback);
    }

    private static void hookNavigationState(DatePicker datePicker) {
        for (Node node : datePicker.lookupAll(".left-button, .right-button, .next-month-button, .next-year-button, .previous-month-button, .previous-year-button")) {
            if (Boolean.TRUE.equals(node.getProperties().get(NAV_HOOK_KEY))) {
                continue;
            }

            node.addEventHandler(MouseEvent.MOUSE_CLICKED, event ->
                    Platform.runLater(() -> syncFutureNavigationState(datePicker)));
            node.getProperties().put(NAV_HOOK_KEY, Boolean.TRUE);
        }
    }

    private static void toggleButtons(DatePicker datePicker, String selector, boolean disabled) {
        for (Node node : datePicker.lookupAll(selector)) {
            node.setDisable(disabled);
            node.setOpacity(disabled ? 0.35 : 1.0);
            node.setMouseTransparent(disabled);
        }
    }
}
