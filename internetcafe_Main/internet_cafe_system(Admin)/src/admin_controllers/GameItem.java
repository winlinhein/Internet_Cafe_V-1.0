package admin_controllers;

public class GameItem {
    private String gameId;
    private String title;
    private String imagePath;

    public GameItem(String gameId, String title, String imagePath) {
        this.gameId = gameId;
        this.title = title;
        this.imagePath = imagePath;
    }

    public String getGameId() {
        return gameId;
    }

    public void setGameId(String gameId) {
        this.gameId = gameId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }
}
