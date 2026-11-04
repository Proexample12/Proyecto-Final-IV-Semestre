package ni.edu.uam.proyectofinalivsemestre.model;

/**
 * Representa un equipo de fútbol dentro de las ligas soportadas
 * (LaLiga, Premier League o Champions League).
 * Módulo 1 - Integrante 1 (Donald).
 */
public class Team {

    private int id;
    private String name;
    private String shortName;
    private String tla;              // Siglas (ej: 'RMA', 'BAR', 'MCI')
    private String crestUrl;         // URL del escudo/logo
    private int competitionId;       // ID de la liga (2014, 2021, 2001)
    private boolean isFavorite;      // Marcador de favorito del usuario

    public Team() {
    }

    public Team(int id, String name, String shortName, String tla, String crestUrl, int competitionId, boolean isFavorite) {
        this.id = id;
        this.name = name;
        this.shortName = shortName;
        this.tla = tla;
        this.crestUrl = crestUrl;
        this.competitionId = competitionId;
        this.isFavorite = isFavorite;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getTla() {
        return tla;
    }

    public void setTla(String tla) {
        this.tla = tla;
    }

    public String getCrestUrl() {
        return crestUrl;
    }

    public void setCrestUrl(String crestUrl) {
        this.crestUrl = crestUrl;
    }

    public int getCompetitionId() {
        return competitionId;
    }

    public void setCompetitionId(int competitionId) {
        this.competitionId = competitionId;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    @Override
    public String toString() {
        return String.format("%s [%s] (ID: %d, Liga: %d, Favorito: %s)",
                name, tla, id, competitionId, isFavorite ? "⭐ SÍ" : "NO");
    }
}
