package ni.edu.uam.proyectofinalivsemestre.ui;

import ni.edu.uam.proyectofinalivsemestre.controller.ReporteClasificacionController;
import ni.edu.uam.proyectofinalivsemestre.controller.ReporteGolesController;
import ni.edu.uam.proyectofinalivsemestre.controller.ReporteRendimientoController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ReportesTest {

    @Test
    void testPlantillasCompilanYGeneranPdf() throws Exception {
        for (String nombre : new String[]{"clasificacion", "goles", "rendimiento"}) {
            try (var in = getClass().getResourceAsStream("/reportes/" + nombre + ".jrxml")) {
                var reporte = net.sf.jasperreports.engine.JasperCompileManager.compileReport(in);
                var fila = new java.util.HashMap<String, Object>();
                for (var campo : reporte.getFields()) {
                    fila.put(campo.getName(), campo.getValueClass() == Integer.class ? 1 : "Prueba");
                }
                var datos = new net.sf.jasperreports.engine.data.JRMapCollectionDataSource(
                        java.util.List.<java.util.Map<String, ?>>of(fila));
                var print = net.sf.jasperreports.engine.JasperFillManager.fillReport(
                        reporte, new java.util.HashMap<>(), datos);
                byte[] pdf = net.sf.jasperreports.engine.JasperExportManager.exportReportToPdf(print);
                assertFalse(print.getPages().isEmpty(), nombre);
                assertTrue(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)
                        .equals("%PDF-"), nombre);
            }
        }
    }

    @Test
    @DisplayName("FilaGoles almacena correctamente datos de partido y total de goles")
    void testFilaGoles() {
        var fila = new ReporteGolesController.FilaGoles(
                11, "Real Madrid", "2 - 1", "Barcelona", 3, "Local"
        );
        assertEquals(11, fila.jornada());
        assertEquals("Real Madrid", fila.local());
        assertEquals("2 - 1", fila.marcador());
        assertEquals("Barcelona", fila.visitante());
        assertEquals(3, fila.totalGoles());
        assertEquals("Local", fila.resultado());
    }

    @Test
    @DisplayName("FilaReporte almacena estadísticas de clasificación y racha")
    void testFilaReporte() {
        var fila = new ReporteClasificacionController.FilaReporte(
                1, "Real Madrid", 10, 8, 1, 1, 21, 7, 14, 25, "V"
        );
        assertEquals(1, fila.pos());
        assertEquals("Real Madrid", fila.equipo());
        assertEquals(10, fila.pj());
        assertEquals(8, fila.g());
        assertEquals(1, fila.e());
        assertEquals(1, fila.p());
        assertEquals(21, fila.gf());
        assertEquals(7, fila.gc());
        assertEquals(14, fila.dg());
        assertEquals(25, fila.pts());
        assertEquals("V", fila.racha());
    }

    @Test
    @DisplayName("FilaRendimiento almacena métricas calculadas y zona de clasificación")
    void testFilaRendimiento() {
        var fila = new ReporteRendimientoController.FilaRendimiento(
                1, "Real Madrid", 10, 25, "83.3%", "2.10", "Alta", "🏆 Champions"
        );
        assertEquals(1, fila.pos());
        assertEquals("Real Madrid", fila.equipo());
        assertEquals(10, fila.pj());
        assertEquals(25, fila.pts());
        assertEquals("83.3%", fila.efectividad());
        assertEquals("2.10", fila.golesPartido());
        assertEquals("Alta", fila.solidez());
        assertTrue(fila.zona().contains("Champions"));
    }

    @Test
    @DisplayName("Los recursos FXML de los 3 reportes existen y están bien formados")
    void testRecursosFxmlExisten() {
        assertNotNull(getClass().getResource("/views/reporte_goles.fxml"), "reporte_goles.fxml debe existir");
        assertNotNull(getClass().getResource("/views/reporte_clasificacion.fxml"), "reporte_clasificacion.fxml debe existir");
        assertNotNull(getClass().getResource("/views/reporte_rendimiento.fxml"), "reporte_rendimiento.fxml debe existir");
    }

    @Test
    @DisplayName("Las plantillas JRXML para exportación a PDF existen en el classpath")
    void testPlantillasJrxmlExisten() {
        assertNotNull(getClass().getResource("/reportes/clasificacion.jrxml"), "clasificacion.jrxml debe existir");
        assertNotNull(getClass().getResource("/reportes/goles.jrxml"), "goles.jrxml debe existir");
        assertNotNull(getClass().getResource("/reportes/rendimiento.jrxml"), "rendimiento.jrxml debe existir");
    }

    @Test
    @DisplayName("La hoja de estilos contiene las clases requeridas para reportes y gráfica")
    void testStylesContieneClasesReporte() throws Exception {
        var cssUrl = getClass().getResource("/css/styles.css");
        assertNotNull(cssUrl, "styles.css debe existir");
        try (var in = cssUrl.openStream()) {
            String css = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(css.contains(".pestanas-reportes"), "Debe contener .pestanas-reportes");
            assertTrue(css.contains(".tab-reporte"), "Debe contener .tab-reporte");
            assertTrue(css.contains(".tarjeta-kpi"), "Debe contener .tarjeta-kpi");
            assertTrue(css.contains(".panel-grafica"), "Debe contener .panel-grafica");
            assertTrue(css.contains(".chart-bar"), "Debe contener .chart-bar");
            assertTrue(css.contains(".badge-resultado"), "Debe contener .badge-resultado");
        }
    }
}
