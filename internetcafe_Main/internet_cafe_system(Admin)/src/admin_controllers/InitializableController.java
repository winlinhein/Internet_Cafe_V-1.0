package admin_controllers;

import member_controllers.ClientInterface;
import java.sql.Connection;

public interface InitializableController {
    void setServer(ServerInterface server);
    void setClient(ClientInterface client);  
    void setDatabaseConnection(Connection con);
}