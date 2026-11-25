package ni.edu.uam.proyectofinalivsemestre.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import ni.edu.uam.proyectofinalivsemestre.util.UiUtil;

import java.io.IOException;

/**
 * Controlador contenedor para navegar entre los tres reportes del sistema:
 * Clasificación, Goles y Rendimiento.
 */
public final class ReportesController {
    @FXML private ToggleGroup grupoReportes;
    @FXML private ToggleButton btnClasificacion;
    @FXML private ToggleButton btnGoles;
    @FXML private ToggleButton btnRendimiento;
    @FXML private StackPane contenedorReporte;

    @FXML
    private void initialize() {
        grupoReportes.selectedToggleProperty().addListener((o, anterior, actual) -> {
            if (actual == null && anterior != null) {
                anterior.setSelected(true);
            }
        });
        irAClasificacion();
    }

    private void abrir(String vista, ToggleButton boton) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/" + vista + ".fxml"));
            Parent root = loader.load();
            contenedorReporte.getChildren().setAll(root);
            boton.setSelected(true);
        } catch (IOException e) {
            UiUtil.error(e);
        }
    }

    @FXML
    private void irAClasificacion() {
        abrir("reporte_clasificacion", btnClasificacion);
    }

    @FXML
    private void irAGoles() {
        abrir("reporte_goles", btnGoles);
    }

    @FXML
    private void irARendimiento() {
        abrir("reporte_rendimiento", btnRendimiento);
    }
}
