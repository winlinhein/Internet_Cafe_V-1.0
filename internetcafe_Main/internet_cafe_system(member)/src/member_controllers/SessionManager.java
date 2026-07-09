package member_controllers;

public final class SessionManager {

    private static String currentGameProcessName;

    private SessionManager() {
    }

    public static void setCurrentGameProcessName(String processName) {
        currentGameProcessName = processName;
    }

    public static String getCurrentGameProcessName() {
        return currentGameProcessName;
    }

    public static void clearCurrentGameProcessName() {
        currentGameProcessName = null;
    }
}
