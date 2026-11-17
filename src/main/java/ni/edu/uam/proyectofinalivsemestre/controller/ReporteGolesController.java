package ni.edu.uam.proyectofinalivsemestre.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import ni.edu.uam.proyectofinalivsemestre.dao.MatchDao;
import ni.edu.uam.proyectofinalivsemestre.model.Match;
import ni.edu.uam.proyectofinalivsemestre.util.UiUtil;

import javafx.stage.FileChooser;
import ni.edu.uam.proyectofinalivsemestre.service.ReportePdfService;
import java.awt.Desktop;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class ReporteGolesController {

    // ── Records ──────────────────────────────────────────────────────────────
    public record FilaGoles(Integer jornada, String local, String marcador, String visitante,
                            int totalGoles, String resultado) {}

    // ── Competición IDs ──────────────────────────────────────────────────────
    private static final Map<String, Integer> LIGA_IDS = Map.of(
            "LaLiga", 2014,
            "Premier League", 2021,
            "Champions League", 2001
    );

    // ── FXML ─────────────────────────────────────────────────────────────────
    @FXML private ComboBox<String> cmbLiga;
    @FXML private Label lblTotalGoles, lblPromedio, lblMaxGoles, lblMinGoles,
            lblPartidosJugados, lblOver25, lblMensaje;
    @FXML private VBox panelKpis;
    @FXML private TableView<FilaGoles> tblGoles;
    @FXML private TableColumn<FilaGoles, Object> colJornada, colLocal, colMarcador,
            colVisitante, colTotal, colResultado;
    @FXML private Button btnExportar;
    @FXML private ProgressIndicator piCarga;

    private final MatchDao dao = new MatchDao();
    private List<FilaGoles> filasCargadas = List.of();

    @FXML
    private void initialize() {
        cmbLiga.getItems().setAll("LaLiga", "Premier League", "Champions League");
        cmbLiga.setValue("LaLiga");

        tblGoles.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tblGoles.setPlaceholder(new Label("No hay datos disponibles. Sincroniza la aplicación primero."));

        col(colJornada, f -> f.jornada() == null ? "—" : f.jornada());
        col(colLocal, FilaGoles::local);
        col(colMarcador, FilaGoles::marcador);
        col(colVisitante, FilaGoles::visitante);
        col(colTotal, FilaGoles::totalGoles);
        col(colResultado, FilaGoles::resultado);

        cmbLiga.valueProperty().addListener((o, a, n) -> cargar());
        cargar();
    }

    private <T> void col(TableColumn<FilaGoles, Object> c, Function<FilaGoles, T> fn) {
        c.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(fn.apply(cell.getValue())));
    }

    private void cargar() {
        String liga = cmbLiga.getValue();
        if (liga == null) return;
        int compId = LIGA_IDS.get(liga);

        UiUtil.ocupado(true, piCarga, cmbLiga, btnExportar, tblGoles);
        lblMensaje.setVisible(false);

        UiUtil.async(
                () -> dao.findFinishedMatchesByCompetition(compId),
                matches -> {
                    UiUtil.ocupado(false, piCarga, cmbLiga, btnExportar, tblGoles);
                    if (matches.isEmpty()) {
                        lblMensaje.setText("No hay datos disponibles. Sincroniza la aplicación primero.");
                        lblMensaje.setVisible(true);
                        panelKpis.setVisible(false);
                        tblGoles.getItems().clear();
                        filasCargadas = List.of();
                        return;
                    }
                    panelKpis.setVisible(true);

                    // KPIs calculados en Java puro
                    int totalGoles = matches.stream()
                            .mapToInt(m -> safe(m.getHomeScore()) + safe(m.getAwayScore())).sum();
                    int totalPartidos = matches.size();
                    BigDecimal promedio = totalPartidos > 0
                            ? BigDecimal.valueOf(totalGoles)
                                .divide(BigDecimal.valueOf(totalPartidos), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    long over25 = matches.stream()
                            .filter(m -> (safe(m.getHomeScore()) + safe(m.getAwayScore())) > 2).count();
                    double pctOver25 = totalPartidos > 0 ? (over25 * 100.0 / totalPartidos) : 0.0;

                    Optional<Match> maxGoles = matches.stream()
                            .max(Comparator.comparingInt(m -> safe(m.getHomeScore()) + safe(m.getAwayScore())));
                    Optional<Match> minGoles = matches.stream()
                            .min(Comparator.comparingInt(m -> safe(m.getHomeScore()) + safe(m.getAwayScore())));

                    lblTotalGoles.setText("⚽ Total de goles: " + totalGoles);
                    lblPromedio.setText("📊 Promedio goles/partido: " + promedio);
                    lblPartidosJugados.setText("🗓️ Partidos finalizados: " + totalPartidos);
                    lblOver25.setText("📈 Over 2.5 goles: " + String.format("%.1f%%", pctOver25) +
                            " (" + over25 + " partidos)");
                    lblMaxGoles.setText("🔥 Más goles: " + maxGoles.map(this::descMatch).orElse("—"));
                    lblMinGoles.setText("🔒 Menos goles: " + minGoles.map(this::descMatch).orElse("—"));

                    // Tabla
                    filasCargadas = matches.stream().map(m -> {
                        int hs = safe(m.getHomeScore()), as = safe(m.getAwayScore());
                        String resultado = hs > as ? "Local" : (as > hs ? "Visitante" : "Empate");
                        return new FilaGoles(
                                m.getMatchday(),
                                m.getHomeTeam().getName(),
                                hs + " - " + as,
                                m.getAwayTeam().getName(),
                                hs + as,
                                resultado
                        );
                    }).toList();
                    tblGoles.getItems().setAll(filasCargadas);
                },
                e -> {
                    UiUtil.ocupado(false, piCarga, cmbLiga, btnExportar, tblGoles);
                    UiUtil.error(e);
                }
        );
    }

    private int safe(Integer v) { return v == null ? 0 : v; }

    private String descMatch(Match m) {
        return m.getHomeTeam().getName() + " " + safe(m.getHomeScore()) +
                "-" + safe(m.getAwayScore()) + " " + m.getAwayTeam().getName();
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
        chooser.setInitialFileName("goles_" + liga.replace(" ", "_") + "_" + fecha + ".pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File destino = chooser.showSaveDialog(btnExportar.getScene().getWindow());
        if (destino == null) return;

        UiUtil.ocupado(true, piCarga, cmbLiga, btnExportar, tblGoles);

        List<Map<String, ?>> rows = new ArrayList<>();
        for (FilaGoles f : filasCargadas) {
            Map<String, Object> m = new HashMap<>();
            m.put("jornada",    f.jornada());
            m.put("local",      f.local());
            m.put("marcador",   f.marcador());
            m.put("visitante",  f.visitante());
            m.put("totalGoles", f.totalGoles());
            m.put("resultado",  f.resultado());
            rows.add(m);
        }

        // KPIs como parámetros (los valores ya están en los Labels)
        Map<String, Object> params = new HashMap<>();
        params.put("LIGA_NOMBRE",   liga);
        params.put("FECHA_EMISION", fecha);
        params.put("TOTAL_GOLES",   lblTotalGoles.getText());
        params.put("PROMEDIO",      lblPromedio.getText());
        params.put("OVER_25",       lblOver25.getText());
        params.put("MAX_GOLES",     lblMaxGoles.getText());

        File destinoFinal = destino;
        UiUtil.async(
            () -> {
                ReportePdfService.exportarPdf("/reportes/goles.jrxml", params, rows, destinoFinal);
                return destinoFinal;
            },
            pdf -> {
                UiUtil.ocupado(false, piCarga, cmbLiga, btnExportar, tblGoles);
                Alert ok = new Alert(Alert.AlertType.INFORMATION,
                        "PDF guardado en:\n" + pdf.getAbsolutePath(), ButtonType.OK);
                ok.setTitle("Exportación exitosa");
                ok.setHeaderText("Reporte generado correctamente");
                ok.show();
                try { Desktop.getDesktop().open(pdf); } catch (Exception ignored) {}
            },
            ex -> {
                UiUtil.ocupado(false, piCarga, cmbLiga, btnExportar, tblGoles);
                UiUtil.error(ex);
            }
        );
    }
}
