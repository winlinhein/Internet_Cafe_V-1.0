package member_controllers;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ClientInterface extends Remote {
    String getClientName() throws RemoteException;
    String getMemberName() throws RemoteException;
    void showLoginScreen() throws RemoteException;
    void updateTimer(int elapsedSeconds) throws RemoteException;
    void forceLogout() throws RemoteException;
    void updatePoints(int newPoints) throws RemoteException;
    void updateTier(String newTier) throws RemoteException;
    void updateName(String newName) throws RemoteException;
     void shutdownPC() throws RemoteException;
}