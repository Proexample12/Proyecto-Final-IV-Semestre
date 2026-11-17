package ni.edu.uam.proyectofinalivsemestre.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import ni.edu.uam.proyectofinalivsemestre.dao.StandingDao;
import ni.edu.uam.proyectofinalivsemestre.model.Standing;
import ni.edu.uam.proyectofinalivsemestre.util.UiUtil;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
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
    @FXML private Button btnExportar;
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

        UiUtil.ocupado(true, piCarga, cmbLiga, btnExportar, tblRendimiento);
        lblMensaje.setVisible(false);

        UiUtil.async(
                () -> dao.findStandingsByCompetitionOrdered(compId),
                standings -> {
                    UiUtil.ocupado(false, piCarga, cmbLiga, btnExportar, tblRendimiento);
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
                    UiUtil.ocupado(false, piCarga, cmbLiga, btnExportar, tblRendimiento);
                    UiUtil.error(e);
                }
        );
    }

    @FXML
    private void onExportar() {
        if (filasCargadas.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "No hay datos para exportar.", ButtonType.OK).show();
            return;
        }
        String liga = cmbLiga.getValue().replace(" ", "_");
        String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String nombre = "rendimiento_" + liga + "_" + fecha + ".csv";
        String ruta = System.getProperty("user.home") + File.separator + nombre;
        try (PrintWriter pw = new PrintWriter(new FileWriter(ruta))) {
            pw.println("Pos,Equipo,PJ,Pts,Efectividad (%),Goles/Partido,Solidez Def.,Zona");
            for (FilaRendimiento f : filasCargadas) {
                pw.println(f.pos() + "," + escapeCsv(f.equipo()) + "," + f.pj() + "," + f.pts() + "," +
                        escapeCsv(f.efectividad()) + "," + escapeCsv(f.golesPartido()) + "," +
                        f.solidez() + "," + escapeCsv(f.zona()));
            }
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Archivo guardado en:\n" + ruta, ButtonType.OK);
            alert.setTitle("Exportación exitosa");
            alert.setHeaderText("CSV exportado correctamente");
            alert.show();
        } catch (Exception ex) {
            UiUtil.error(ex);
        }
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
