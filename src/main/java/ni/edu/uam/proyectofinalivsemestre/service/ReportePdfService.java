package ni.edu.uam.proyectofinalivsemestre.service;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;

import java.io.File;
import java.io.InputStream;
import java.util.Collection;
import java.util.Map;

public class ReportePdfService {

    /**
     * Compila el jrxml, llena los datos y exporta a PDF.
     *
     * @param jrxmlPath  ruta del recurso classpath, ej: "/reportes/clasificacion.jrxml"
     * @param params     parámetros del reporte (LIGA_NOMBRE, FECHA_EMISION, etc.)
     * @param rows       colección de Map<String,Object> con los datos de las filas
     * @param destino    archivo de destino .pdf
     */
    public static void exportarPdf(String jrxmlPath,
                                   Map<String, Object> params,
                                   Collection<Map<String, ?>> rows,
                                   File destino) throws JRException {
        InputStream is = ReportePdfService.class.getResourceAsStream(jrxmlPath);
        if (is == null) {
            throw new IllegalArgumentException("No se encontró la plantilla de reporte: " + jrxmlPath);
        }
        JasperReport jasperReport = JasperCompileManager.compileReport(is);
        JRMapCollectionDataSource ds = new JRMapCollectionDataSource(rows);
        JasperPrint print = JasperFillManager.fillReport(jasperReport, params, ds);

        JRPdfExporter exporter = new JRPdfExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(destino));
        exporter.exportReport();
    }
}
