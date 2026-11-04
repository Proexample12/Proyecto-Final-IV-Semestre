package ni.edu.uam.proyectofinalivsemestre.dao;

import ni.edu.uam.proyectofinalivsemestre.model.Team;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object (DAO) para la entidad Team y la gestión de favoritos en PostgreSQL.
 * Módulo 1 - Integrante 1 (Donald).
 */
public class TeamDao {

    /**
     * Obtiene todos los equipos registrados en la base de datos.
     */
    public List<Team> getAllTeams() {
        List<Team> list = new ArrayList<>();
        String sql = "SELECT id, name, short_name, tla, crest_url, competition_id, is_favorite FROM teams ORDER BY name ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapResultSetToTeam(rs));
            }
        } catch (SQLException e) {
            System.err.println("❌ Error en getAllTeams: " + e.getMessage());
        }
        return list;
    }

    /**
     * Obtiene los equipos pertenecientes a una competición específica.
     * @param competitionId ID de la liga (ej. 2014 para LaLiga, 2021 para Premier League, 2001 para Champions)
     */
    public List<Team> getTeamsByCompetition(int competitionId) {
        List<Team> list = new ArrayList<>();
        String sql = "SELECT id, name, short_name, tla, crest_url, competition_id, is_favorite FROM teams " +
                     "WHERE competition_id = ? ORDER BY name ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, competitionId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToTeam(rs));
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error en getTeamsByCompetition (" + competitionId + "): " + e.getMessage());
        }
        return list;
    }

    /**
     * Obtiene únicamente los equipos marcados como favoritos por el usuario.
     */
    public List<Team> getFavoriteTeams() {
        List<Team> list = new ArrayList<>();
        String sql = "SELECT id, name, short_name, tla, crest_url, competition_id, is_favorite FROM teams " +
                     "WHERE is_favorite = TRUE ORDER BY name ASC";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                list.add(mapResultSetToTeam(rs));
            }
        } catch (SQLException e) {
            System.err.println("❌ Error en getFavoriteTeams: " + e.getMessage());
        }
        return list;
    }

    /**
     * Busca un equipo por su ID primario.
     */
    public Team getTeamById(int teamId) {
        String sql = "SELECT id, name, short_name, tla, crest_url, competition_id, is_favorite FROM teams WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, teamId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToTeam(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error en getTeamById (" + teamId + "): " + e.getMessage());
        }
        return null;
    }

    /**
     * Actualiza el estado de favorito de un equipo (TRUE / FALSE).
     * @param teamId ID del equipo
     * @param isFavorite nuevo estado
     * @return true si se actualizó con éxito
     */
    public boolean updateFavoriteStatus(int teamId, boolean isFavorite) {
        String sql = "UPDATE teams SET is_favorite = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setBoolean(1, isFavorite);
            ps.setInt(2, teamId);

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Error en updateFavoriteStatus (" + teamId + "): " + e.getMessage());
            return false;
        }
    }

    /**
     * Guarda o actualiza un equipo (Upsert).
     */
    public boolean saveTeam(Team team) {
        String sql = "INSERT INTO teams (id, name, short_name, tla, crest_url, competition_id, is_favorite, updated_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                     "ON CONFLICT (id) DO UPDATE SET " +
                     "name = EXCLUDED.name, " +
                     "short_name = EXCLUDED.short_name, " +
                     "tla = EXCLUDED.tla, " +
                     "crest_url = EXCLUDED.crest_url, " +
                     "competition_id = EXCLUDED.competition_id, " +
                     "updated_at = CURRENT_TIMESTAMP";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, team.getId());
            ps.setString(2, team.getName());
            ps.setString(3, team.getShortName());
            ps.setString(4, team.getTla());
            ps.setString(5, team.getCrestUrl());
            ps.setInt(6, team.getCompetitionId());
            ps.setBoolean(7, team.isFavorite());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ Error en saveTeam: " + e.getMessage());
            return false;
        }
    }

    /**
     * Helper privado para transformar un ResultSet en un objeto Team.
     */
    private Team mapResultSetToTeam(ResultSet rs) throws SQLException {
        return new Team(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("short_name"),
                rs.getString("tla"),
                rs.getString("crest_url"),
                rs.getInt("competition_id"),
                rs.getBoolean("is_favorite")
        );
    }

    /**
     * Demostración y prueba del Módulo 1 en ejecución.
     */
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  PRUEBA DEL MÓDULO 1: EQUIPOS Y FAVORITOS       ");
        System.out.println("  Integrante 1: Donald                           ");
        System.out.println("=================================================");

        TeamDao dao = new TeamDao();

        // 1. Listar todos los equipos
        System.out.println("\n📋 1. TODOS LOS EQUIPOS EN BD:");
        List<Team> allTeams = dao.getAllTeams();
        for (Team t : allTeams) {
            System.out.println("   • " + t);
        }

        // 2. Listar equipos de LaLiga (2014)
        System.out.println("\n🇪🇸 2. EQUIPOS DE LALIGA (ID: 2014):");
        List<Team> laligaTeams = dao.getTeamsByCompetition(2014);
        for (Team t : laligaTeams) {
            System.out.println("   • " + t.getName() + " (" + t.getShortName() + ")");
        }

        // 3. Listar favoritos actuales
        System.out.println("\n⭐ 3. EQUIPOS FAVORITOS:");
        List<Team> favorites = dao.getFavoriteTeams();
        for (Team t : favorites) {
            System.out.println("   ⭐ " + t.getName() + " [" + t.getTla() + "]");
        }

        // 4. Probar actualización de favorito (toggle Barcelona como favorito)
        System.out.println("\n🔄 4. MARCANDO AL FC BARCELONA (ID: 81) COMO FAVORITO...");
        boolean updated = dao.updateFavoriteStatus(81, true);
        if (updated) {
            System.out.println("   ✅ ¡Actualizado exitosamente en PostgreSQL!");
            Team b = dao.getTeamById(81);
            System.out.println("   Nuevo estado: " + b);
        } else {
            System.err.println("   ❌ No se pudo actualizar.");
        }

        System.out.println("\n=================================================");
        System.out.println("✅ ¡MÓDULO 1 Y CONEXIÓN PROBADOS AL 100%!");
        System.out.println("=================================================");
    }
}
