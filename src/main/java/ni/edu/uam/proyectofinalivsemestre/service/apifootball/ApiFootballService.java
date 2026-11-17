package ni.edu.uam.proyectofinalivsemestre.service.apifootball;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import ni.edu.uam.proyectofinalivsemestre.config.LocalConfig;
import ni.edu.uam.proyectofinalivsemestre.service.apifootball.ApiFootballDtos.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * =============================================================================
 * ApiFootballService — Cliente HTTP para API-Football v3 (API-Sports)
 * =============================================================================
 * Fuente: https://v3.football.api-sports.io/
 * Plan:   Free Tier — 100 peticiones/día, 10 peticiones/minuto
 *
 * Rol en el proyecto:
 *   Fuente SECUNDARIA complementando a football-data.org para obtener
 *   información detallada de partidos: alineaciones, eventos y estadísticas.
 *   Los datos crudos son consumidos por el motor de ratings del equipo.
 *
 * Carga de API Key (en orden de prioridad):
 *   1. Variable de entorno:   API_FOOTBALL_KEY
 *   2. Propiedad de JVM:      -Dapi.football.key=...
 *   3. Archivo local (Git-ignorado): config.local.properties → API_FOOTBALL_KEY=...
 *
 * @author Integrante 2 (Rommel Miranda) — Módulo de Conectividad API
 */
public final class ApiFootballService {

    // -------------------------------------------------------------------------
    // Constantes
    // -------------------------------------------------------------------------
    private static final String BASE_URL   = "https://v3.football.api-sports.io/";
    private static final String HOST       = "v3.football.api-sports.io";
    private static final Duration TIMEOUT  = Duration.ofSeconds(20);

    // -------------------------------------------------------------------------
    // Clientes HTTP y JSON
    // -------------------------------------------------------------------------
    private static final ni.edu.uam.proyectofinalivsemestre.service.RequestBudget CUOTA = new ni.edu.uam.proyectofinalivsemestre.service.RequestBudget();
    private final HttpClient  httpClient;
    private final Gson        gson;

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public ApiFootballService() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(TIMEOUT)
                .build();

        // Gson especial: el campo "value" de StatItem puede ser String, Number o null.
        this.gson = new GsonBuilder()
                .registerTypeAdapter(StatItem.class, new StatItemDeserializer())
                .create();
    }

    // =========================================================================
    // API PÚBLICA — Métodos de Consulta
    // =========================================================================

    /**
     * Obtiene las alineaciones de ambos equipos para un partido dado.
     * Endpoint: GET /fixtures/lineups?fixture={fixtureId}
     *
     * Incluye formación táctica (ej: "4-3-3"), los 11 titulares
     * y hasta 12 jugadores en la banca.
     *
     * @param fixtureId ID del partido en API-Football
     * @return Lista con 2 elementos (alineación local y visitante).
     *         Lista vacía si el partido no tiene alineaciones publicadas aún.
     * @throws ApiFootballException Si hay error de red, autenticación o cuota agotada.
     */
    public List<LineupEquipo> obtenerAlineaciones(long fixtureId) throws ApiFootballException {
        String json = get("fixtures/lineups?fixture=" + fixtureId);
        LineupResponse resp = gson.fromJson(json, LineupResponse.class);
        return Objects.requireNonNullElse(resp.response(), Collections.emptyList());
    }

    /**
     * Obtiene todos los eventos ocurridos en un partido.
     * Endpoint: GET /fixtures/events?fixture={fixtureId}
     *
     * Incluye goles, tarjetas y sustituciones con el minuto exacto.
     * Para sustituciones: 'player' = quien ENTRA, 'assist' = quien SALE.
     *
     * @param fixtureId ID del partido en API-Football
     * @return Lista de eventos ordenados por minuto.
     * @throws ApiFootballException Si hay error de red o de autenticación.
     */
    public List<EventoDto> obtenerEventos(long fixtureId) throws ApiFootballException {
        String json = get("fixtures/events?fixture=" + fixtureId);
        EventResponse resp = gson.fromJson(json, EventResponse.class);
        return Objects.requireNonNullElse(resp.response(), Collections.emptyList());
    }

    /**
     * Obtiene las estadísticas de ambos equipos para un partido dado.
     * Endpoint: GET /fixtures/statistics?fixture={fixtureId}
     *
     * Incluye posesión, tiros totales, tiros al arco, faltas, córners,
     * fueras de juego y pases completados entre otros.
     *
     * @param fixtureId ID del partido en API-Football
     * @return Lista con 2 elementos (estadísticas local y visitante).
     * @throws ApiFootballException Si hay error de red o de autenticación.
     */
    public List<EstadisticasEquipo> obtenerEstadisticas(long fixtureId) throws ApiFootballException {
        String json = get("fixtures/statistics?fixture=" + fixtureId);
        StatsResponse resp = gson.fromJson(json, StatsResponse.class);
        return Objects.requireNonNullElse(resp.response(), Collections.emptyList());
    }

    /**
     * Conveniencia: agrega los 3 endpoints en un solo objeto.
     * Consume 3 de las 100 peticiones diarias del plan Free.
     *
     * @param fixtureId ID del partido en API-Football
     * @return {@link PartidoDetalle} con alineaciones, eventos y estadísticas.
     * @throws ApiFootballException Si alguna consulta falla.
     */
    public PartidoDetalle obtenerDetalleCompleto(long fixtureId) throws ApiFootballException {
        return new PartidoDetalle(
                fixtureId,
                obtenerAlineaciones(fixtureId),
                obtenerEventos(fixtureId),
                obtenerEstadisticas(fixtureId)
        );
    }

    // =========================================================================
    // HTTP Interno
    // =========================================================================

    /**
     * Realiza una petición GET autenticada a API-Football v3.
     * Incluye los headers requeridos por API-Sports.
     *
     * @param recurso Ruta relativa al BASE_URL (ej: "fixtures/lineups?fixture=123")
     * @return Cuerpo de la respuesta como String JSON
     * @throws ApiFootballException para todos los casos de error
     */
    public synchronized String consultarRecurso(String recurso) throws ApiFootballException {
        if (!recurso.matches("(?:status|fixtures(?:\\?(?:id=\\d+|league=\\d+&season=\\d{4}(?:&date=\\d{4}-\\d{2}-\\d{2})?))?|fixtures/(?:lineups|events|statistics)\\?fixture=\\d+)"))
            throw new IllegalArgumentException("Recurso API-Football no permitido");
        return get(recurso);
    }
    private record Cache(String json, java.time.Instant fecha) {}
    private final java.util.Map<String, Cache> cache = new java.util.HashMap<>();
    private synchronized String get(String recurso) throws ApiFootballException {
        Cache anterior = cache.get(recurso);
        if (anterior != null && java.time.Instant.now().isBefore(anterior.fecha().plusSeconds(90))) return anterior.json();
        String apiKey = cargarApiKey();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + recurso))
                // Headers obligatorios para autenticarse con API-Sports
                .header("x-apisports-key", apiKey)
                .header("Accept", "application/json")
                .timeout(TIMEOUT)
                .GET()
                .build();

        try {
            CUOTA.acquire();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString()
            );
            validarRespuesta(response);
            validarContenido(response.body());
            if (cache.size() >= 256) cache.clear();
            cache.put(recurso, new Cache(response.body(), java.time.Instant.now()));
            return response.body();

        } catch (java.net.http.HttpTimeoutException e) {
            throw new ApiFootballException(
                    "Tiempo de espera agotado consultando API-Football. " +
                    "Verifica tu conexión a internet.", e
            );
        } catch (IOException e) {
            throw new ApiFootballException(
                    "Error de red al comunicarse con API-Football: " + e.getMessage(), e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiFootballException("La consulta a API-Football fue interrumpida.", e);
        }
    }

    /**
     * Valida el código de estado HTTP de la respuesta.
     * Lanza excepciones descriptivas para los errores más comunes.
     */
    public static void validarContenido(String body) throws ApiFootballException {
        try {
            var json = com.google.gson.JsonParser.parseString(body).getAsJsonObject();
            var errors = json.get("errors");
            if (errors != null && !errors.isJsonNull()
                    && !(errors.isJsonArray() && errors.getAsJsonArray().isEmpty())
                    && !(errors.isJsonObject() && errors.getAsJsonObject().size() == 0))
                throw new ApiFootballException(errors.isJsonObject() && errors.getAsJsonObject().has("plan")
                        ? "Tu plan de API-Football no incluye esta temporada o fecha. El detalle principal sigue disponible."
                        : "API-Football rechazó la consulta: revisa la clave y la cuota de tu plan.");
            if (!json.has("response")) throw new ApiFootballException("Respuesta de API-Football incompleta.");
        } catch (RuntimeException e) { throw new ApiFootballException("Respuesta de API-Football no válida.", e); }
    }
    private void validarRespuesta(HttpResponse<String> response) throws ApiFootballException {
        switch (response.statusCode()) {
            case 200 -> { /* OK */ }
            case 401 -> throw new ApiFootballException(
                    "API Key de API-Football inválida o expirada (HTTP 401). " +
                    "Verifica API_FOOTBALL_KEY en config.local.properties."
            );
            case 403 -> throw new ApiFootballException(
                    "Acceso denegado (HTTP 403). Tu plan Free puede no cubrir " +
                    "este endpoint o temporada específica."
            );
            case 429 -> throw new ApiFootballException(
                    "Cuota diaria agotada (HTTP 429). El plan Free permite " +
                    "100 peticiones/día. Reintenta mañana."
            );
            default  -> throw new ApiFootballException(
                    "API-Football respondió con HTTP " + response.statusCode() +
                    "."
            );
        }
    }

    // =========================================================================
    // Carga Segura de API Key
    // =========================================================================

    /**
     * Carga la API Key de API-Football siguiendo la jerarquía de configuración:
     * 1. Variable de entorno:    API_FOOTBALL_KEY
     * 2. Propiedad JVM:          -Dapi.football.key=...
     * 3. config.local.properties: API_FOOTBALL_KEY=...
     */
    private static String cargarApiKey() throws ApiFootballException {
        // Reutilizamos LocalConfig que ya maneja env → JVM → archivo local
        String key = LocalConfig.valor("API_FOOTBALL_KEY", "");

        // Fallback: nombre alternativo con punto (convención JVM)
        if (key.isBlank()) key = LocalConfig.valor("api.football.key", "");

        if (key.isBlank()) {
            throw new ApiFootballException(
                    "No se encontró la API Key de API-Football.\n" +
                    "Agrégala en config.local.properties como:\n" +
                    "  API_FOOTBALL_KEY=tu_key_aqui\n" +
                    "O como variable de entorno: API_FOOTBALL_KEY"
            );
        }
        return key;
    }

    // =========================================================================
    // Deserializador personalizado para StatItem
    // =========================================================================

    /**
     * El campo 'value' de las estadísticas puede ser:
     *  - String: "55%", "1 (3)"
     *  - Number: 12, 0
     *  - null:   para estadísticas no disponibles
     * Este deserializador normaliza siempre a String para simplificar el consumo.
     */
    private static final class StatItemDeserializer implements JsonDeserializer<StatItem> {
        @Override
        public StatItem deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) {
            var obj   = json.getAsJsonObject();
            String t  = obj.has("type") ? obj.get("type").getAsString() : "";
            String v  = "N/D";
            if (obj.has("value") && !obj.get("value").isJsonNull()) {
                v = obj.get("value").getAsString();
            }
            return new StatItem(t, v);
        }
    }

    // =========================================================================
    // Excepción específica del servicio
    // =========================================================================

    /**
     * Excepción comprobada lanzada por {@link ApiFootballService}.
     * Permite al código cliente distinguir errores de API-Football de otros IOExceptions.
     */
    public static final class ApiFootballException extends Exception {
        public ApiFootballException(String message)             { super(message); }
        public ApiFootballException(String message, Throwable c){ super(message, c); }
    }

    // =========================================================================
    // Método main() — Prueba Rápida en IntelliJ
    // =========================================================================

    /**
     * Ejecuta una prueba completa con un partido real de LaLiga:
     * Atlético de Madrid vs Sevilla (fixture ID: 1208630, temporada 2024/25).
     *
     * Imprime en consola:
     *   - Formación y 11 titulares de cada equipo
     *   - Banca de cada equipo
     *   - Cambios realizados (Minuto X: Sale A ➔ Entra B)
     *   - Estadísticas clave de posesión y tiros
     *
     * Para probar con otro partido, cambia FIXTURE_ID por un ID válido
     * de API-Football. Los IDs se pueden buscar en /fixtures?league=140&season=2024
     */
    public static void main(String[] args) {
        // ID de partido de LaLiga (Atlético de Madrid 1-0 Sevilla, 2024/25)
        final long FIXTURE_ID = 1208630L;

        System.out.println("═".repeat(72));
        System.out.println("  PRUEBA — API-Football v3  |  Fixture ID: " + FIXTURE_ID);
        System.out.println("═".repeat(72));

        ApiFootballService servicio = new ApiFootballService();

        try {
            // ── 1. ALINEACIONES ──────────────────────────────────────────────
            System.out.println("\n📋 Obteniendo alineaciones...");
            List<LineupEquipo> alineaciones = servicio.obtenerAlineaciones(FIXTURE_ID);

            for (LineupEquipo equipo : alineaciones) {
                String nombre    = equipo.team().name();
                String formacion = equipo.formation();
                System.out.println("\n┌─── " + nombre + " (" + formacion + ") " +
                        "── DT: " + (equipo.coach() != null ? equipo.coach().name() : "N/D"));

                System.out.println("│  ⚽ TITULARES:");
                for (int i = 0; i < equipo.startXI().size(); i++) {
                    JugadorLineup j = equipo.startXI().get(i).player();
                    System.out.printf("│    %2d. %-3s #%-3d  %s%n",
                            i + 1, j.pos(), j.number(), j.name());
                }

                System.out.println("│  🪑 BANCA:");
                for (JugadorEnCancha sup : equipo.substitutes()) {
                    JugadorLineup j = sup.player();
                    System.out.printf("│       %-3s #%-3d  %s%n",
                            j.pos(), j.number(), j.name());
                }
                System.out.println("└" + "─".repeat(55));
            }

            // ── 2. EVENTOS Y CAMBIOS ─────────────────────────────────────────
            System.out.println("\n📡 Obteniendo eventos del partido...");
            List<EventoDto> eventos = servicio.obtenerEventos(FIXTURE_ID);

            System.out.println("\n  TIMELINE DEL PARTIDO:");
            System.out.println("  " + "─".repeat(60));
            for (EventoDto ev : eventos) {
                int minuto = (ev.time() != null && ev.time().elapsed() != null) ? ev.time().elapsed() : 0;
                String equipo = (ev.team() != null) ? ev.team().name() : "";

                switch (ev.type()) {
                    case "subst" -> {
                        String entra = ev.player() != null ? ev.player().name() : "?";
                        String sale  = ev.assist() != null ? ev.assist().name() : "?";
                        System.out.printf("  %3d' 🔄  %-20s  Sale: %-22s ➔ Entra: %s%n",
                                minuto, equipo, sale, entra);
                    }
                    case "Goal" -> {
                        String goleador = ev.player() != null ? ev.player().name() : "?";
                        String asistente = (ev.assist() != null && ev.assist().name() != null)
                                ? " (Asist: " + ev.assist().name() + ")" : "";
                        System.out.printf("  %3d' ⚽  %-20s  %s%s%n",
                                minuto, equipo, goleador, asistente);
                    }
                    case "Card" -> {
                        String jugador = ev.player() != null ? ev.player().name() : "?";
                        String icono = ev.detail() != null && ev.detail().contains("Red") ? "🟥" : "🟨";
                        System.out.printf("  %3d' %s  %-20s  %s (%s)%n",
                                minuto, icono, equipo, jugador, ev.detail());
                    }
                    default -> { /* Var, missed penalty, etc. se omiten en esta vista */ }
                }
            }
            System.out.println("  " + "─".repeat(60));

            // ── 3. ESTADÍSTICAS ──────────────────────────────────────────────
            System.out.println("\n📊 Obteniendo estadísticas del partido...");
            List<EstadisticasEquipo> estadisticasLista = servicio.obtenerEstadisticas(FIXTURE_ID);

            // Estadísticas clave que nos interesan para el motor de ratings
            final List<String> CLAVE = List.of(
                    "Ball Possession", "Total Shots", "Shots on Goal",
                    "Fouls", "Corner Kicks", "Offsides", "Passes %"
            );

            System.out.println();
            System.out.printf("  %-28s", "ESTADÍSTICA");
            for (EstadisticasEquipo eq : estadisticasLista) {
                System.out.printf("  %-20s", eq.team().name());
            }
            System.out.println();
            System.out.println("  " + "─".repeat(28 + estadisticasLista.size() * 22));

            for (String stat : CLAVE) {
                System.out.printf("  %-28s", stat);
                for (EstadisticasEquipo eq : estadisticasLista) {
                    String valor = eq.statistics().stream()
                            .filter(s -> stat.equals(s.type()))
                            .findFirst()
                            .map(s -> String.valueOf(s.value()))
                            .orElse("N/D");
                    System.out.printf("  %-20s", valor);
                }
                System.out.println();
            }

            System.out.println("\n═".repeat(72));
            System.out.println("  ✅ Prueba completada con éxito.");
            System.out.println("  Peticiones consumidas: 3 de 100 diarias del plan Free.");
            System.out.println("═".repeat(72));

        } catch (ApiFootballException e) {
            System.err.println("\n❌ ERROR: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("   Causa: " + e.getCause().getMessage());
            }
        }
    }
}
