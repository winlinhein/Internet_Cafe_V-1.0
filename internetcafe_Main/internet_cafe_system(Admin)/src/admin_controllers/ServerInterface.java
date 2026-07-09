package admin_controllers;

import member_controllers.ClientInterface;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServerInterface extends Remote {
    // Connection Management
    void registerClient(ClientInterface client) throws RemoteException;
    void unregisterClient(ClientInterface client) throws RemoteException;
    
    // Session Management
    void loginClient(ClientInterface client, String memberName) throws RemoteException;
    void logoutClient(ClientInterface client) throws RemoteException;
    
    // Admin Controls
    void allowSpecificClient(String pcName) throws RemoteException;
    void setClientTimer(String pcName, int seconds) throws RemoteException;  
    void forceLogoutClient(String pcName) throws RemoteException;  
    void shutDownClient(String pcName) throws RemoteException;

    // Data Retrieval
    int getRemainingTime(String pcName) throws RemoteException;      
    int getElapsedTimeForPC(String pcName) throws RemoteException;
    String getCurrentUserForPC(String pcName) throws RemoteException;
    byte[] getImageBytes(String imageName) throws RemoteException;
    
    // Admin Callbacks
    void registerAdminCallback(AdminCallback callback) throws RemoteException;
    void unregisterAdminCallback(AdminCallback callback) throws RemoteException;
    void broadcastMemberUpdate(int memberId, int newPoints, String newName) throws RemoteException;
    
    // Member Management
    void updateMemberPointsAndNotify(int memberId, int newPoints) throws RemoteException;
    void updateMemberNameAndNotify(int memberId, String newName) throws RemoteException;
    void updateMemberTierAndNotify(int memberId, String newTier) throws RemoteException;
    void deductPointsForSale(String pcName, int pointsDeducted) throws RemoteException;
    int getLiveRemainingPoints(int memberId) throws RemoteException;
    
    // Inventory Callbacks
    void registerInventoryCallback(InventoryCallback callback) throws RemoteException;
    void unregisterInventoryCallback(InventoryCallback callback) throws RemoteException;
    void notifyInventoryChanged() throws RemoteException;
    
    // Login & Sales
    void requestLogin(String pcName) throws RemoteException;
    void notifySaleCompleted(String pcName, int saleId, double amount) throws RemoteException;
    void notifyPointsDeducted(String pcName, int pointsDeducted, int newTotalPoints) throws RemoteException;
    public void checkAndUpdateMemberTierAfterPointPurchase(int memberId, double pointsPurchased) throws RemoteException;
    
    // Inside ServerInterface, add:
    void registerGameCallback(GameCallback callback) throws RemoteException;
    void unregisterGameCallback(GameCallback callback) throws RemoteException;
    void notifyGamesChanged() throws RemoteException;
}