package admin_controllers;


import java.rmi.Remote;
import java.rmi.RemoteException;

public interface InventoryCallback extends Remote {
    void onInventoryChanged() throws RemoteException;
}