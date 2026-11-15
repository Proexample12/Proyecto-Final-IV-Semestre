package ni.edu.uam.proyectofinalivsemestre.service.apifootball;

import java.util.List;

/**
 * DTOs inmutables (Java Records) para parsear las respuestas JSON de API-Football v3.
 * La API envuelve todos los datos en {"response": [...]} por lo que se modela
 * un envelope genérico y luego los records específicos de cada endpoint.
 *
 * Estructura base de API-Football v3:
 * {
 *   "get": "fixtures/lineups",
 *   "errors": [],
 *   "results": 2,
 *   "response": [ ... ]
 * }
 */
public final class ApiFootballDtos {

    // Bloquea instanciación de la clase contenedora
    private ApiFootballDtos() {}

    // =========================================================================
    // ENVELOPE GENÉRICO (Raíz de toda respuesta de API-Football)
    // =========================================================================

    /** Envelope de respuesta de /fixtures/lineups */
    public record LineupResponse(List<LineupEquipo> response) {}

    /** Envelope de respuesta de /fixtures/events */
    public record EventResponse(List<EventoDto> response) {}

    /** Envelope de respuesta de /fixtures/statistics */
    public record StatsResponse(List<EstadisticasEquipo> response) {}

    // =========================================================================
    // DTOs DE ALINEACIONES (/fixtures/lineups?fixture={id})
    // =========================================================================

    /**
     * Alineación completa de un equipo en un partido.
     * Incluye datos del equipo, formación, titulares y suplentes.
     */
    public record LineupEquipo(
            TeamInfo team,
            CoachInfo coach,
            String formation,
            List<JugadorEnCancha> startXI,
            List<JugadorEnCancha> substitutes
    ) {}

    /**
     * Envoltorio de un jugador en la lista startXI o substitutes.
     * API-Football encapsula cada jugador en un objeto {"player": {...}}
     */
    public record JugadorEnCancha(JugadorLineup player) {}

    /**
     * Datos de un jugador dentro de la alineación.
     * "pos": G=Portero, D=Defensa, M=Mediocampista, F=Delantero.
     * "grid": posición táctica en formato "fila:columna" (ej: "2:3"), null para suplentes.
     */
    public record JugadorLineup(
            Long id,
            String name,
            Integer number,
            String pos,
            String grid
    ) {}

    /** Información básica del equipo */
    public record TeamInfo(Long id, String name, String logo) {}

    /** Información del entrenador */
    public record CoachInfo(Long id, String name, String photo) {}

    // =========================================================================
    // DTOs DE EVENTOS (/fixtures/events?fixture={id})
    // =========================================================================

    /**
     * Evento ocurrido durante el partido (gol, tarjeta, sustitución, etc.)
     *
     * Tipos de evento (campo "type"):
     *   - "Goal"  → gol anotado
     *   - "Card"  → tarjeta (detail: "Yellow Card", "Red Card")
     *   - "subst" → sustitución (player=entra, assist=sale)
     *   - "Var"   → revisión VAR
     */
    public record EventoDto(
            TiempoEvento time,
            TeamInfo team,
            JugadorEvento player,  // Jugador principal del evento (en subst: el que ENTRA)
            JugadorEvento assist,  // Asistente o saliente (en subst: el que SALE)
            String type,
            String detail,
            String comments
    ) {}

    /** Tiempo del evento dentro del partido */
    public record TiempoEvento(
            Integer elapsed,     // Minuto del partido (1–90+)
            Integer extra        // Minutos extra de tiempo adicional (puede ser null)
    ) {}

    /**
     * Referencia a un jugador dentro de un evento.
     * En sustituciones: 'player' = quien entra, 'assist' = quien sale.
     * En tarjetas o goles sin asistencia: 'assist' tiene id y name como null.
     */
    public record JugadorEvento(Long id, String name) {}

    // =========================================================================
    // DTOs DE ESTADÍSTICAS (/fixtures/statistics?fixture={id})
    // =========================================================================

    /**
     * Bloque de estadísticas de un equipo para un partido.
     * Contiene una lista de pares {type, value} con cada estadística.
     */
    public record EstadisticasEquipo(
            TeamInfo team,
            List<StatItem> statistics
    ) {}

    /**
     * Ítem individual de estadística.
     * El campo 'value' puede ser String (ej: "55%"), Integer o null.
     * Se mantiene como String para manejo flexible; parsear a número si se necesita.
     */
    public record StatItem(String type, Object value) {}

    // =========================================================================
    // MODELO DE PRESENTACIÓN PARA EL MOTOR DE RATINGS
    // =========================================================================

    /**
     * Resumen estructurado de un partido, listo para ser consumido por el
     * motor de calificaciones de jugadores que implementará el equipo.
     * Este record agrega los 3 endpoints en un solo objeto coherente.
     */
    public record PartidoDetalle(
            long fixtureId,
            List<LineupEquipo> alineaciones,
            List<EventoDto> eventos,
            List<EstadisticasEquipo> estadisticas
    ) {}
}
