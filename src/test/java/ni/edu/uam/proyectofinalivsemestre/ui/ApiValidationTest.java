package ni.edu.uam.proyectofinalivsemestre.ui;
import ni.edu.uam.proyectofinalivsemestre.service.apifootball.ApiFootballService;
import ni.edu.uam.proyectofinalivsemestre.util.UiUtil;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ApiValidationTest {
    @Test void erroresEnHttp200NoSonExito() {
        assertThrows(ApiFootballService.ApiFootballException.class,
                () -> ApiFootballService.validarContenido("{\"errors\":{\"plan\":\"denied\"},\"response\":[]}"));
        assertThrows(ApiFootballService.ApiFootballException.class,
                () -> ApiFootballService.validarContenido("{\"errors\":{\"token\":\"invalid\"},\"response\":[]}"));
        assertThrows(ApiFootballService.ApiFootballException.class, () -> ApiFootballService.validarContenido("not json"));
        assertDoesNotThrow(() -> ApiFootballService.validarContenido("{\"errors\":[],\"response\":[]}"));
    }
    @Test void cuotaCuentaFallosYLiberaLaVentana() throws Exception {
        var instante = new java.util.concurrent.atomic.AtomicReference<>(java.time.Instant.EPOCH);
        var clock = new java.time.Clock() {
            public java.time.ZoneId getZone() { return java.time.ZoneOffset.UTC; }
            public java.time.Clock withZone(java.time.ZoneId z) { return this; }
            public java.time.Instant instant() { return instante.get(); }
        };
        var cuota = new ni.edu.uam.proyectofinalivsemestre.service.RequestBudget(clock);
        for (int i=0;i<10;i++) cuota.acquire();
        assertThrows(java.io.IOException.class, cuota::acquire);
        instante.set(java.time.Instant.EPOCH.plusSeconds(60));
        assertDoesNotThrow(cuota::acquire);
    }
    @Test void busquedaIgnoraTildesMayusculasYEspacios() {
        assertEquals("atletico", UiUtil.normalizar("  ATLÉTICO  "));
        assertEquals("", UiUtil.normalizar(null));
    }
}
