package database;

import member_controllers.Game;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class GameDAO {
    private static final String TABLE_NAME = "internet_cafe.game";
    private static final String COL_ID = "game_id";
    private static final String COL_TITLE = "game_name";
    private static final String COL_IMAGE = "image";

    public static List<Game> getAllGames(Connection connection) throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Database connection is null or closed.");
        }

        String sql = "SELECT " + COL_ID + ", " + COL_TITLE + ", " + COL_IMAGE
                + " FROM " + TABLE_NAME
                + " ORDER BY " + COL_ID;

        List<Game> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String gameId = rs.getString(COL_ID);
                String title = emptyToNull(rs.getString(COL_TITLE));
                String imageName = emptyToNull(rs.getString(COL_IMAGE));

                result.add(new Game(
                        title,
                        null,
                        imageName,
                        null,
                        gameId,
                        null
                ));
            }
        } catch (SQLException ex) {
            System.err.println("GameDAO.getAllGames SQL error: " + ex.getMessage());
            throw ex;
        }

        return result;
    }

    public static Game getGameById(Connection connection, String gameId) throws SQLException {
        if (connection == null || connection.isClosed()) 
            throw new SQLException("Database connection is null or closed.");
        
        String sql = "SELECT " + COL_ID + ", " + COL_TITLE + ", " + COL_IMAGE
                + " FROM " + TABLE_NAME + " WHERE " + COL_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, gameId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String title = emptyToNull(rs.getString(COL_TITLE));
                    String imageName = emptyToNull(rs.getString(COL_IMAGE));
                    return new Game(title, null, imageName, null, gameId, null);
                }
            }
        }
        return null;
    }

    public static List<Game> searchGames(Connection connection, String searchTerm) throws SQLException {
        if (connection == null || connection.isClosed()) 
            throw new SQLException("Database connection is null or closed.");
        
        String sql = "SELECT " + COL_ID + ", " + COL_TITLE + ", " + COL_IMAGE
                + " FROM " + TABLE_NAME + " WHERE " + COL_TITLE + " LIKE ? ORDER BY " + COL_ID;
        List<Game> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, "%" + searchTerm + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String gameId = rs.getString(COL_ID);
                    String title = emptyToNull(rs.getString(COL_TITLE));
                    String imageName = emptyToNull(rs.getString(COL_IMAGE));
                    result.add(new Game(title, null, imageName, null, gameId, null));
                }
            }
        }
        return result;
    }

    private static String emptyToNull(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}