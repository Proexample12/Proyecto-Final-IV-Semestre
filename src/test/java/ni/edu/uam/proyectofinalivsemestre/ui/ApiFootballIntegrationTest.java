package ni.edu.uam.proyectofinalivsemestre.ui;

import com.google.gson.JsonParser;
import ni.edu.uam.proyectofinalivsemestre.config.HibernateUtil;
import ni.edu.uam.proyectofinalivsemestre.dao.GenericDao;
import ni.edu.uam.proyectofinalivsemestre.model.ApiSnapshot;
import ni.edu.uam.proyectofinalivsemestre.service.apifootball.ApiFootballService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="futbol.test.real", matches="true")
class ApiFootballIntegrationTest {
    @Test void detalleRealPersisteYTransaccionRevierte() throws Exception {
        var api = new ApiFootballService();
        var estado = JsonParser.parseString(api.consultarRecurso("status")).getAsJsonObject();
        assertTrue(estado.getAsJsonObject("response").getAsJsonObject("subscription").get("active").getAsBoolean());
        String payload = api.consultarRecurso("fixtures?id=1208630");
        var partido = JsonParser.parseString(payload).getAsJsonObject().getAsJsonArray("response").get(0).getAsJsonObject();
        assertFalse(partido.getAsJsonArray("events").isEmpty());
        assertEquals(2, partido.getAsJsonArray("statistics").size());
        String id = "test/api-football/" + java.util.UUID.randomUUID();
        try {
            GenericDao.executeInTransaction(em -> { em.persist(new ApiSnapshot(id,payload)); return null; });
            HibernateUtil.shutdown();
            try (var em = HibernateUtil.getEntityManager()) { assertEquals(payload, em.find(ApiSnapshot.class,id).getPayload()); }
            assertThrows(IllegalStateException.class, () -> GenericDao.executeInTransaction(em -> {
                em.merge(new ApiSnapshot(id,"rollback")); em.flush(); throw new IllegalStateException("simulado");
            }));
            try (var em = HibernateUtil.getEntityManager()) { assertEquals(payload, em.find(ApiSnapshot.class,id).getPayload()); }
            assertEquals(payload, api.consultarRecurso("fixtures?id=1208630"));
            System.out.println("API-Football: detalle real, persistencia y rollback verificados.");
        } finally {
            GenericDao.executeInTransaction(em -> { var snapshot = em.find(ApiSnapshot.class,id); if(snapshot != null) em.remove(snapshot); return null; });
            HibernateUtil.shutdown();
        }
    }
}
