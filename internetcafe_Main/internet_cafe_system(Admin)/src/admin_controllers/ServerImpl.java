package admin_controllers;

import database.DatabaseConnection;
import member_controllers.ClientInterface;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import javafx.application.Platform;
import java.sql.SQLException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ServerImpl extends UnicastRemoteObject implements ServerInterface {
    private PCViewController controller;
    private Connection dbConnection;

    private final Map<String, Integer> clientRemainingSeconds = new ConcurrentHashMap<>();
    private final Map<String, Integer> clientElapsedSeconds = new ConcurrentHashMap<>();
    private final Map<String, Integer> clientInitialPoints = new ConcurrentHashMap<>();
    private final Map<String, ClientInterface> clientMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> clientActive = new ConcurrentHashMap<>();
    private final Map<String, Integer> clientMemberIds = new ConcurrentHashMap<>();
    private final Map<String, String> clientMemberNames = new ConcurrentHashMap<>();
    private final List<AdminCallback> adminCallbacks = new CopyOnWriteArrayList<>();
    private final List<InventoryCallback> inventoryCallbacks = new CopyOnWriteArrayList<>();
    private final Map<Integer, Integer> cachedPointsRate = new ConcurrentHashMap<>();
    private final List<GameCallback> gameCallbacks = new CopyOnWriteArrayList<>();
    private static final Path IMAGE_DIR = Paths.get(System.getProperty("user.dir"), "src", "dbimages");


    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService clientUpdateExecutor = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "client-update");
        t.setDaemon(true);
        return t;
    });

    public ServerImpl() throws RemoteException {
        super();
        startTickScheduler();
    }

    public void setPCController(PCViewController controller) {
        this.controller = controller;
    }
    
    public void setDatabaseConnection(Connection con) {
        this.dbConnection = con;
    }

    public Connection getDatabaseConnection() {
        return this.dbConnection;
    }

    private void startTickScheduler() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                tickOneSecond();
            } catch (Exception e) {
                System.err.println("Tick error: " + e.getMessage());
                e.printStackTrace();
            }
        }, 1, 1, TimeUnit.SECONDS);
    
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkInactiveVipMembers();
            } catch (Exception e) {
                System.err.println("VIP inactivity check error: " + e.getMessage());
            }
        }, 1, 24, TimeUnit.HOURS);
    }

    // Get member type info (only points consumption rate)
    private MemberTypeInfo getMemberTypeInfo(int memberId) {
        String sql = "SELECT mt.member_type_name, mt.points_consumption_rate " +
                     "FROM internet_cafe.member m " +
                     "JOIN internet_cafe.member_type mt ON m.member_type_id = mt.member_type_id " +
                     "WHERE m.member_id = ?";
        try (Connection dbCon = new DatabaseConnection().connectDB();
             PreparedStatement pst = dbCon.prepareStatement(sql)) {
            pst.setInt(1, memberId);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) {
                return new MemberTypeInfo(
                    rs.getString("member_type_name"),
                    rs.getInt("points_consumption_rate")
                );
            }
        } catch (Exception e) {
            System.err.println("Error getting member type info: " + e.getMessage());
        }
        return new MemberTypeInfo("normal", 360);
    }
    
    // Get points consumption rate
    private int getPointsConsumptionRate(int memberId) {
        if (cachedPointsRate.containsKey(memberId)) {
            return cachedPointsRate.get(memberId);
        }
        MemberTypeInfo info = getMemberTypeInfo(memberId);
        int rate = info.pointsRate;
        cachedPointsRate.put(memberId, rate);
        return rate;
    }
    
    // Get member tier
    private String getMemberTier(int memberId) {
        MemberTypeInfo info = getMemberTypeInfo(memberId);
        return info.tier;
    }

    private void tickOneSecond() {
        System.out.println("=== SERVER TICK ===");

        System.out.println("Current PCs in maps:");
        for (String pc : clientRemainingSeconds.keySet()) {
            System.out.println("  " + pc +
                " | active=" + clientActive.getOrDefault(pc, false) +
                " | remaining=" + clientRemainingSeconds.get(pc) +
                " | elapsed=" + clientElapsedSeconds.getOrDefault(pc, 0));
        }

        for (String pcName : clientRemainingSeconds.keySet().toArray(new String[0])) {
            boolean active = clientActive.getOrDefault(pcName, false);
            if (!active) {
                continue;
            }

            try {
                Integer memberId = clientMemberIds.get(pcName);
                if (memberId == null) {
                    System.out.println("No member ID for " + pcName + " – deactivating");
                    clientActive.put(pcName, false);
                    continue;
                }

                int secondsPerPoint = getPointsConsumptionRate(memberId);
                if (secondsPerPoint <= 0) secondsPerPoint = 360;

                int elapsed = clientElapsedSeconds.getOrDefault(pcName, 0);
                int initialPoints = clientInitialPoints.getOrDefault(pcName, 0);

                int pointsUsed = elapsed / secondsPerPoint;
                int remainingPoints = Math.max(0, initialPoints - pointsUsed);

                Integer remaining = clientRemainingSeconds.get(pcName);
                if (remaining == null) {
                    remaining = Math.max(0, remainingPoints * secondsPerPoint - (elapsed % secondsPerPoint));
                    clientRemainingSeconds.put(pcName, remaining);
                    System.out.println(pcName + " rem time rebuilt to: " + remaining);
                }

                if (remainingPoints <= 0 && initialPoints > 0) {
                    System.out.println("⚠️ ZERO POINTS for " + pcName + " – initiating force logout");
                    forceLogoutClient(pcName);
                    continue;
                }

                if (remaining > 0) {
                    int newRemaining = remaining - 1;
                    clientRemainingSeconds.put(pcName, newRemaining);

                    int newElapsed = elapsed + 1;
                    clientElapsedSeconds.put(pcName, newElapsed);

                    System.out.println(pcName + " elapsed now: " + newElapsed);

                    updateCardElapsed(pcName, newElapsed);
                    updateClientTimerAsync(pcName, newElapsed);

                    notifyAdminsTimeTick(memberId, remainingPoints, newElapsed);

                    if (newRemaining <= 0) {
                        System.out.println("TIME EXPIRED for " + pcName);
                        forceLogoutClient(pcName);
                    }
                } else {
                    System.out.println("⏳ " + pcName + " remaining=0 but points>0 – forcing logout");
                    forceLogoutClient(pcName);
                }

            } catch (Exception e) {
                System.err.println("‼️ Exception processing tick for " + pcName + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private void updateClientTimerAsync(String pcName, int elapsedSeconds) {
        ClientInterface client = clientMap.get(pcName);
        if (client != null) {
            clientUpdateExecutor.submit(() -> {
                try {
                    System.out.println("Server: Calling client.updateTimer(" + elapsedSeconds + ") for " + pcName);
                    client.updateTimer(elapsedSeconds);
                    System.out.println("Server: client.updateTimer() succeeded for " + pcName);
                } catch (RemoteException e) {
                    System.err.println("Server: Failed to update client " + pcName + ": " + e.getMessage());
                }
            });
        } else {
            System.out.println("Server: No client in map for: " + pcName);
        }
    }

    private void updateCardElapsed(String pcName, int elapsedSeconds) {
        if (controller != null) {
            Platform.runLater(() -> controller.updateCardElapsed(pcName, elapsedSeconds));
        }
    }

    @Override
    public void setClientTimer(String pcName, int seconds) throws RemoteException {
        int remainingSeconds = Math.max(0, seconds);
        clientRemainingSeconds.put(pcName, remainingSeconds);
        clientElapsedSeconds.put(pcName, 0);
        clientActive.put(pcName, true);
        
        System.out.println("setClientTimer: " + pcName + " remainingSeconds=" + remainingSeconds);
        updateCardElapsed(pcName, 0);
        updateClientTimerAsync(pcName, 0);
    }

    @Override
    public int getRemainingTime(String pcName) throws RemoteException {
        return clientRemainingSeconds.getOrDefault(pcName, 0);
    }
    
    @Override
    public int getElapsedTimeForPC(String pcName) throws RemoteException {
        return clientElapsedSeconds.getOrDefault(pcName, 0);
    }
    
    @Override
    public String getCurrentUserForPC(String pcName) throws RemoteException {
        return clientMemberNames.getOrDefault(pcName, "");
    }

    @Override
    public void loginClient(ClientInterface client, String memberName) throws RemoteException {
        String pcName = client.getClientName();
        int memberId = getMemberIdFromDatabase(memberName);
        if (memberId == -1) throw new RemoteException("Member not found");

        clientMap.put(pcName, client);
        clientMemberIds.put(pcName, memberId);
        clientMemberNames.put(pcName, memberName);
        updateDatabaseStatus(pcName, "active");

        int currentPoints = getMemberPointsFromDatabase(memberId);

        if (currentPoints <= 0) {
            System.out.println("⚠️ Login attempt with ZERO points for " + memberName);
            throw new RemoteException("Insufficient points (0 points). Please recharge to continue.");
        }

        MemberTypeInfo typeInfo = getMemberTypeInfo(memberId);

        int remainingSeconds = currentPoints * typeInfo.pointsRate;

        clientInitialPoints.put(pcName, currentPoints);
        clientRemainingSeconds.put(pcName, remainingSeconds);
        clientElapsedSeconds.put(pcName, 0);
        clientActive.put(pcName, true);

        pushTierUpdateToClient(pcName, typeInfo.tier);

        System.out.println("loginClient: " + pcName + " | Member: " + memberName + 
                          " | Points: " + currentPoints + 
                          " | Tier: " + typeInfo.tier + 
                          " | Points Rate: 1 point per " + typeInfo.pointsRate + " seconds (" + (typeInfo.pointsRate/60) + " min)" +
                          " | Total Time Available: " + (remainingSeconds/60) + " minutes");

        if (controller != null) {
            Platform.runLater(() -> {
                controller.updateCardUser(pcName, memberName);
                controller.updateCardElapsed(pcName, 0);
            });
        }

        updateClientTimerAsync(pcName, 0);
    }

    @Override
    public void logoutClient(ClientInterface client) throws RemoteException {
        String pcName = client.getClientName();
        Integer memberId = clientMemberIds.get(pcName);
  
        clientActive.put(pcName, false);
        clientMemberIds.remove(pcName);
        clientRemainingSeconds.put(pcName, 0);
        clientElapsedSeconds.put(pcName, 0);
        clientMemberNames.put(pcName, "");
        clientInitialPoints.put(pcName, 0);
        updateDatabaseStatus(pcName, "online");
        
        System.out.println("logoutClient: " + pcName + " | Session reset to 0");
        
        if (controller != null) {
            Platform.runLater(() -> {
                controller.updateCardUser(pcName, "Available");
                controller.updateCardElapsed(pcName, 0);
            });
        }
        
        if (memberId != null) {
            notifyAdminsMemberDataChanged(memberId);
        }
    }

    @Override
    public void forceLogoutClient(String pcName) throws RemoteException {
        System.out.println("forceLogoutClient called for: " + pcName);
        
        Integer memberId = clientMemberIds.get(pcName);
        ClientInterface client = clientMap.get(pcName);
        if (client != null) {
            ClientInterface finalClient = client;
            clientUpdateExecutor.submit(() -> {
                try {
                    finalClient.forceLogout();
                } catch (RemoteException e) {
                    System.err.println("Error force logging out " + pcName + ": " + e.getMessage());
                }
            });
        }
  
        clientActive.put(pcName, false);
        clientMemberIds.remove(pcName);
        clientRemainingSeconds.put(pcName, 0);
        clientElapsedSeconds.put(pcName, 0);
        clientMemberNames.put(pcName, "");
        clientInitialPoints.put(pcName, 0);
        updateDatabaseStatus(pcName, "online");
        
        System.out.println("forceLogoutClient: " + pcName + " | Session reset to 0");
        
        if (controller != null) {
            Platform.runLater(() -> {
                controller.updateCardUser(pcName, "Available");
                controller.updateCardElapsed(pcName, 0);
            });
        }
        
        if (memberId != null) {
            notifyAdminsMemberDataChanged(memberId);
        }
    }

    @Override
    public void allowSpecificClient(String pcName) throws RemoteException {
        ClientInterface client = clientMap.get(pcName);
        if (client != null) {
            client.showLoginScreen();
            System.out.println("Allowed login for: " + pcName);
        } else {
            System.out.println("Cannot allow login: Client not found for " + pcName);
        }
    }

    @Override
    public void registerClient(ClientInterface client) throws RemoteException {
        try {
            String name = client.getClientName();
            clientMap.put(name, client);
            clientRemainingSeconds.putIfAbsent(name, 0);
            clientElapsedSeconds.putIfAbsent(name, 0);
            clientActive.putIfAbsent(name, false);

            System.out.println("registerClient: " + name + " | Active: false waiting for login");
            updateDatabaseStatus(name, "online");

            if (this.controller != null) {
                Platform.runLater(() -> {
                    controller.loadPC();
                    controller.updateCardElapsed(name, 0);
                    controller.updateCardUser(name, "Ready");
                });
            }
        } catch (Exception e) {
            System.err.println("Error in registerClient: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void unregisterClient(ClientInterface client) throws RemoteException {
        try {
            String name = client.getClientName();
            clientMap.remove(name);
            clientRemainingSeconds.remove(name);
            clientElapsedSeconds.remove(name);
            clientActive.remove(name);
            clientMemberNames.remove(name);
            clientInitialPoints.remove(name);

            updateDatabaseStatus(name, "offline");

            if (this.controller != null) {
                Platform.runLater(() -> {
                    controller.loadPC();
                    controller.updateCardUser(name, "Offline");
                    controller.updateCardElapsed(name, 0);
                });
            }

            System.out.println("Client unregistered: " + name);
        } catch (Exception e) {
            System.err.println("Error in unregisterClient: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int getMemberPointsFromDatabase(int memberId) {
        String sql = "SELECT point FROM internet_cafe.member WHERE member_id = ?";
        try (java.sql.Connection dbCon = new database.DatabaseConnection().connectDB();
             java.sql.PreparedStatement pst = dbCon.prepareStatement(sql)) {
            pst.setInt(1, memberId);
            try (java.sql.ResultSet rs = pst.executeQuery()) {
                if (rs.next()) {
                    int points = rs.getInt("point");
                    System.out.println("Database: " + memberId + " has " + points + " points");
                    return points;
                }
            }
        } catch (Exception e) {
            System.err.println("Error getting points for " + memberId + ": " + e.getMessage());
        }
        return 0;
    }
    
    private void updateMemberPointsInDatabase(int memberId, int points) {
        String sql = "UPDATE internet_cafe.member SET point = ? WHERE member_id = ?";
        try (Connection dbCon = new DatabaseConnection().connectDB();
            PreparedStatement pst = dbCon.prepareStatement(sql)) {
            pst.setInt(1, points);
            pst.setInt(2, memberId);
            pst.executeUpdate();
            System.out.println("Updated points for memberId " + memberId + " to " + points);
        } catch (Exception e) {
            System.err.println("Error updating points: " + e.getMessage());
        }
    }
    
    private void updateMemberTypeInDatabase(int memberId, String tier) {
        String sql = "UPDATE internet_cafe.member SET member_type_id = " +
                     "(SELECT member_type_id FROM internet_cafe.member_type WHERE member_type_name = ?) " +
                     "WHERE member_id = ?";
        try (java.sql.Connection dbCon = new database.DatabaseConnection().connectDB();
             java.sql.PreparedStatement pst = dbCon.prepareStatement(sql)) {
            pst.setString(1, tier);
            pst.setInt(2, memberId);
            pst.executeUpdate();
            System.out.println("Updated tier for " + memberId + " to " + tier);
        } catch (Exception e) {
            System.err.println("Error updating tier for " + memberId + ": " + e.getMessage());
        }
    }
    
    private void updateMemberNameInDatabase(int memberId, String newName) {
        String sql = "UPDATE internet_cafe.member SET member_name = ? WHERE member_id = ?";
        try (java.sql.Connection dbCon = new database.DatabaseConnection().connectDB();
             java.sql.PreparedStatement pst = dbCon.prepareStatement(sql)) {
            pst.setString(1, newName);
            pst.setInt(2, memberId);
            pst.executeUpdate();
            System.out.println("Updated name for " + memberId + " to " + newName);
        } catch (Exception e) {
            System.err.println("Error updating name for " + memberId + ": " + e.getMessage());
        }
    }

    private void updateDatabaseStatus(String pcName, String status) {
        String sql = "UPDATE internet_cafe.computer SET status = ? WHERE model = ?";
        try (java.sql.Connection dbCon = new database.DatabaseConnection().connectDB();
             java.sql.PreparedStatement pst = dbCon.prepareStatement(sql)) {
            pst.setString(1, status);
            pst.setString(2, pcName);
            pst.executeUpdate();
        } catch (Exception e) {
            System.err.println("Database Sync Error: " + e.getMessage());
        }
    }
    
    private void pushTierUpdateToClient(String pcName, String newTier) {
        ClientInterface client = clientMap.get(pcName);
        if (client != null) {
            try {
                client.updateTier(newTier);
                System.out.println("Pushed tier update to client " + pcName + ": " + newTier);
            } catch (RemoteException e) {
                System.err.println("Failed to push tier update: " + e.getMessage());
            }
        }
    }
    
    public void updateMemberFromServer(int memberId, int newPoints, String newTier, String newName) throws RemoteException {
        String pcName = null;
        for (Map.Entry<String, Integer> entry : clientMemberIds.entrySet()) {
            if (entry.getValue() == memberId) {
                pcName = entry.getKey();
                break;
            }
        }
        if (pcName != null) {
            ClientInterface client = clientMap.get(pcName);
            if (client != null) {
                if (newPoints >= 0) client.updatePoints(newPoints);
                if (newTier != null) client.updateTier(newTier);
                if (newName != null) {
                    client.updateName(newName);
                    clientMemberNames.put(pcName, newName);
                }
            }
        }
    }
    
    public void updateMemberPointsAndNotify(int memberId, int newPoints) throws RemoteException {
        updateMemberPointsInDatabase(memberId, newPoints);
        notifyAdmins(memberId, newPoints, null);
        updateMemberFromServer(memberId, newPoints, null, null);

        String pcName = getPCNameByMemberId(memberId);
        if (pcName != null) {
            int elapsed = clientElapsedSeconds.getOrDefault(pcName, 0);
            int secondsPerPoint = getPointsConsumptionRate(memberId);
            int pointsAlreadyUsed = elapsed / secondsPerPoint;
               
            int adjustedInitialPoints = newPoints + pointsAlreadyUsed;
        
            clientInitialPoints.put(pcName, adjustedInitialPoints);
        
            int newRemainingSeconds = (adjustedInitialPoints * secondsPerPoint) - elapsed;
            clientRemainingSeconds.put(pcName, Math.max(0, newRemainingSeconds));
        
            ClientInterface client = clientMap.get(pcName);
            if (client != null) {
                try {
                    client.updatePoints(newPoints);
                } catch (RemoteException e) {
                    System.err.println("Failed to update client points: " + e.getMessage());
                }
            }
        
            System.out.println("Points added for member " + memberId + 
                            " | newPoints=" + newPoints +
                            " | elapsed=" + elapsed + "s" +
                            " | alreadyUsed=" + pointsAlreadyUsed +
                            " | adjustedInitial=" + adjustedInitialPoints);
        }
    
        notifyAdminsMemberDataChanged(memberId);
    }

    public void updateMemberTierAndNotify(int memberId, String newTier) throws RemoteException {
        updateMemberTypeInDatabase(memberId, newTier);
        notifyAdmins(memberId, -1, null);
        updateMemberFromServer(memberId, -1, newTier, null);
        
        String pcName = getPCNameByMemberId(memberId);
        if (pcName != null) {
            int currentPoints = getMemberPointsFromDatabase(memberId);
            int newSecondsPerPoint = getPointsConsumptionRate(memberId);
            int newRemainingSeconds = currentPoints * newSecondsPerPoint;
            clientRemainingSeconds.put(pcName, newRemainingSeconds);
            cachedPointsRate.remove(memberId);
            System.out.println("Recalculated remaining time for " + pcName + 
                             " based on new tier: " + newRemainingSeconds + " seconds (" + (newRemainingSeconds/60) + " minutes)");
        }
    }

    public void updateMemberNameAndNotify(int memberId, String newName) throws RemoteException {
        updateMemberNameInDatabase(memberId, newName);
        notifyAdmins(memberId, -1, newName);
        updateMemberFromServer(memberId, -1, null, newName);
    }
    
    @Override
    public void deductPointsForSale(String pcName, int pointsDeducted) throws RemoteException {
        Integer memberId = clientMemberIds.get(pcName);
        if (memberId == null) {
            System.out.println("No member found for PC: " + pcName);
            return;
        }

        int secondsPerPoint = getPointsConsumptionRate(memberId);
        int elapsed = clientElapsedSeconds.getOrDefault(pcName, 0);
        int initialPoints = clientInitialPoints.getOrDefault(pcName, 0);

        int currentRemainingPoints = Math.max(0, initialPoints - elapsed / secondsPerPoint);
        int newRemainingPoints = Math.max(0, currentRemainingPoints - pointsDeducted);

        int pointsAlreadyUsed = elapsed / secondsPerPoint;
        int newInitialPoints = newRemainingPoints + pointsAlreadyUsed;
        clientInitialPoints.put(pcName, newInitialPoints);

        int newRemainingSeconds = newRemainingPoints * secondsPerPoint - (elapsed % secondsPerPoint);
        clientRemainingSeconds.put(pcName, Math.max(0, newRemainingSeconds));

        System.out.println("🔵 deductPointsForSale: pcName=" + pcName +
                ", pointsDeducted=" + pointsDeducted +
                ", newRemainingPoints=" + newRemainingPoints);

        updateMemberPointsInDatabase(memberId, newRemainingPoints);

        if (memberId != null) {
            String updateSessionSql = "UPDATE internet_cafe.session SET session_total_cost = COALESCE(session_total_cost, 0) + ? " +
                    "WHERE member_id = ? AND end_time IS NULL AND computer_id = " +
                    "(SELECT computer_id FROM internet_cafe.computer WHERE model = ?)";
            try (Connection dbCon = new DatabaseConnection().connectDB();
                 PreparedStatement pst = dbCon.prepareStatement(updateSessionSql)) {
                pst.setInt(1, pointsDeducted);
                pst.setInt(2, memberId);
                pst.setString(3, pcName);
                pst.executeUpdate();
                System.out.println("Updated session_total_cost for sale deduction");
            } catch (SQLException e) {
                System.err.println("Failed to update session cost for sale: " + e.getMessage());
            }
        }

        notifyAdmins(memberId, newRemainingPoints, null);

        ClientInterface client = clientMap.get(pcName);
        if (client != null) {
            try {
                client.updatePoints(newRemainingPoints);
                System.out.println("📡 Pushed points update to client " + pcName + ": " + newRemainingPoints);
            } catch (RemoteException e) {
                System.err.println("Failed to update client points: " + e.getMessage());
            }
        }

        if (newRemainingPoints <= 0) {
            System.out.println("⚠️ Points became ZERO after sale deduction for " + pcName);
            if (client != null) {
                try {
                    client.updatePoints(0);
                    client.forceLogout();
                } catch (RemoteException e) {
                    System.err.println("Failed to force logout: " + e.getMessage());
                }
            }
            clientActive.put(pcName, false);
            clientRemainingSeconds.put(pcName, 0);
            clientElapsedSeconds.put(pcName, 0);
            if (controller != null) {
                Platform.runLater(() -> {
                    controller.updateCardUser(pcName, "Available");
                    controller.updateCardElapsed(pcName, 0);
                });
            }
        }

        notifyAdminsTimeTick(memberId, newRemainingPoints, elapsed);
        notifyAdminsMemberDataChanged(memberId);

        System.out.println("Sale deduction: " + pcName + " lost " + pointsDeducted +
                " points → remaining points = " + newRemainingPoints);
    }
    
    public void shutdown() {
        try {
            scheduler.shutdownNow();
        } catch (Exception e) {
        }
        try {
            clientUpdateExecutor.shutdownNow();
        } catch (Exception e) {
        }
    }

    public Map<String, Integer> getActiveClientsWithPoints() {
        Map<String, Integer> result = new ConcurrentHashMap<>();
        for (String pcName : clientRemainingSeconds.keySet()) {
            int elapsed = clientElapsedSeconds.getOrDefault(pcName, 0);
            int initial = clientInitialPoints.getOrDefault(pcName, 0);
            int secondsPerPoint = 360;
            Integer memberId = clientMemberIds.get(pcName);
            if (memberId != null) {
                secondsPerPoint = getPointsConsumptionRate(memberId);
            }
            int pointsUsed = elapsed / secondsPerPoint; 
            int remaining = Math.max(0, initial - pointsUsed);
            result.put(pcName, remaining);
        }
        return result;
    }

    @Override
    public void registerAdminCallback(AdminCallback callback) throws RemoteException {
        adminCallbacks.add(callback);
        System.out.println("Admin callback registered. Total callbacks: " + adminCallbacks.size());
    }

    @Override
    public void unregisterAdminCallback(AdminCallback callback) throws RemoteException {
        adminCallbacks.remove(callback);
        System.out.println("Admin callback unregistered. Remaining: " + adminCallbacks.size());
    }

    @Override
    public void broadcastMemberUpdate(int memberId, int newPoints, String newName) throws RemoteException {
        for (AdminCallback cb : adminCallbacks) {
            try {
                cb.onMemberUpdated(memberId, newPoints, newName);
            } catch (RemoteException e) {
                adminCallbacks.remove(cb); 
            }
        }
    }

    private void notifyAdmins(int memberId, int newPoints, String newName) {
        try {
            broadcastMemberUpdate(memberId, newPoints, newName);
        } catch (RemoteException e) {
            System.err.println("Failed to notify admins: " + e.getMessage());
        }
    }
    
    private int getMemberIdFromDatabase(String memberName) {
        String sql = "SELECT member_id FROM internet_cafe.member WHERE member_name = ?";
        try (Connection dbCon = new DatabaseConnection().connectDB();
            PreparedStatement pst = dbCon.prepareStatement(sql)) {
            pst.setString(1, memberName);
            ResultSet rs = pst.executeQuery();
            if (rs.next()) return rs.getInt("member_id");
        } catch (Exception e) { 
            e.printStackTrace(); 
        }
        return -1;
    }
       
    private String getPCNameByMemberId(int memberId) {
        for (Map.Entry<String, Integer> entry : clientMemberIds.entrySet()) {
            if (entry.getValue() == memberId) return entry.getKey();
        }
        return null;
    }
    
    @Override
    public void registerInventoryCallback(InventoryCallback callback) throws RemoteException {
        inventoryCallbacks.add(callback);
        System.out.println("Inventory callback registered");
    }

    @Override
    public void unregisterInventoryCallback(InventoryCallback callback) throws RemoteException {
        inventoryCallbacks.remove(callback);
    }

    @Override
    public void notifyInventoryChanged() throws RemoteException {
        System.out.println("Inventory changed – notifying " + inventoryCallbacks.size() + " clients");
        for (InventoryCallback cb : inventoryCallbacks) {
            try {
                cb.onInventoryChanged();
            } catch (RemoteException e) {
                inventoryCallbacks.remove(cb);
            }
        }
    }
    
    private void notifyAdminsTimeTick(int memberId, int remainingPoints, int elapsedSeconds) {
        if (!clientMemberIds.containsValue(memberId)) {
            return; 
        }

        for (AdminCallback cb : adminCallbacks) {
            try {
                cb.onMemberTimeTick(memberId, remainingPoints, elapsedSeconds);
            } catch (RemoteException e) {
                adminCallbacks.remove(cb);
            }
        }
    }
    
    @Override
    public int getLiveRemainingPoints(int memberId) throws RemoteException {
        String pcName = getPCNameByMemberId(memberId);
        if (pcName == null) return -1;
    
        int elapsed = clientElapsedSeconds.getOrDefault(pcName, 0);
        int secondsPerPoint = getPointsConsumptionRate(memberId);
        int pointsUsed = elapsed / secondsPerPoint;                
        int initial = clientInitialPoints.getOrDefault(pcName, 0);
        int remaining = Math.max(0, initial - pointsUsed);
    
        System.out.println("Live points for member " + memberId + ": initial=" + initial +
                        ", elapsed=" + elapsed + "s, used=" + pointsUsed + ", remaining=" + remaining);
        return remaining;
    }
    
    @Override
    public void requestLogin(String pcName) throws RemoteException {
        System.out.println("Login requested by: " + pcName);
        notifyAdminsLoginRequest(pcName);
    }

    private void notifyAdminsLoginRequest(String pcName) {
        for (AdminCallback cb : adminCallbacks) {
            try {
                cb.onLoginRequest(pcName);
            } catch (RemoteException e) {
                adminCallbacks.remove(cb);
            }
        }
    }
    
    @Override
    public void notifySaleCompleted(String pcName, int saleId, double amount) throws RemoteException {
        String memberName = clientMemberNames.getOrDefault(pcName, "Guest");
        notifyAdminsPurchase(saleId, memberName, amount, pcName);
    }

    private void notifyAdminsPurchase(int saleId, String memberName, double amount, String pcName) {
        for (AdminCallback cb : adminCallbacks) {
            try {
                cb.onMemberPurchase(saleId, memberName, amount, pcName);
            } catch (Exception e) {
                adminCallbacks.remove(cb);
                System.err.println("Removed dead admin callback: " + e.getMessage());
            }
        }
    }
    
    private void notifyAdminsMemberDataChanged(int memberId) {
        System.out.println("Notifying admins of data change for member: " + memberId);
        for (AdminCallback cb : adminCallbacks) {
            try {
                cb.onMemberDataChanged(memberId);
            } catch (Exception e) {
                adminCallbacks.remove(cb);
                System.err.println("Removed dead admin callback: " + e.getMessage());
            }
        }
    }
    
    private static class MemberTypeInfo {
        final String tier;
        final int pointsRate;
        
        MemberTypeInfo(String tier, int pointsRate) {
            this.tier = tier;
            this.pointsRate = pointsRate;
        }
    }
    
    @Override
    public void notifyPointsDeducted(String pcName, int pointsDeducted, int newTotalPoints) throws RemoteException {
        Integer memberId = clientMemberIds.get(pcName);
        if (memberId == null) return;

        int secondsPerPoint = getPointsConsumptionRate(memberId);
        int elapsed = clientElapsedSeconds.getOrDefault(pcName, 0);

        int pointsAlreadyUsed = elapsed / secondsPerPoint;

        int newInitialPoints = newTotalPoints + pointsAlreadyUsed;
        clientInitialPoints.put(pcName, newInitialPoints);

        int newRemainingSeconds = (newTotalPoints * secondsPerPoint) - (elapsed % secondsPerPoint);
        clientRemainingSeconds.put(pcName, Math.max(0, newRemainingSeconds));

        System.out.println("Server adjusted: newInitialPoints=" + newInitialPoints +
                           ", newRemainingSeconds=" + newRemainingSeconds);

        notifyAdminsTimeTick(memberId, newTotalPoints, elapsed);
        notifyAdminsMemberDataChanged(memberId);

        if (newTotalPoints <= 0) {
            ClientInterface client = clientMap.get(pcName);
            if (client != null) client.forceLogout();
        }
    }

    public void checkAndUpdateMemberTierAfterPointPurchase(int memberId, double pointsPurchased) {
        try {
            String currentTier = getMemberTier(memberId);

            if (pointsPurchased >= 450 && !"vip".equals(currentTier)) {
                updateMemberTypeInDatabase(memberId, "vip");
                notifyAdmins(memberId, -1, null);
                updateMemberFromServer(memberId, -1, "vip", null);

                String pcName = getPCNameByMemberId(memberId);
                if (pcName != null) {
                    int currentPoints = getMemberPointsFromDatabase(memberId);
                    int vipRate = getPointsConsumptionRate(memberId);
                    int newRemainingSeconds = currentPoints * vipRate;
                    clientRemainingSeconds.put(pcName, newRemainingSeconds);
                    cachedPointsRate.remove(memberId);
                    notifyAdminsTierChanged(memberId, "vip");

                    ClientInterface client = clientMap.get(pcName);
                    if (client != null) {
                        try {
                            client.updateTier("vip");
                        } catch (RemoteException e) {
                            System.err.println("Failed to push VIP update to client: " + e.getMessage());
                        }
                    }
                }

                System.out.println("Member " + memberId + " upgraded to VIP (purchased " + pointsPurchased + " points)");
            }
        } catch (Exception e) {
            System.err.println("Error in tier upgrade: " + e.getMessage());
            e.printStackTrace();
        }
    }
   
    public void checkInactiveVipMembers() {
        String sql = "SELECT m.member_id FROM internet_cafe.member m " +
                     "JOIN internet_cafe.member_type mt ON m.member_type_id = mt.member_type_id " +
                     "WHERE mt.member_type_name = 'vip' " +
                     "AND m.member_id NOT IN (" +
                     "    SELECT DISTINCT member_id FROM internet_cafe.session " +
                     "    WHERE session_date >= DATE_SUB(NOW(), INTERVAL 2 MONTH)" +
                     ")";

        try (Connection dbCon = new DatabaseConnection().connectDB();
             PreparedStatement pst = dbCon.prepareStatement(sql);
             ResultSet rs = pst.executeQuery()) {

            while (rs.next()) {
                int memberId = rs.getInt("member_id");

                updateMemberTypeInDatabase(memberId, "normal");
                notifyAdmins(memberId, -1, null);
                updateMemberFromServer(memberId, -1, "normal", null);

                String pcName = getPCNameByMemberId(memberId);
                if (pcName != null) {
                    int currentPoints = getMemberPointsFromDatabase(memberId);
                    int normalRate = 360;
                    int newRemainingSeconds = currentPoints * normalRate;
                    clientRemainingSeconds.put(pcName, newRemainingSeconds);
                    cachedPointsRate.remove(memberId);
                    notifyAdminsTierChanged(memberId, "normal");

                    ClientInterface client = clientMap.get(pcName);
                    if (client != null) {
                        try {
                            client.updateTier("normal");
                        } catch (RemoteException e) {
                            System.err.println("Failed to push downgrade to client: " + e.getMessage());
                        }
                    }
                }

                System.out.println("VIP expired for member " + memberId + " - inactive for 2 months, downgraded to normal");
            }
        } catch (SQLException | RemoteException e) {
            System.err.println("Error checking inactive VIPs: " + e.getMessage());
        }
    }
    
    private void notifyAdminsTierChanged(int memberId, String newTier) {
        for (AdminCallback cb : adminCallbacks) {
            try {
                cb.onMemberTierChanged(memberId, newTier);
            } catch (Exception e) {
                adminCallbacks.remove(cb);
            }
        }
    }
    
    @Override
    public void shutDownClient(String pcName) throws RemoteException {
        System.out.println("Initiating full shutdown for: " + pcName);

        if (clientActive.getOrDefault(pcName, false)) {
            forceLogoutClient(pcName);
        }

        ClientInterface client = clientMap.get(pcName);
        if (client != null) {
            try {
                client.shutdownPC();
            } catch (RemoteException e) {
                System.err.println("Could not send shutdown command to " + pcName + ": " + e.getMessage());
            }
        }

        clientMap.remove(pcName);
        clientRemainingSeconds.remove(pcName);
        clientElapsedSeconds.remove(pcName);
        clientActive.remove(pcName);
        clientMemberIds.remove(pcName);
        clientMemberNames.remove(pcName);
        clientInitialPoints.remove(pcName);

        updateDatabaseStatus(pcName, "offline");

        if (controller != null) {
            Platform.runLater(() -> {
                controller.updateCardUser(pcName, "Offline");
                controller.updateCardElapsed(pcName, 0);
                controller.loadPC();
            });
        }

        System.out.println("PC " + pcName + " fully shut down.");
    }
    
    @Override
public void registerGameCallback(GameCallback callback) throws RemoteException {
    gameCallbacks.add(callback);
}

@Override
public void unregisterGameCallback(GameCallback callback) throws RemoteException {
    gameCallbacks.remove(callback);
}

@Override
public void notifyGamesChanged() throws RemoteException {
    for (GameCallback cb : gameCallbacks) {
        try {
            cb.onGamesChanged();
        } catch (RemoteException e) {
            gameCallbacks.remove(cb); // remove dead callback
        }
    }
}

    @Override
    public byte[] getImageBytes(String imageName) throws RemoteException {
        if (imageName == null || imageName.isEmpty()) {
            return null;
        }
        try {
            return Files.readAllBytes(IMAGE_DIR.resolve(imageName));
        } catch (IOException e) {
            System.err.println("Server image not found: " + imageName);
            return null;
        }
    }
}