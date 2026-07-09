package admin_controllers;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface GameCallback extends Remote {
    void onGamesChanged() throws RemoteException;
}