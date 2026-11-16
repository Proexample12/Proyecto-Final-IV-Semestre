package ni.edu.uam.proyectofinalivsemestre.ui;

import ni.edu.uam.proyectofinalivsemestre.config.HibernateUtil;
import ni.edu.uam.proyectofinalivsemestre.model.ApiSnapshot;
import ni.edu.uam.proyectofinalivsemestre.service.FootballApiService;
import ni.edu.uam.proyectofinalivsemestre.service.highlightly.HighlightlyService;
import ni.edu.uam.proyectofinalivsemestre.service.real.ServiciosReales;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.io.IOException;
import java.time.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="futbol.test.highlightly",matches="true")
class HighlightlyIntegrationTest {
    @Test void partidoActualCompletoPersisteSinRed() throws Exception {
        var servicio=new ServiciosReales();
        if(servicio.buscar("LaLiga",LocalDate.of(2026,11,1)).stream().noneMatch(p->p.getId()==564734L))
            servicio.sincronizar("LaLiga",LocalDate.of(2026,11,1));
        servicio.actualizarDetalle(564734L);
        var d=servicio.detalle(564734L);
        assertEquals("Real Racing Club de Santander",d.partido().getLocal().getNombre());
        assertEquals("Real Madrid CF",d.partido().getVisitante().getNombre());
        assertTrue(servicio.buscar("LaLiga",LocalDate.of(2026,11,1)).stream()
                .anyMatch(p -> p.getId()==564734L && p.getLocal().getNombre().equals("Real Racing Club de Santander")));
        assertEquals("El Sardinero",d.estadio()); assertEquals(46,d.posesionLocal()); assertEquals(54,d.posesionVisitante());
        assertEquals(14,d.eventos().size()); assertEquals(22,d.tirosVisitante()); assertFalse(d.alineaciones().isEmpty());
        assertEquals("Highlightly · football-data",d.fuente());
        assertEquals(2,d.formaciones().size());
        var mbappe=d.formaciones().get(1).filas().stream().flatMap(java.util.List::stream)
                .filter(j->java.util.Objects.equals(j.id(),44912)).findFirst().orElseThrow();
        assertEquals(7.84,mbappe.puntuacion()); assertEquals(1,mbappe.goles());
        LocalDateTime antes;
        try(var em=HibernateUtil.getEntityManager()) { antes=em.find(ApiSnapshot.class,"highlightly/564734").getUpdatedAt(); }
        servicio.actualizarDetalle(564734L);
        try(var em=HibernateUtil.getEntityManager()) { assertEquals(antes,em.find(ApiSnapshot.class,"highlightly/564734").getUpdatedAt(),"TTL debe evitar reescribir el detalle"); }
        HibernateUtil.shutdown();
        var offline=new ServiciosReales(new FootballApiService(false) {
            @Override public String consultarRecurso(String recurso) throws IOException { throw new IOException("Sin red"); }
        },new HighlightlyService() {
            @Override public boolean configurado() { return true; }
            @Override public com.google.gson.JsonElement consultar(String recurso) throws IOException { throw new IOException("Sin red"); }
        });
        assertEquals(d.eventos(),offline.detalle(564734L).eventos());
        assertEquals(d.alineaciones(),offline.detalle(564734L).alineaciones());
        assertEquals(d.formaciones(),offline.detalle(564734L).formaciones());
        assertThrows(IOException.class,()->offline.actualizarDetalle(564734L));
        assertEquals(46,offline.detalle(564734L).posesionLocal());
        HibernateUtil.shutdown();
        System.out.println("Highlightly verificado: Santander–Real Madrid 2026, eventos, estadísticas, alineaciones, TTL y lectura offline.");
    }
}
