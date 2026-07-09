package member_controllers;

public class Game {
    private final String title;
    private final String tag;
    private final String imagePath;
    private final String localExePath;
    private final String steamGameId;
    private final String processName;

    public Game(String title, String tag, String imagePath, String localExePath, String steamGameId, String processName) {
        this.title = title;
        this.tag = tag;
        this.imagePath = imagePath;
        this.localExePath = localExePath;
        this.steamGameId = steamGameId;
        this.processName = processName;
    }

    public String getTitle() { return title; }
    public String getTag() { return tag; }
    public String getImagePath() { return imagePath; }
    public String getLocalExePath() { return localExePath; }
    public String getSteamGameId() { return steamGameId; }
    public String getProcessName() { return processName; }
}
