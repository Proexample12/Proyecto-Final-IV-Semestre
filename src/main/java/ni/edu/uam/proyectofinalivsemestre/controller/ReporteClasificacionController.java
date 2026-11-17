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
import java.util.Optional;

public final class ReporteClasificacionController {

    // ── Records ──────────────────────────────────────────────────────────────
    public record FilaReporte(int pos, String equipo, int pj, int g, int e, int p,
                              int gf, int gc, int dg, int pts, String racha) {}

    // ── Competición IDs ──────────────────────────────────────────────────────
    private static final Map<String, Integer> LIGA_IDS = Map.of(
            "LaLiga", 2014,
            "Premier League", 2021,
            "Champions League", 2001
    );

    // ── FXML ─────────────────────────────────────────────────────────────────
    @FXML private ComboBox<String> cmbLiga;
    @FXML private Label lblLider, lblDefensa, lblAtaque, lblDescenso, lblMensaje;
    @FXML private VBox panelKpis;
    @FXML private TableView<FilaReporte> tblClasificacion;
    @FXML private TableColumn<FilaReporte, Object> colPos, colEquipo, colPj, colG, colE, colP,
            colGf, colGc, colDg, colPts, colRacha;
    @FXML private Button btnExportar;
    @FXML private ProgressIndicator piCarga;

    private final StandingDao dao = new StandingDao();
    private List<FilaReporte> filasCargadas = List.of();

    @FXML
    private void initialize() {
        cmbLiga.getItems().setAll("LaLiga", "Premier League", "Champions League");
        cmbLiga.setValue("LaLiga");

        tblClasificacion.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tblClasificacion.setPlaceholder(new Label("No hay datos disponibles. Sincroniza la aplicación primero."));

        col(colPos, FilaReporte::pos);
        col(colEquipo, FilaReporte::equipo);
        col(colPj, FilaReporte::pj);
        col(colG, FilaReporte::g);
        col(colE, FilaReporte::e);
        col(colP, FilaReporte::p);
        col(colGf, FilaReporte::gf);
        col(colGc, FilaReporte::gc);
        col(colDg, r -> r.dg() >= 0 ? "+" + r.dg() : String.valueOf(r.dg()));
        col(colPts, FilaReporte::pts);
        col(colRacha, FilaReporte::racha);

        cmbLiga.valueProperty().addListener((o, a, n) -> cargar());
        cargar();
    }

    private <T> void col(TableColumn<FilaReporte, Object> c, java.util.function.Function<FilaReporte, T> fn) {
        c.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(fn.apply(cell.getValue())));
    }

    private void cargar() {
        String liga = cmbLiga.getValue();
        if (liga == null) return;
        int compId = LIGA_IDS.get(liga);
        int descensoPosMin = compId == 2001 ? 24 : 18;

        UiUtil.ocupado(true, piCarga, cmbLiga, btnExportar, tblClasificacion);
        lblMensaje.setVisible(false);

        UiUtil.async(
                () -> dao.findStandingsByCompetitionOrdered(compId),
                standings -> {
                    UiUtil.ocupado(false, piCarga, cmbLiga, btnExportar, tblClasificacion);
                    if (standings.isEmpty()) {
                        lblMensaje.setText("No hay datos disponibles. Sincroniza la aplicación primero.");
                        lblMensaje.setVisible(true);
                        panelKpis.setVisible(false);
                        tblClasificacion.getItems().clear();
                        filasCargadas = List.of();
                        return;
                    }
                    panelKpis.setVisible(true);

                    // KPIs calculados en Java puro
                    Optional<Standing> lider = standings.stream()
                            .filter(s -> s.getPosition() == 1).findFirst();
                    Optional<Standing> mejorDefensa = standings.stream()
                            .filter(s -> s.getPlayedGames() > 0)
                            .min(Comparator.comparingInt(Standing::getGoalsAgainst));
                    Optional<Standing> mejorAtaque = standings.stream()
                            .max(Comparator.comparingInt(Standing::getGoalsFor));
                    Optional<Standing> descenso = standings.stream()
                            .filter(s -> s.getPosition() >= descensoPosMin)
                            .max(Comparator.comparingInt(Standing::getPosition));

                    lblLider.setText("👑 Líder: " + lider.map(s -> s.getTeam().getName()).orElse("—"));
                    lblDefensa.setText("🛡️ Mejor defensa: " + mejorDefensa.map(s ->
                            s.getTeam().getName() + " (" + s.getGoalsAgainst() + " GC)").orElse("—"));
                    lblAtaque.setText("⚽ Mejor ataque: " + mejorAtaque.map(s ->
                            s.getTeam().getName() + " (" + s.getGoalsFor() + " GF)").orElse("—"));
                    lblDescenso.setText("📉 Zona de descenso: " + descenso.map(s ->
                            s.getTeam().getName() + " (pos " + s.getPosition() + ")").orElse("—"));

                    // Tabla
                    filasCargadas = standings.stream().map(s -> new FilaReporte(
                            s.getPosition(),
                            s.getTeam().getName(),
                            s.getPlayedGames(),
                            s.getWon(),
                            s.getDraw(),
                            s.getLost(),
                            s.getGoalsFor(),
                            s.getGoalsAgainst(),
                            s.getGoalDifference(),
                            s.getPoints(),
                            racha(s)
                    )).toList();
                    tblClasificacion.getItems().setAll(filasCargadas);
                },
                e -> {
                    UiUtil.ocupado(false, piCarga, cmbLiga, btnExportar, tblClasificacion);
                    UiUtil.error(e);
                }
        );
    }

    private String racha(Standing s) {
        if (s.getWon() > s.getLost()) return "V";
        if (s.getLost() > s.getWon()) return "D";
        return "E";
    }

    @FXML
    private void onExportar() {
        if (filasCargadas.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "No hay datos para exportar.", ButtonType.OK).show();
            return;
        }
        String liga = cmbLiga.getValue().replace(" ", "_");
        String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String nombre = "clasificacion_" + liga + "_" + fecha + ".csv";
        String ruta = System.getProperty("user.home") + File.separator + nombre;
        try (PrintWriter pw = new PrintWriter(new FileWriter(ruta))) {
            pw.println("Pos,Equipo,PJ,G,E,P,GF,GC,DG,Pts,Racha");
            for (FilaReporte f : filasCargadas) {
                pw.println(f.pos() + "," + escapeCsv(f.equipo()) + "," + f.pj() + "," +
                        f.g() + "," + f.e() + "," + f.p() + "," +
                        f.gf() + "," + f.gc() + "," + f.dg() + "," + f.pts() + "," + f.racha());
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
