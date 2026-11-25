package ni.edu.uam.proyectofinalivsemestre.service;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.view.JasperViewer;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

public class ReportePdfService {

    /**
     * Compila la plantilla JRXML y genera el documento JasperPrint en memoria.
     *
     * @param jrxmlPath ruta del recurso classpath, ej: "/reportes/clasificacion.jrxml"
     * @param params    parámetros del reporte (LIGA_NOMBRE, FECHA_EMISION, etc.)
     * @param rows      colección de Map<String,Object> con los datos de las filas
     * @return JasperPrint listo para visualizar o exportar
     */
    public static JasperPrint generarJasperPrint(String jrxmlPath,
                                                 Map<String, Object> params,
                                                 Collection<Map<String, ?>> rows) throws JRException {
        InputStream is = ReportePdfService.class.getResourceAsStream(jrxmlPath);
        if (is == null) {
            throw new IllegalArgumentException("No se encontró la plantilla de reporte: " + jrxmlPath);
        }
        JasperReport jasperReport = JasperCompileManager.compileReport(is);
        JRMapCollectionDataSource ds = new JRMapCollectionDataSource(rows);
        return JasperFillManager.fillReport(jasperReport, params, ds);
    }

    /**
     * Muestra la ventana interactiva de JasperViewer en un hilo independiente.
     * Incluye botones nativos de Guardar, Imprimir, Zoom y Paginación con sus respectivos tooltips e iconos.
     *
     * @param print  el documento JasperPrint
     * @param titulo título de la ventana
     */
    public static void mostrarVistaPrevia(JasperPrint print, String titulo) {
        SwingUtilities.invokeLater(() -> {
            JasperViewer viewer = new JasperViewer(print, false);
            if (titulo != null && !titulo.isBlank()) {
                viewer.setTitle(titulo);
            }
            viewer.setLocationRelativeTo(null);
            viewer.setVisible(true);
        });
    }

    /**
     * Compila y exporta directamente a PDF en disco.
     */
    public static void exportarPdf(String jrxmlPath,
                                   Map<String, Object> params,
                                   Collection<Map<String, ?>> rows,
                                   File destino) throws JRException {
        JasperPrint print = generarJasperPrint(jrxmlPath, params, rows);
        exportarPdf(print, destino);
    }

    /**
     * Exporta un JasperPrint existente a un archivo PDF.
     */
    public static void exportarPdf(JasperPrint print, File destino) throws JRException {
        JRPdfExporter exporter = new JRPdfExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(destino));
        exporter.exportReport();
    }
}
