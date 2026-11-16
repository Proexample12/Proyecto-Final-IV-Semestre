package ni.edu.uam.proyectofinalivsemestre.service.highlightly;

import com.google.gson.*;
import ni.edu.uam.proyectofinalivsemestre.config.LocalConfig;
import ni.edu.uam.proyectofinalivsemestre.service.RequestBudget;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Cliente del producto Football de Highlightly; nunca reutiliza IDs de otros proveedores. */
public class HighlightlyService {
    private static final RequestBudget CUOTA = new RequestBudget();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private record Cache(JsonElement data, Instant fecha) {}
    private final Map<String, Cache> cache = new LinkedHashMap<>();
    public boolean configurado() { return !LocalConfig.valor("HIGHLIGHTLY_API_KEY", "").isBlank(); }

    public synchronized JsonElement consultar(String recurso) throws IOException, InterruptedException {
        if (!recurso.matches("(?:matches\\?[^\\s#]+|(?:matches|lineups|box-score)/\\d+)"))
            throw new IllegalArgumentException("Recurso Highlightly no permitido");
        Cache previo = cache.get(recurso);
        long ttl = recurso.startsWith("box-score/") ? 300 : 90;
        if (previo != null && Instant.now().isBefore(previo.fecha().plusSeconds(ttl))) return previo.data().deepCopy();
        String key = LocalConfig.valor("HIGHLIGHTLY_API_KEY", "");
        if (key.isBlank()) throw new IOException("Falta HIGHLIGHTLY_API_KEY en la configuración local.");
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://soccer.highlightly.net/" + recurso))
                .header("x-rapidapi-key",key).header("Accept","application/json")
                .timeout(Duration.ofSeconds(25)).GET().build();
        CUOTA.acquire();
        HttpResponse<String> response = http.send(request,HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException(switch(response.statusCode()) {
            case 401 -> "Highlightly no aceptó la clave configurada.";
            case 403 -> "Highlightly denegó el acceso: revisa la suscripción Football API de tu clave.";
            case 429 -> "Se agotó la cuota de Highlightly. Se conserva el detalle guardado.";
            case 404 -> "Highlightly aún no tiene esos datos para el partido.";
            default -> "Highlightly no está disponible (HTTP " + response.statusCode() + ").";
        });
        JsonElement json;
        try { json = JsonParser.parseString(response.body()); }
        catch (JsonParseException e) { throw new IOException("Highlightly devolvió JSON no válido.",e); }
        if (!json.isJsonObject() && !json.isJsonArray()) throw new IOException("Respuesta Highlightly incompleta.");
        if (json.isJsonObject() && (json.getAsJsonObject().has("error") || json.getAsJsonObject().has("errors")))
            throw new IOException("Highlightly devolvió un error de servicio.");
        if (cache.size() >= 128) cache.remove(cache.keySet().iterator().next());
        cache.put(recurso,new Cache(json.deepCopy(),Instant.now())); return json;
    }
    public JsonObject localizar(JsonObject principal) throws IOException, InterruptedException {
        String codigo = text(obj(principal,"competition"),"code","");
        String liga = switch(codigo) { case "PD" -> "La Liga"; case "PL" -> "Premier League";
            case "CL" -> "UEFA Champions League"; default -> throw new IOException("Liga no compatible con Highlightly."); };
        String pais = switch(codigo) { case "PD" -> "Spain"; case "PL" -> "England"; default -> ""; };
        LocalDate fecha = Instant.parse(text(principal,"utcDate","")).atOffset(ZoneOffset.UTC).toLocalDate();
        List<JsonObject> encontrados = new ArrayList<>();
        for (int offset=0; offset<1000; offset+=100) {
            JsonObject pagina = consultar("matches?date=" + fecha + "&leagueName="
                    + URLEncoder.encode(liga,StandardCharsets.UTF_8) + (pais.isEmpty() ? "" : "&countryName=" + pais) + "&limit=100&offset=" + offset).getAsJsonObject();
            if (!pagina.has("data") || !pagina.get("data").isJsonArray()) throw new IOException("Lista de partidos Highlightly incompleta.");
            JsonArray datos = array(pagina,"data");
            for (JsonElement e : datos) if (e.isJsonObject() && coincide(principal,e.getAsJsonObject())) encontrados.add(e.getAsJsonObject());
            Integer total = integer(obj(pagina,"pagination"),"totalCount");
            if (total == null || offset + datos.size() >= total) break;
            if (datos.isEmpty() || offset == 900) throw new IOException("No se pudo completar la búsqueda paginada en Highlightly.");
        }
        if (encontrados.size() != 1) throw new IOException(encontrados.isEmpty()
                ? "No se encontró una coincidencia segura de este partido en Highlightly. Se conservan los datos disponibles."
                : "Highlightly devolvió más de una coincidencia; no se mezclaron los partidos.");
        return encontrados.getFirst();
    }
    public JsonObject partido(long id) throws IOException, InterruptedException {
        JsonElement json = consultar("matches/" + id);
        // Algunas versiones del endpoint envuelven el detalle en una lista.
        if (json.isJsonArray() && json.getAsJsonArray().size() == 1) json = json.getAsJsonArray().get(0);
        if (!json.isJsonObject() || !Objects.equals(text(json.getAsJsonObject(),"id",""),Long.toString(id)))
            throw new IOException("El detalle Highlightly no corresponde al ID solicitado.");
        return json.getAsJsonObject();
    }
    public JsonObject alineaciones(long id, JsonObject partido) throws IOException, InterruptedException {
        JsonElement json = consultar("lineups/" + id);
        if (!json.isJsonObject()) throw new IOException("Formato de alineaciones Highlightly no válido.");
        JsonObject result = json.getAsJsonObject();
        for (String lado : List.of("homeTeam","awayTeam")) {
            if (!Objects.equals(integer(obj(result,lado),"id"),integer(obj(partido,lado),"id")))
                throw new IOException("Las alineaciones no corresponden a los equipos del partido.");
        }
        return result;
    }
    public JsonArray puntuaciones(long id, JsonObject partido) throws IOException, InterruptedException {
        JsonElement json = consultar("box-score/" + id);
        if (!json.isJsonArray()) throw new IOException("Formato de puntuaciones Highlightly no válido.");
        Set<Integer> equipos = new HashSet<>();
        equipos.add(integer(obj(partido,"homeTeam"),"id")); equipos.add(integer(obj(partido,"awayTeam"),"id"));
        Set<Integer> vistos = new HashSet<>();
        for (JsonElement e : json.getAsJsonArray()) {
            if (!e.isJsonObject()) throw new IOException("Puntuaciones Highlightly incompletas.");
            Integer equipo = integer(obj(e.getAsJsonObject(),"team"),"id");
            if (equipo == null || !equipos.contains(equipo) || !vistos.add(equipo))
                throw new IOException("Las puntuaciones no corresponden a los equipos del partido.");
        }
        return json.getAsJsonArray();
    }
    public static boolean coincide(JsonObject principal, JsonObject candidato) {
        String liga = switch(text(obj(principal,"competition"),"code","")) {
            case "PD" -> "laliga"; case "PL" -> "premierleague"; case "CL" -> "uefachampionsleague"; default -> "";
        };
        if (liga.isEmpty() || !liga.equals(normalizar(text(obj(candidato,"league"),"name","")))) return false;
        String pais = switch(text(obj(principal,"competition"),"code","")) { case "PD" -> "Spain"; case "PL" -> "England"; default -> ""; };
        if (!pais.isEmpty() && !pais.equals(text(obj(candidato,"country"),"name",""))) return false;
        try {
            if (!Instant.parse(text(principal,"utcDate","")).equals(Instant.parse(text(candidato,"date","")))) return false;
        } catch (DateTimeException e) { return false; }
        return mismoEquipo(obj(principal,"homeTeam"),obj(candidato,"homeTeam"))
                && mismoEquipo(obj(principal,"awayTeam"),obj(candidato,"awayTeam"));
    }
    private static String normalizar(String valor) {
        return java.text.Normalizer.normalize(valor,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","")
                .toLowerCase(Locale.ROOT).replaceAll("\\b(fc|cf|afc)\\b","").replaceAll("[^a-z0-9]","");
    }
    private static String nombre(String valor) {
        String n = normalizar(valor);
        return switch(n) {
            case "realracingclubdesantander", "racingsantander", "santander" -> "racingsantander";
            case "clubatleticodemadrid", "atleticomadrid", "atleticodemadrid", "atleti" -> "atleticomadrid";
            case "athleticclub", "athleticclubdebilbao", "athleticbilbao", "athletic" -> "athleticclub";
            case "barca", "barcelona" -> "barcelona";
            default -> n;
        };
    }
    private static boolean mismoEquipo(JsonObject a, JsonObject b) {
        String destino = nombre(text(b,"name",""));
        return !destino.isBlank() && (destino.equals(nombre(text(a,"name",""))) || destino.equals(nombre(text(a,"shortName",""))));
    }
    public static JsonObject obj(JsonObject json,String key) { return json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key) : new JsonObject(); }
    public static JsonArray array(JsonObject json,String key) { return json.has(key) && json.get(key).isJsonArray() ? json.getAsJsonArray(key) : new JsonArray(); }
    public static String text(JsonObject json,String key,String fallback) { return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback; }
    public static Integer integer(JsonObject json,String key) {
        try { return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : null; }
        catch (NumberFormatException e) { return null; }
    }
}
