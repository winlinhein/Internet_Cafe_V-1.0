package member_controllers;

import admin_controllers.GameCallback;
import admin_controllers.ServerInterface;
import animation.AnimationUtil;
import animation.PixelMotion;
import database.GameDAO;

import java.io.IOException;
import java.net.URL;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

public class GamepageController implements Initializable, InitializableController, GameCallback {

    private static final double GAME_CELL_WIDTH = 260;
    private static final double GAME_CELL_HEIGHT = 384;

    @FXML private TilePane gameGrid;
    @FXML private TextField searchField;
    @FXML private StackPane searchShell;

    private List<Game> allGames = FALLBACK_GAMES;
    private final List<GameCardEntry> gameEntries = new ArrayList<>();
    private String lastQuery = "";
    private Connection databaseConnection;
    private ServerInterface server;
    private ClientImpl client;
    private boolean gamesLoaded;

    private boolean serverReady = false;
    private boolean connectionReady = false;
    private boolean rebuildNeeded = false;

    private GameCallback callbackStub;

    private static final List<Game> FALLBACK_GAMES = List.of();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        configureGrid();
        configureSearchBar();
    }

    @Override
    public void setDatabaseConnection(Connection con) {
        boolean connectionChanged = databaseConnection != con;
        this.databaseConnection = con;
        connectionReady = true;

        if (databaseConnection != null && (!gamesLoaded || connectionChanged)) {
            if (!serverReady) {
                rebuildNeeded = true;
            } else {
                rebuildNeeded = false;
                loadGameCardsFromDatabase();
            }
        } else if (!gamesLoaded) {
            allGames = FALLBACK_GAMES;
            rebuildGameCards(allGames);
        } else {
            applyFilter(lastQuery);
        }
    }

    @Override
    public void setServer(ServerInterface server) {
        this.server = server;
        serverReady = true;
        registerGameCallback();

        if (connectionReady && rebuildNeeded) {
            rebuildNeeded = false;
            loadGameCardsFromDatabase();
        }
    }

    @Override
    public void setClient(ClientImpl client) {
        this.client = client;
    }

    private void registerGameCallback() {
        if (server == null || callbackStub != null) return;
        try {
            callbackStub = (GameCallback) UnicastRemoteObject.exportObject(this, 0);
            server.registerGameCallback(callbackStub);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onGamesChanged() throws RemoteException {
        GameCardController.clearImageCache();
        Platform.runLater(() -> loadGameCardsFromDatabase());
    }

    public void cleanup() {
        if (callbackStub != null && server != null) {
            try {
                server.unregisterGameCallback(callbackStub);
                UnicastRemoteObject.unexportObject(callbackStub, true);
                callbackStub = null;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private void configureSearchBar() {
        if (searchField == null) return;

        if (searchShell != null) {
            PixelMotion.installSearchFieldFX(searchShell, searchField, 460, 320);
        } else {
            searchField.setOnMouseEntered(e -> PixelMotion.pulse(searchField, 1.01, 180));
            searchField.focusedProperty().addListener((obs, oldV, newV) -> {
                if (newV) {
                    PixelMotion.createWidthMorph(searchField, 420, 220).play();
                    PixelMotion.pulse(searchField, 1.01, 220);
                } else {
                    PixelMotion.createWidthMorph(searchField, 290, 180).play();
                }
            });
        }

        searchField.textProperty().addListener((obs, oldText, newText) -> {
            if (searchShell != null) {
                searchShell.pseudoClassStateChanged(
                    javafx.css.PseudoClass.getPseudoClass("filled"),
                    newText != null && !newText.isBlank()
                );
            }
            lastQuery = newText;
            applyFilter(newText);
        });
    }

    private void loadGameCardsFromDatabase() {
        Task<List<Game>> task = new Task<>() {
            @Override
            protected List<Game> call() throws Exception {
                if (databaseConnection != null && !databaseConnection.isClosed()) {
                    return GameDAO.getAllGames(databaseConnection);
                }
                return FALLBACK_GAMES;
            }
        };

        task.setOnSucceeded(e -> {
            List<Game> loaded = task.getValue();
            allGames = (loaded == null || loaded.isEmpty()) ? FALLBACK_GAMES : loaded;
            rebuildGameCards(allGames);
        });

        task.setOnFailed(e -> {
            allGames = FALLBACK_GAMES;
            rebuildGameCards(allGames);
        });

        Thread t = new Thread(task, "load-games");
        t.setDaemon(true);
        t.start();
    }

    private void rebuildGameCards(List<Game> games) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> rebuildGameCards(games));
            return;
        }

        gameGrid.getChildren().clear();
        gameEntries.clear();

        for (int i = 0; i < games.size(); i++) {
            Game game = games.get(i);
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/member_view/GameCard.fxml"));
                VBox card = loader.load();

                GameCardController controller = loader.getController();
                controller.setServer(this.server);
                controller.setGameData(game);

                StackPane cell = new StackPane(card);
                cell.setAlignment(Pos.TOP_LEFT);
                cell.setPrefSize(GAME_CELL_WIDTH, GAME_CELL_HEIGHT);
                cell.setMinSize(GAME_CELL_WIDTH, GAME_CELL_HEIGHT);
                cell.setMaxSize(GAME_CELL_WIDTH, GAME_CELL_HEIGHT);
                cell.setPadding(new Insets(8, 8, 16, 8));
                cell.getStyleClass().add("card-cell-wrap");
                cell.setOpacity(0);

                gameEntries.add(new GameCardEntry(game, cell));
                gameGrid.getChildren().add(cell);
                PixelMotion.playEntrance(cell, 8, 180, i * 18L);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        gamesLoaded = true;
        applyFilter(lastQuery);
    }

    private void applyFilter(String query) {
        if (gameEntries.isEmpty()) return;

        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (GameCardEntry entry : gameEntries) {
            boolean visible = normalized.isEmpty() || matchesQuery(entry.game, normalized);
            entry.cell.setVisible(visible);
            entry.cell.setManaged(visible);
            entry.cell.setMouseTransparent(!visible);
        }
    }

    private boolean matchesQuery(Game game, String query) {
        if (game == null || query == null || query.isBlank()) return true;

        String title = game.getTitle() == null ? "" : game.getTitle().toLowerCase(Locale.ROOT);
        String tag = game.getTag() == null ? "" : game.getTag().toLowerCase(Locale.ROOT);
        String steamId = game.getSteamGameId() == null ? "" : game.getSteamGameId().toLowerCase(Locale.ROOT);
        String process = game.getProcessName() == null ? "" : game.getProcessName().toLowerCase(Locale.ROOT);

        return title.contains(query) || tag.contains(query) || steamId.contains(query) || process.contains(query);
    }

    private void configureGrid() {
        gameGrid.setPrefTileWidth(GAME_CELL_WIDTH);
        gameGrid.setPrefTileHeight(GAME_CELL_HEIGHT);
        gameGrid.setTileAlignment(Pos.TOP_LEFT);
        gameGrid.setHgap(22);
        gameGrid.setVgap(22);
        gameGrid.setPadding(new Insets(14, 22, 30, 22));
    }

    private static final class GameCardEntry {
        final Game game;
        final StackPane cell;

        GameCardEntry(Game game, StackPane cell) {
            this.game = game;
            this.cell = cell;
        }
    }

    @Override
    public void replayEntranceAnimations() {
        AnimationUtil.neonCardEntrance(searchShell, "#8b5cf6", 0.04);
        int i = 0;
        for (GameCardEntry entry : gameEntries) {
            if (entry.cell.isVisible()) {
                AnimationUtil.neonCardEntrance(entry.cell, "#39ddff", 0.10 + i * 0.05);
                i++;
            }
        }
    }

    @Override
    public void preHideForEntrance() {
        if (searchShell != null) searchShell.setOpacity(0);
        for (GameCardEntry entry : gameEntries) {
            if (entry.cell.isVisible()) entry.cell.setOpacity(0);
        }
    }
}