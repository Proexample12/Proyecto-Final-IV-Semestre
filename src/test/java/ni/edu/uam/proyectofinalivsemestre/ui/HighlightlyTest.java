package ni.edu.uam.proyectofinalivsemestre.ui;

import com.google.gson.*;
import ni.edu.uam.proyectofinalivsemestre.model.*;
import ni.edu.uam.proyectofinalivsemestre.model.dto.PartidoDetalle;
import ni.edu.uam.proyectofinalivsemestre.service.highlightly.*;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HighlightlyTest {
    @Test void normalizaSiglasSinCambiosPorPartido() {
        assertEquals(HighlightlyService.nombre("Sabah FK"), HighlightlyService.nombre("Sabah"));
        assertEquals(HighlightlyService.nombre("Sabah FK"), HighlightlyService.nombre("Sabah FA"));
        assertEquals(HighlightlyService.nombre("FC Barcelona"), HighlightlyService.nombre("Barcelona"));
        assertEquals(HighlightlyService.nombre("AC Milan"), HighlightlyService.nombre("Milan"));
        assertNotEquals(HighlightlyService.nombre("Manchester City"), HighlightlyService.nombre("Manchester United"));
        assertNotEquals(HighlightlyService.nombre("Barcelona B"), HighlightlyService.nombre("Barcelona"));
        assertNotEquals(HighlightlyService.nombre("Sabah U21"), HighlightlyService.nombre("Sabah"));
    }
    @Test void reconocePsvSinConfundirOtrosEquipos() {
        var principal = JsonParser.parseString("""
                {"competition":{"code":"CL"},"utcDate":"2026-11-24T20:00:00Z",
                 "homeTeam":{"name":"Real Madrid CF"},"awayTeam":{"name":"PSV"}}
                """).getAsJsonObject();
        var match = JsonParser.parseString("""
                {"league":{"name":"UEFA Champions League"},"date":"2026-11-24T20:00:00.000Z",
                 "homeTeam":{"name":"Real Madrid"},"awayTeam":{"name":"PSV Eindhoven"}}
                """).getAsJsonObject();
        assertTrue(HighlightlyService.coincide(principal, match));
        match.getAsJsonObject("awayTeam").addProperty("name", "FC Eindhoven");
        assertFalse(HighlightlyService.coincide(principal, match));
        match.getAsJsonObject("awayTeam").addProperty("name", "Jong PSV");
        assertFalse(HighlightlyService.coincide(principal, match));
    }
    static JsonObject fixture(String nombre) throws IOException {
        try(var in=HighlightlyTest.class.getResourceAsStream("/highlightly/"+nombre+".json")) {
            return JsonParser.parseString(new String(Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private PartidoDetalle base() {
        var partido = new Partido(564734L,LocalDateTime.of(2026,11,1,9,15),new Liga("LaLiga"),
                new Equipo(5335L,"Santander"),new Equipo(86L,"Real Madrid"),0,3,"FINALIZADO",null);
        return new PartidoDetalle(partido,"No disponible",List.of(),null,null,null,null,null);
    }
    @Test void normalizaDatosRealesSinCruzarEquipos() throws Exception {
        JsonObject snapshot=new JsonObject(); snapshot.add("match",fixture("match")); snapshot.add("lineups",fixture("lineups"));
        // El orden de estadísticas del proveedor no debe decidir quién es local.
        JsonArray stats=snapshot.getAsJsonObject("match").getAsJsonArray("statistics");
        JsonElement primero=stats.remove(0); stats.add(primero);
        var d=HighlightlyMapper.combinar(base(),snapshot,LocalDateTime.now());
        assertEquals("El Sardinero",d.estadio()); assertEquals(14,d.eventos().size());
        assertEquals(46,d.posesionLocal()); assertEquals(54,d.posesionVisitante());
        assertEquals(10,d.tirosLocal()); assertEquals(22,d.tirosVisitante());
        assertEquals(1,d.tirosPuertaLocal()); assertEquals(7,d.tirosPuertaVisitante());
        assertTrue(d.alineaciones().size()>26); assertTrue(d.estadisticas().size()>20);
        assertTrue(d.eventos().stream().anyMatch(e->e.contains("45+1")));
    }
    @Test void validaIdentidadFechaLigaYLados() throws Exception {
        var principal=fixture("principal"); var match=fixture("match");
        assertTrue(HighlightlyService.coincide(principal,match));
        var otro=match.deepCopy(); otro.addProperty("date","2026-11-02T15:15:00Z");
        assertFalse(HighlightlyService.coincide(principal,otro));
        otro=match.deepCopy(); otro.add("homeTeam",match.get("awayTeam")); otro.add("awayTeam",match.get("homeTeam"));
        assertFalse(HighlightlyService.coincide(principal,otro));
        otro=match.deepCopy(); otro.getAsJsonObject("country").addProperty("name","England");
        assertFalse(HighlightlyService.coincide(principal,otro));
        otro=match.deepCopy(); otro.getAsJsonObject("league").addProperty("name","Premier League");
        assertFalse(HighlightlyService.coincide(principal,otro));
    }
    @Test void datosAusentesNoSonCeros() {
        var d=HighlightlyMapper.combinar(base(),new JsonObject(),LocalDateTime.now());
        assertNull(d.posesionLocal()); assertNull(d.tirosLocal()); assertTrue(d.alineaciones().isEmpty()); assertNotNull(d.aviso());
    }
    @Test void paginacionYCoincidenciasAmbiguas() throws Exception {
        var principal=fixture("principal"); var match=fixture("match");
        var llamadas=new ArrayList<String>();
        var api=new HighlightlyService() {
            @Override public JsonElement consultar(String recurso) {
                llamadas.add(recurso); var pagina=new JsonObject(); var data=new JsonArray();
                if(recurso.endsWith("offset=0")) for(int i=0;i<100;i++) data.add(new JsonObject());
                else data.add(match);
                pagina.add("data",data); var pag=new JsonObject(); pag.addProperty("totalCount",101); pagina.add("pagination",pag); return pagina;
            }
        };
        assertEquals(1336445224,api.localizar(principal).get("id").getAsInt()); assertEquals(2,llamadas.size());
        var ambiguo=new HighlightlyService() {
            @Override public JsonElement consultar(String recurso) {
                var p=new JsonObject(); var data=new JsonArray(); data.add(match); data.add(match); p.add("data",data); return p;
            }
        };
        assertThrows(IOException.class,()->ambiguo.localizar(principal));
    }
}
