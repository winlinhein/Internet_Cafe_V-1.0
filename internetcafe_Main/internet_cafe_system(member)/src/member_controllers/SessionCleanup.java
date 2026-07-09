package member_controllers;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Internet-cafe style session cleanup: kills all game platform processes and
 * clears login/session data so the next user must log in fresh.
 */
public final class SessionCleanup {

    private SessionCleanup() {
    }

    /** Game platform processes to kill on logout. /T kills child processes too. */
    private static final List<String> PLATFORM_PROCESSES = Arrays.asList(
            /* Steam */
            "steam.exe",
            "steamservice.exe",
            "steamwebhelper.exe",
            "gameoverlayui.exe",
            /* Epic Games */
            "EpicGamesLauncher.exe",
            "EpicGamesLauncherHelper.exe",
            "UnrealCEFSubProcess.exe",
            "EpicWebHelper.exe",
            /* GOG Galaxy */
            "GalaxyClient.exe",
            "GalaxyCommunication.exe",
            /* EA / Origin */
            "EALauncher.exe",
            "EALaunchHelper.exe",
            "Origin.exe",
            "OriginWebHelperService.exe",
            /* Battle.net / Blizzard */
            "Battle.net.exe",
            "Agent.exe",
            "BlizzardErrorProxy.exe",
            /* Ubisoft Connect */
            "UbisoftConnect.exe",
            "UbisoftGameLauncher64.exe",
            "UbisoftGameLauncher.exe",
            /* Riot Games */
            "RiotClientServices.exe",
            "RiotClientUx.exe",
            "RiotClientCrashHandler.exe",
            "VALORANT-Win64-Shipping.exe",
            "League of Legends.exe",
            "LeagueClient.exe",
            "LeagueClientUx.exe",
            /* Discord (common in cafes) */
            "Discord.exe",
            "DiscordPTB.exe",
            "DiscordCanary.exe",
            /* Xbox / Microsoft Store */
            "XboxApp.exe",
            "XboxGameOverlay.exe",
            "GamingServices.exe",
            /* Common games */
            "dota2.exe",
            "cs2.exe",
            "FallGuys_client.exe",
            "FortniteClient-Win64-Shipping.exe",
            "GTA5.exe",
            "Cyberpunk2077.exe",
            "eldenring.exe",
            "Minecraft.Windows.exe",
            "RobloxPlayerBeta.exe"
    );

    public static void cleanupForLogout(String currentGameProcessName) {
        Set<String> processNames = new LinkedHashSet<>();

        if (currentGameProcessName != null && !currentGameProcessName.isBlank()) {
            processNames.add(currentGameProcessName);
        }
        processNames.addAll(PLATFORM_PROCESSES);

        killProcesses(new ArrayList<>(processNames));
        clearAllPlatformLoginData();
    }

    /** Kill processes using /F (force) and /T (kill child tree). */
    private static void killProcesses(List<String> processNames) {
        for (String processName : processNames) {
            if (processName == null || processName.isBlank()) {
                continue;
            }
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        "cmd", "/c", "taskkill /F /T /IM " + processName + " 2>nul");
                pb.redirectErrorStream(true);
                pb.start().waitFor();
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static void clearAllPlatformLoginData() {
        clearSteamLoginData();
        clearEpicLoginData();
        clearGogLoginData();
        clearEaOriginLoginData();
        clearBattleNetLoginData();
        clearUbisoftLoginData();
        clearRiotLoginData();
        clearDiscordLoginData();
    }

    private static void clearSteamLoginData() {
        String steamConfigDir = findSteamConfigDir();
        if (steamConfigDir == null) {
            return;
        }
        deleteFiles(steamConfigDir, "loginusers.vdf", "config.vdf");
        runSilent("reg", "delete", "HKCU\\Software\\Valve\\Steam", "/v", "AutoLoginUser", "/f");
    }

    private static String findSteamConfigDir() {
        try {
            Process p = new ProcessBuilder("cmd", "/c",
                    "reg query \"HKCU\\Software\\Valve\\Steam\" /v SteamPath 2>nul")
                    .redirectErrorStream(true)
                    .start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            for (String line : output.split("\r?\n")) {
                String upper = line.toUpperCase();
                if (upper.contains("STEAMPATH") && upper.contains("REG_SZ")) {
                    int idx = upper.indexOf("REG_SZ");
                    String path = line.substring(idx + 6).trim();
                    if (!path.isEmpty()) {
                        return path + File.separator + "config";
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        String[] fallbacks = {
                System.getenv("ProgramFiles(X86)") + "\\Steam\\config",
                System.getenv("ProgramFiles") + "\\Steam\\config",
        };
        for (String path : fallbacks) {
            if (path != null && !path.contains("null")) {
                File f = new File(path);
                if (f.isDirectory()) {
                    return path;
                }
            }
        }
        return null;
    }

    private static void clearEpicLoginData() {
        String local = System.getenv("LOCALAPPDATA");
        if (local == null) return;
        Path saved = Path.of(local, "EpicGamesLauncher", "Saved");
        if (!Files.isDirectory(saved)) return;
        try {
            Files.list(saved)
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().toLowerCase().startsWith("webcache"))
                    .forEach(SessionCleanup::deleteDirectorySilent);
        } catch (IOException ignored) {
        }
    }

    private static void clearGogLoginData() {
        String local = System.getenv("LOCALAPPDATA");
        String programData = System.getenv("ProgramData");
        if (local != null) {
            deleteDirectorySilent(Path.of(local, "GOG Galaxy", "Cache"));
            deleteDirectorySilent(Path.of(local, "GOG.com", "Galaxy", "Applications"));
        }
        if (programData != null) {
            deleteDirectorySilent(Path.of(programData, "GOG.com", "Galaxy", "webcache"));
        }
    }

    private static void clearEaOriginLoginData() {
        String local = System.getenv("LOCALAPPDATA");
        String appData = System.getenv("APPDATA");
        String programData = System.getenv("ProgramData");
        if (local != null) {
            deleteDirectorySilent(Path.of(local, "EALaunchHelper", "cache"));
            deleteDirectorySilent(Path.of(local, "EADesktop", "cache"));
            deleteDirectorySilent(Path.of(local, "Electronic Arts", "EA Desktop"));
        }
        if (appData != null) {
            deleteDirectorySilent(Path.of(appData, "Origin"));
        }
        if (programData != null) {
            Path origin = Path.of(programData, "Origin");
            if (Files.isDirectory(origin)) {
                try {
                    Files.list(origin)
                            .filter(p -> !"LocalContent".equals(p.getFileName().toString()))
                            .forEach(SessionCleanup::deleteDirectorySilent);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static void clearBattleNetLoginData() {
        String programData = System.getenv("ProgramData");
        if (programData == null) return;
        deleteDirectorySilent(Path.of(programData, "Battle.net"));
        deleteDirectorySilent(Path.of(programData, "Blizzard Entertainment"));
    }

    private static void clearUbisoftLoginData() {
        String programData = System.getenv("ProgramData");
        if (programData == null) return;
        deleteDirectorySilent(Path.of(programData, "Ubisoft Connect", "cache"));
        deleteDirectorySilent(Path.of(programData, "Ubisoft", "Ubisoft Game Launcher", "cache"));
    }

    private static void clearRiotLoginData() {
        String local = System.getenv("LOCALAPPDATA");
        if (local == null) return;
        deleteDirectorySilent(Path.of(local, "VALORANT", "Saved"));
        deleteDirectorySilent(Path.of(local, "Riot Games", "Riot Client", "Data"));
        deleteDirectorySilent(Path.of(local, "Riot Games", "Riot Client", "Cache"));
    }

    private static void clearDiscordLoginData() {
        String appData = System.getenv("APPDATA");
        if (appData == null) return;
        for (String name : Arrays.asList("discord", "DiscordPTB", "DiscordCanary")) {
            Path base = Path.of(appData, name);
            deleteDirectorySilent(base.resolve("Local Storage"));
            deleteDirectorySilent(base.resolve("Cookies"));
            deleteDirectorySilent(base.resolve("Session Storage"));
        }
    }

    private static void deleteFiles(String parentDir, String... names) {
        for (String name : names) {
            File f = new File(parentDir, name);
            if (f.isFile()) {
                try {
                    f.delete();
                } catch (SecurityException ignored) {
                }
            }
        }
    }

    private static void deleteDirectorySilent(Path path) {
        if (!Files.exists(path) || !Files.isDirectory(path)) return;
        try {
            Files.walk(path, FileVisitOption.FOLLOW_LINKS)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }

    private static void runSilent(String... command) {
        try {
            new ProcessBuilder(command).redirectErrorStream(true).start().waitFor();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
