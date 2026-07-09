package member_controllers;

import admin_controllers.ServerInterface;
import java.sql.Connection;

public interface InitializableController {
    void setServer(ServerInterface server);
    void setClient(ClientImpl client);
    void setDatabaseConnection(Connection con);
    default void replayEntranceAnimations() {}
    /** Hide all animated card/panel nodes instantly (no animation). Called before
     *  the page fade-in so cards are invisible during the transition and only
     *  appear via the pop-in after the page is fully visible. */
    default void preHideForEntrance() {}
}