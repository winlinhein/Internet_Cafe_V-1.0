package admin_controllers;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AdminCallback extends Remote {
    void onMemberUpdated(int memberId, int newPoints, String newName) throws RemoteException;
    void onMemberTimeTick(int memberId, int remainingPoints, int elapsedSeconds) throws RemoteException;
    void onLoginRequest(String pcName) throws RemoteException;
    void onMemberPurchase(int saleId, String memberName, double amountSpent, String pcName) throws RemoteException;
    void onMemberDataChanged(int memberId) throws RemoteException; 
}