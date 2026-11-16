package ni.edu.uam.proyectofinalivsemestre.ui;

import com.google.gson.*;
import ni.edu.uam.proyectofinalivsemestre.model.*;
import ni.edu.uam.proyectofinalivsemestre.model.dto.*;
import ni.edu.uam.proyectofinalivsemestre.service.highlightly.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AlineacionTest {
    static JsonObject snapshot() throws Exception {
        JsonObject s=new JsonObject(); s.add("match",HighlightlyTest.fixture("match")); s.add("lineups",HighlightlyTest.fixture("lineups"));
        try(var in=AlineacionTest.class.getResourceAsStream("/highlightly/box-score.json")) {
            s.add("boxScore",JsonParser.parseString(new String(Objects.requireNonNull(in).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)));
        }
        return s;
    }
    static PartidoDetalle detalle(JsonObject snapshot) {
        var p=new Partido(564734L,LocalDateTime.of(2026,11,1,9,15),new Liga("LaLiga"),
                new Equipo(5335L,"Real Racing Club de Santander"),new Equipo(86L,"Real Madrid CF"),0,3,"FINALIZADO",null);
        return HighlightlyMapper.combinar(new PartidoDetalle(p,"",List.of(),null,null,null,null,null),snapshot,LocalDateTime.now());
    }
    private Alineacion.Jugador jugador(PartidoDetalle d,int id) {
        return d.formaciones().stream().flatMap(a->java.util.stream.Stream.concat(a.filas().stream().flatMap(List::stream),a.suplentes().stream()))
                .filter(j->Objects.equals(j.id(),id)).findFirst().orElseThrow();
    }
    @Test void titularesPuntuacionGolesAsistenciasYCambios() throws Exception {
        var d=detalle(snapshot()); assertEquals(2,d.formaciones().size());
        for(var a:d.formaciones()) assertEquals(11,a.filas().stream().mapToInt(List::size).sum());
        assertEquals(7.84,jugador(d,44912).puntuacion()); assertEquals(1,jugador(d,44912).goles());
        assertEquals(1,jugador(d,47006358).asistencias()); assertEquals("67′",jugador(d,47006358).salida());
        assertEquals("67′",jugador(d,119938).entrada());
        assertEquals(1,jugador(d,55274030).amarillas()); assertEquals(1,jugador(d,55274030).rojas());
    }
    @Test void capitanSoloCuandoElProveedorLoMarcaYRatingNulo() throws Exception {
        var s=snapshot();
        var players=s.getAsJsonArray("boxScore").get(1).getAsJsonObject().getAsJsonArray("players");
        for(var e:players) {var j=e.getAsJsonObject(); if(j.get("id").getAsInt()==44912) {j.addProperty("isCaptain",true); j.add("matchRating",JsonNull.INSTANCE);} }
        var d=detalle(s); assertTrue(jugador(d,44912).capitan()); assertNull(jugador(d,44912).puntuacion());
    }
    @Test void sinBoxScoreConservaEventosYNoInventaCapitanNiRating() throws Exception {
        var s=snapshot(); s.remove("boxScore"); var d=detalle(s);
        assertNull(jugador(d,44912).puntuacion()); assertNull(jugador(d,44912).capitan());
        assertEquals(1,jugador(d,44912).goles()); assertEquals(1,jugador(d,47006358).asistencias());
        assertEquals("67′",jugador(d,47006358).salida());
    }
    @Test void rechazaPuntuacionesDeEquiposAjenos() throws Exception {
        var s=snapshot(); s.getAsJsonArray("boxScore").get(1).getAsJsonObject().getAsJsonObject("team").addProperty("id",999);
        var fake=new HighlightlyService() { @Override public JsonElement consultar(String recurso) { return s.get("boxScore"); } };
        assertThrows(java.io.IOException.class,()->fake.puntuaciones(1336445224,s.getAsJsonObject("match")));
        assertNull(jugador(detalle(s),44912).puntuacion());
    }
}
