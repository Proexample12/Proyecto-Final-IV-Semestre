package ni.edu.uam.proyectofinalivsemestre.service.highlightly;

import com.google.gson.*;
import ni.edu.uam.proyectofinalivsemestre.model.Partido;
import ni.edu.uam.proyectofinalivsemestre.model.dto.Alineacion;
import java.util.*;
import static ni.edu.uam.proyectofinalivsemestre.service.highlightly.HighlightlyService.*;

public final class AlineacionMapper {
    private AlineacionMapper() {}
    public static List<Alineacion> convertir(JsonObject snapshot, Partido partido) {
        List<Alineacion> resultado = new ArrayList<>();
        JsonObject lineups=obj(snapshot,"lineups"), match=obj(snapshot,"match");
        for(String lado:List.of("homeTeam","awayTeam")) {
            JsonObject equipo=obj(lineups,lado);
            Integer equipoId=integer(equipo,"id");
            if (equipoId == null || !equipoId.equals(integer(obj(match,lado),"id"))) continue;
            Map<Integer,JsonObject> jugadores=new HashMap<>();
            for(JsonElement e:array(snapshot,"boxScore")) {
                JsonObject b=e.getAsJsonObject();
                if (!equipoId.equals(integer(obj(b,"team"),"id"))) continue;
                for(JsonElement j:array(b,"players")) {
                    JsonObject jugador=j.getAsJsonObject(); Integer id=integer(jugador,"id");
                    if(id!=null) jugadores.put(id,jugador);
                }
            }
            List<List<Alineacion.Jugador>> filas=new ArrayList<>();
            for(JsonElement fila:array(equipo,"initialLineup")) {
                if(!fila.isJsonArray()) continue;
                List<Alineacion.Jugador> linea=new ArrayList<>();
                for(JsonElement j:fila.getAsJsonArray()) linea.add(jugador(j.getAsJsonObject(),jugadores,equipoId,match));
                if(!linea.isEmpty()) filas.add(List.copyOf(linea));
            }
            List<Alineacion.Jugador> suplentes=new ArrayList<>();
            for(JsonElement j:array(equipo,"substitutes")) suplentes.add(jugador(j.getAsJsonObject(),jugadores,equipoId,match));
            resultado.add(new Alineacion(lado.equals("homeTeam") ? partido.getLocal().getNombre() : partido.getVisitante().getNombre(),
                    text(equipo,"formation","—"),List.copyOf(filas),List.copyOf(suplentes)));
        }
        return List.copyOf(resultado);
    }
    private static int cantidad(JsonObject datos,String clave,int eventos) {
        Integer valor=integer(datos,clave); return valor == null ? eventos : Math.max(0,valor);
    }
    private static String normalizar(String nombre) {
        return java.text.Normalizer.normalize(nombre,java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");
    }
    private static boolean esJugador(JsonObject evento,String campoId,String campoNombre,Integer id,String nombre,String completo) {
        Integer objetivo=integer(evento,campoId);
        if(objetivo!=null && id!=null) return objetivo.equals(id);
        String n=normalizar(text(evento,campoNombre,""));
        return !n.isEmpty() && (n.equals(normalizar(nombre)) || n.equals(normalizar(completo)));
    }
    private static Alineacion.Jugador jugador(JsonObject lineup,Map<Integer,JsonObject> jugadores,Integer equipo,JsonObject match) {
        Integer id=integer(lineup,"id"); JsonObject box=jugadores.getOrDefault(id,new JsonObject());
        String nombre=text(lineup,"name","Jugador"), completo=text(box,"fullName",nombre);
        if(completo.isBlank()) completo=nombre;
        JsonObject stats=obj(box,"statistics");
        // La respuesta real usa objeto; el OpenAPI también documenta una lista de estadísticas.
        if(box.has("statistics") && box.get("statistics").isJsonArray() && !box.getAsJsonArray("statistics").isEmpty())
            stats=box.getAsJsonArray("statistics").get(0).getAsJsonObject();
        int goles=0,asistencias=0,amarillas=0,rojas=0; String salida=null,entrada=null;
        for(JsonElement e:array(match,"events")) {
            JsonObject evento=e.getAsJsonObject();
            if(!equipo.equals(integer(obj(evento,"team"),"id"))) continue;
            String tipo=text(evento,"type","");
            boolean propio=esJugador(evento,"playerId","player",id,nombre,completo);
            if(propio) switch(tipo) {
                case "Goal", "Penalty" -> goles++;
                case "Yellow Card" -> amarillas++;
                case "Red Card", "Second Yellow Card" -> rojas++;
                case "Substitution" -> salida=text(evento,"time","?")+"′";
                default -> {}
            }
            if((tipo.equals("Goal") || tipo.equals("Penalty")) && esJugador(evento,"assistingPlayerId","assist",id,nombre,completo)) asistencias++;
            if(tipo.equals("Substitution") && esJugador(evento,"assistingPlayerId","substituted",id,nombre,completo)) entrada=text(evento,"time","?")+"′";
        }
        Double rating=null;
        try { double v=Double.parseDouble(text(box,"matchRating","")); if(Double.isFinite(v) && v>=0 && v<=10) rating=v; }
        catch(NumberFormatException ignored) { /* Sin puntuación, no inventar cero. */ }
        Boolean capitan=box.has("isCaptain") && box.get("isCaptain").isJsonPrimitive() ? box.get("isCaptain").getAsBoolean() : null;
        int expulsiones=cantidad(stats,"cardsRed",rojas);
        if(cantidad(stats,"cardsSecondYellow",0)>0) expulsiones=Math.max(1,expulsiones);
        return new Alineacion.Jugador(id,completo,integer(lineup,"number"),rating,capitan,
                cantidad(stats,"goalsScored",goles),cantidad(stats,"assists",asistencias),cantidad(stats,"cardsYellow",amarillas),expulsiones,salida,entrada);
    }
}
