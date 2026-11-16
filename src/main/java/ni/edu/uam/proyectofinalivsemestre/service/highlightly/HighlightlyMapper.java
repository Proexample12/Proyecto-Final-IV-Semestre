package ni.edu.uam.proyectofinalivsemestre.service.highlightly;

import com.google.gson.*;
import ni.edu.uam.proyectofinalivsemestre.model.Partido;
import ni.edu.uam.proyectofinalivsemestre.model.dto.PartidoDetalle;
import java.time.LocalDateTime;
import java.util.*;
import static ni.edu.uam.proyectofinalivsemestre.service.highlightly.HighlightlyService.*;

/** Traduce datos opcionales sin sustituir ausencias por ceros. */
public final class HighlightlyMapper {
    private HighlightlyMapper() {}
    private static Map<String,Double> stats(JsonObject match,String lado) {
        Integer id = integer(obj(match,lado),"id");
        Map<String,Double> result = new LinkedHashMap<>();
        if (id == null) return result;
        for (JsonElement element : array(match,"statistics")) {
            if (!element.isJsonObject()) continue;
            JsonObject fila = element.getAsJsonObject();
            if (!id.equals(integer(obj(fila,"team"),"id"))) continue;
            for (JsonElement stat : array(fila,"statistics")) {
                JsonObject s = stat.getAsJsonObject();
                try {
                    double valor = Double.parseDouble(text(s,"value",""));
                    if (Double.isFinite(valor) && valor >= 0) result.put(text(s,"displayName","").toLowerCase(Locale.ROOT),valor);
                } catch (NumberFormatException ignored) { /* No disponible. */ }
            }
        }
        return result;
    }
    private static Integer valor(Map<String,Double> stats,String nombre) {
        Double v = stats.get(nombre); return v == null ? null : (int)Math.round(v);
    }
    private static Integer posesion(Map<String,Double> stats) {
        Double v = stats.get("possession");
        if (v == null) return null;
        double porcentaje = v <= 1 ? v*100 : v;
        return porcentaje <= 100 ? (int)Math.round(porcentaje) : null;
    }
    private static Integer tiros(Map<String,Double> stats) {
        Integer total = valor(stats,"total shots");
        if (total != null) return total;
        Integer dentro = valor(stats,"shots within penalty area"), fuera = valor(stats,"shots outside penalty area");
        if (dentro != null && fuera != null) return dentro+fuera;
        Integer puerta = valor(stats,"shots on target"), desviados = valor(stats,"shots off target"), bloqueados = valor(stats,"blocked shots");
        return puerta == null || desviados == null || bloqueados == null ? null : puerta+desviados+bloqueados;
    }
    private static String titulo(String key) {
        return switch(key) {
            case "possession" -> "Posesión"; case "shots on target" -> "Tiros a puerta";
            case "shots off target" -> "Tiros desviados"; case "blocked shots" -> "Tiros bloqueados";
            case "expected goals" -> "Goles esperados (xG)"; case "expected assists" -> "Asistencias esperadas";
            case "corners" -> "Córners"; case "fouls" -> "Faltas"; case "offsides" -> "Fueras de juego";
            case "yellow cards" -> "Tarjetas amarillas"; case "red cards" -> "Tarjetas rojas";
            case "total passes" -> "Pases"; case "successful passes" -> "Pases completados";
            case "goalkeeper saves" -> "Paradas"; case "shots accuracy" -> "Precisión de tiro";
            default -> key.isEmpty() ? "Estadística" : Character.toUpperCase(key.charAt(0))+key.substring(1);
        };
    }
    private static String formato(String key,Double v) {
        if (v == null) return "—";
        if (key.equals("possession") || key.equals("shots accuracy")) return Math.round(v <= 1 ? v*100 : v)+"%";
        return java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }
    private static String evento(String tipo) {
        return switch(tipo) {
            case "Goal" -> "⚽ Gol"; case "Own Goal" -> "⚽ Autogol"; case "Penalty" -> "⚽ Penal";
            case "Missed Penalty" -> "Penal fallado"; case "Yellow Card" -> "▨ Amarilla";
            case "Red Card" -> "▨ Roja"; case "Substitution" -> "⇄ Cambio";
            case "VAR Goal Confirmed" -> "VAR: gol confirmado"; case "VAR Goal Cancelled" -> "VAR: gol anulado";
            case "VAR Goal Cancelled - Offside" -> "VAR: fuera de juego";
            case "VAR Penalty" -> "VAR: penal"; case "VAR Penalty Cancelled" -> "VAR: penal anulado";
            default -> tipo;
        };
    }
    private static void jugadores(JsonArray lista,List<String> salida) {
        for (JsonElement e : lista) {
            if (e.isJsonArray()) jugadores(e.getAsJsonArray(),salida);
            else if (e.isJsonObject()) {
                JsonObject p = e.getAsJsonObject();
                salida.add(text(p,"number","—")+" · "+text(p,"name","Jugador"));
            }
        }
    }
    public static PartidoDetalle combinar(PartidoDetalle base,JsonObject snapshot,LocalDateTime actualizado) {
        JsonObject match = obj(snapshot,"match"), lineup = obj(snapshot,"lineups");
        Map<String,Double> home = stats(match,"homeTeam"), away = stats(match,"awayTeam");
        List<String> eventos = new ArrayList<>();
        for (JsonElement e : array(match,"events")) {
            JsonObject v = e.getAsJsonObject();
            String cambio = text(v,"substituted","");
            String asistencia = text(v,"assist","");
            eventos.add(text(v,"time","?")+"′ · "+evento(text(v,"type","Evento"))+"\n"
                    +text(v,"player","")+(cambio.isBlank() ? "" : " ↔ "+cambio)
                    +(asistencia.isBlank() ? "" : " · Asist.: "+asistencia)+"\n"+text(obj(v,"team"),"name",""));
        }
        List<PartidoDetalle.Estadistica> todas = new ArrayList<>();
        Set<String> claves = new LinkedHashSet<>(home.keySet()); claves.addAll(away.keySet());
        for(String key:claves) todas.add(new PartidoDetalle.Estadistica(titulo(key),formato(key,home.get(key)),formato(key,away.get(key))));
        List<String> alineaciones = new ArrayList<>();
        for(String lado:List.of("homeTeam","awayTeam")) {
            JsonObject equipo = obj(lineup,lado);
            if (array(equipo,"initialLineup").isEmpty() && array(equipo,"substitutes").isEmpty()) continue;
            alineaciones.add(text(equipo,"name","")+" · "+text(equipo,"formation","Sin formación"));
            alineaciones.add("TITULARES"); jugadores(array(equipo,"initialLineup"),alineaciones);
            alineaciones.add("SUPLENTES"); jugadores(array(equipo,"substitutes"),alineaciones);
        }
        Partido p = base.partido();
        if ("EN_VIVO".equals(p.getEstado())) p = new Partido(p.getId(),p.getFecha(),p.getLiga(),p.getLocal(),p.getVisitante(),
                p.getGolesLocal(),p.getGolesVisitante(),p.getEstado(),integer(obj(match,"state"),"clock"));
        String aviso = text(snapshot,"warning",null);
        if (eventos.isEmpty() && todas.isEmpty() && alineaciones.isEmpty() && aviso == null)
            aviso = "Highlightly todavía no publica eventos, estadísticas ni alineaciones para este encuentro.";
        return new PartidoDetalle(p,text(obj(match,"venue"),"name",base.estadio()),
                eventos.isEmpty() ? base.eventos() : List.copyOf(eventos),
                home.containsKey("possession") ? posesion(home) : base.posesionLocal(),
                tiros(home) == null ? base.tirosLocal() : tiros(home),tiros(away) == null ? base.tirosVisitante() : tiros(away),
                home.containsKey("shots on target") ? valor(home,"shots on target") : base.tirosPuertaLocal(),
                away.containsKey("shots on target") ? valor(away,"shots on target") : base.tirosPuertaVisitante(),
                away.containsKey("possession") ? posesion(away) : base.posesionVisitante(),
                List.copyOf(todas),List.copyOf(alineaciones),"Highlightly · football-data",actualizado,aviso, AlineacionMapper.convertir(snapshot,base.partido()));
    }
}
