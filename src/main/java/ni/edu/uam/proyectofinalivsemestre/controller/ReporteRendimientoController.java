package ni.edu.uam.proyectofinalivsemestre.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import ni.edu.uam.proyectofinalivsemestre.dao.StandingDao;
import ni.edu.uam.proyectofinalivsemestre.model.Standing;
import ni.edu.uam.proyectofinalivsemestre.util.UiUtil;

import javafx.stage.FileChooser;
import ni.edu.uam.proyectofinalivsemestre.service.ReportePdfService;
import java.awt.Desktop;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class ReporteRendimientoController {

    // ── Records ──────────────────────────────────────────────────────────────
    public record FilaRendimiento(int pos, String equipo, int pj, int pts,
                                  String efectividad, String golesPartido,
                                  String solidez, String zona) {}

    // ── Competición IDs ──────────────────────────────────────────────────────
    private static final Map<String, Integer> LIGA_IDS = Map.of(
            "LaLiga", 2014,
            "Premier League", 2021,
            "Champions League", 2001
    );

    // ── FXML ─────────────────────────────────────────────────────────────────
    @FXML private ComboBox<String> cmbLiga;
    @FXML private Label lblMensaje;
    @FXML private VBox panelContenido;
    @FXML private TableView<FilaRendimiento> tblRendimiento;
    @FXML private TableColumn<FilaRendimiento, Object> colPos, colEquipo, colPj, colPts,
            colEfectividad, colGolesPartido, colSolidez, colZona;
    @FXML private Button btnExportar, btnVistaPrevia;
    @FXML private ProgressIndicator piCarga;

    private final StandingDao dao = new StandingDao();
    private List<FilaRendimiento> filasCargadas = List.of();

    @FXML
    private void initialize() {
        cmbLiga.getItems().setAll("LaLiga", "Premier League", "Champions League");
        cmbLiga.setValue("LaLiga");

        tblRendimiento.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tblRendimiento.setPlaceholder(new Label("No hay datos disponibles. Sincroniza la aplicación primero."));

        col(colPos, FilaRendimiento::pos);
        col(colEquipo, FilaRendimiento::equipo);
        col(colPj, FilaRendimiento::pj);
        col(colPts, FilaRendimiento::pts);
        col(colEfectividad, FilaRendimiento::efectividad);
        col(colGolesPartido, FilaRendimiento::golesPartido);
        col(colSolidez, FilaRendimiento::solidez);
        col(colZona, FilaRendimiento::zona);

        cmbLiga.valueProperty().addListener((o, a, n) -> cargar());
        cargar();
    }

    private <T> void col(TableColumn<FilaRendimiento, Object> c, Function<FilaRendimiento, T> fn) {
        c.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(fn.apply(cell.getValue())));
    }

    private void cargar() {
        String liga = cmbLiga.getValue();
        if (liga == null) return;
        int compId = LIGA_IDS.get(liga);

        UiUtil.ocupado(true, piCarga, cmbLiga, btnExportar, btnVistaPrevia, tblRendimiento);
        lblMensaje.setVisible(false);

        UiUtil.async(
                () -> dao.findStandingsByCompetitionOrdered(compId),
                standings -> {
                    UiUtil.ocupado(false, piCarga, cmbLiga, btnExportar, btnVistaPrevia, tblRendimiento);
                    if (standings.isEmpty()) {
                        lblMensaje.setText("No hay datos disponibles. Sincroniza la aplicación primero.");
                        lblMensaje.setVisible(true);
                        panelContenido.setVisible(false);
                        tblRendimiento.getItems().clear();
                        filasCargadas = List.of();
                        return;
                    }
                    panelContenido.setVisible(true);

                    // Calcular métricas en Java puro
                    filasCargadas = standings.stream()
                            .map(s -> {
                                int pj = s.getPlayedGames();
                                int pts = s.getPoints();
                                int pos = s.getPosition();

                                // Efectividad %
                                double ef = pj > 0 ? (pts / (pj * 3.0)) * 100 : 0.0;
                                String efectividad = String.format("%.1f%%", ef);

                                // Promedio goles por partido
                                double gpp = pj > 0 ? (double) s.getGoalsFor() / pj : 0.0;
                                String golesPartido = String.format("%.2f", gpp);

                                // Solidez defensiva
                                double solidezVal = pj > 0 ? 1.0 - ((double) s.getGoalsAgainst() / pj) : 1.0;
                                String solidez = solidezVal > 0.7 ? "Alta" : solidezVal > 0.4 ? "Media" : "Baja";

                                // Zona
                                String zona;
                                if (pos <= 4) zona = "🏆 Champions";
                                else if (pos <= 6) zona = "⚽ Europa";
                                else if (pos <= 17) zona = "🔄 Mitad";
                                else zona = "⬇ Descenso";

                                return new FilaRendimiento(pos,
                                        s.getTeam().getName(), pj, pts,
                                        efectividad, golesPartido, solidez, zona);
                            })
                            // Ordenar por efectividad DESC (parseamos el número)
                            .sorted(Comparator.comparingDouble(
                                    (FilaRendimiento f) -> Double.parseDouble(
                                            f.efectividad().replace("%", "").replace(",", ".")
                                    )).reversed())
                            .toList();

                    tblRendimiento.getItems().setAll(filasCargadas);
                },
                e -> {
                    UiUtil.ocupado(false, piCarga, cmbLiga, btnExportar, btnVistaPrevia, tblRendimiento);
                    UiUtil.error(e);
                }
        );
    }

    private List<Map<String, ?>> obtenerFilasComoMap() {
        List<Map<String, ?>> rows = new ArrayList<>();
        for (FilaRendimiento f : filasCargadas) {
            Map<String, Object> m = new HashMap<>();
            m.put("pos",          f.pos());
            m.put("equipo",       f.equipo());
            m.put("pj",           f.pj());
            m.put("pts",          f.pts());
            m.put("efectividad",  f.efectividad());
            m.put("golesPartido", f.golesPartido());
            m.put("solidez",      f.solidez());
            m.put("zona",         f.zona());
            rows.add(m);
        }
        return rows;
    }

    @FXML
    private void onVistaPrevia() {
        if (filasCargadas.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "No hay datos para generar el reporte.", ButtonType.OK).show();
            return;
        }
        String liga = cmbLiga.getValue();
        String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        UiUtil.ocupado(true, piCarga, cmbLiga, btnExportar, btnVistaPrevia, tblRendimiento);

        List<Map<String, ?>> rows = obtenerFilasComoMap();
        Map<String, Object> params = new HashMap<>();
        params.put("LIGA_NOMBRE",   liga);
        params.put("FECHA_EMISION", fecha);

        UiUtil.async(
            () -> ReportePdfService.generarJasperPrint("/reportes/rendimiento.jrxml", params, rows),
            print -> {
                UiUtil.ocupado(false, piCarga, cmbLiga, btnExportar, btnVistaPrevia, tblRendimiento);
                ReportePdfService.mostrarVistaPrevia(print, "Reporte de Rendimiento - " + liga);
            },
            ex -> {
                UiUtil.ocupado(false, piCarga, cmbLiga, btnExportar, btnVistaPrevia, tblRendimiento);
                UiUtil.error(ex);
            }
        );
    }

    @FXML
    private void onExportar() {
        if (filasCargadas.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "No hay datos para exportar.", ButtonType.OK).show();
            return;
        }
        String liga = cmbLiga.getValue();
        String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar Reporte PDF");
        chooser.setInitialFileName("rendimiento_" + liga.replace(" ", "_") + "_" + fecha + ".pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File destino = chooser.showSaveDialog(btnExportar.getScene().getWindow());
        if (destino == null) return;

        UiUtil.ocupado(true, piCarga, cmbLiga, btnExportar, btnVistaPrevia, tblRendimiento);

        List<Map<String, ?>> rows = obtenerFilasComoMap();
        Map<String, Object> params = new HashMap<>();
        params.put("LIGA_NOMBRE",   liga);
        params.put("FECHA_EMISION", fecha);

        File destinoFinal = destino;
        UiUtil.async(
            () -> {
                ReportePdfService.exportarPdf("/reportes/rendimiento.jrxml", params, rows, destinoFinal);
                return destinoFinal;
            },
            pdf -> {
                UiUtil.ocupado(false, piCarga, cmbLiga, btnExportar, btnVistaPrevia, tblRendimiento);
                Alert ok = new Alert(Alert.AlertType.INFORMATION,
                        "PDF guardado en:\n" + pdf.getAbsolutePath(), ButtonType.OK);
                ok.setTitle("Exportación exitosa");
                ok.setHeaderText("Reporte generado correctamente");
                ok.show();
                try { Desktop.getDesktop().open(pdf); } catch (Exception ignored) {}
            },
            ex -> {
                UiUtil.ocupado(false, piCarga, cmbLiga, btnExportar, btnVistaPrevia, tblRendimiento);
                UiUtil.error(ex);
            }
        );
    }
}
