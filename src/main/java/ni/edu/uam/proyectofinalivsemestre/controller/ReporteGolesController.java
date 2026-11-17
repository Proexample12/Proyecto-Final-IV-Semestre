package ni.edu.uam.proyectofinalivsemestre.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import ni.edu.uam.proyectofinalivsemestre.dao.MatchDao;
import ni.edu.uam.proyectofinalivsemestre.model.Match;
import ni.edu.uam.proyectofinalivsemestre.model.Team;
import ni.edu.uam.proyectofinalivsemestre.service.ReportePdfService;
import ni.edu.uam.proyectofinalivsemestre.util.UiUtil;

import java.awt.Desktop;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ReporteGolesController {

    // ── Records ──────────────────────────────────────────────────────────────
    public record FilaGoles(Integer jornada, String local, String marcador,
                            String visitante, int totalGoles, String resultado) {}

    // ── Competición IDs ──────────────────────────────────────────────────────
    private static final Map<String, Integer> LIGA_IDS = Map.of(
            "LaLiga", 2014,
            "Premier League", 2021,
            "Champions League", 2001
    );

    // ── FXML Navigation ──────────────────────────────────────────────────────
    @FXML private ToggleButton btnTabClasificacion, btnTabGoles, btnTabRendimiento;

    // ── FXML Controles y Filtros ─────────────────────────────────────────────
    @FXML private ComboBox<String> cmbLiga, cmbJornada;
    @FXML private Label lblTotalGoles, lblPromedio, lblMaxGoles, lblMinGoles,
            lblPartidosJugados, lblOver25, lblOver25Sub, lblMensaje,
            lblTituloGrafica, lblSubtituloGrafica, lblResumenTabla;
    @FXML private VBox panelKpis, panelGrafica;
    @FXML private BarChart<String, Number> chartGoles;
    @FXML private CategoryAxis axisX;
    @FXML private NumberAxis axisY;
    @FXML private TableView<FilaGoles> tblGoles;
    @FXML private TableColumn<FilaGoles, Object> colJornada, colLocal, colMarcador,
            colVisitante, colTotal, colResultado;
    @FXML private Button btnExportar, btnExportarCsv;
    @FXML private ProgressIndicator piCarga;

    private final MatchDao dao = new MatchDao();
    private List<Match> partidosLiga = List.of();
    private List<FilaGoles> filasCargadas = List.of();
    private boolean actualizandoFiltro = false;

    @FXML
    private void initialize() {
        cmbLiga.getItems().setAll("LaLiga", "Premier League", "Champions League");
        cmbLiga.setValue("LaLiga");

        tblGoles.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tblGoles.setPlaceholder(new Label("No hay datos disponibles. Sincroniza la aplicación primero."));

        col(colJornada, f -> f.jornada() == null ? "—" : "J" + f.jornada());
        col(colLocal, FilaGoles::local);
        col(colMarcador, FilaGoles::marcador);
        col(colVisitante, FilaGoles::visitante);
        col(colTotal, FilaGoles::totalGoles);
        col(colResultado, FilaGoles::resultado);

        // Estilo personalizado para la columna Resultado con badges tipo píldora
        colResultado.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Object item, boolean vacia) {
                super.updateItem(item, vacia);
                if (vacia || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String resultado = item.toString();
                    Label badge = new Label(resultado);
                    badge.getStyleClass().add("badge-resultado");
                    if ("Local".equalsIgnoreCase(resultado)) {
                        badge.getStyleClass().add("resultado-local");
                    } else if ("Visitante".equalsIgnoreCase(resultado)) {
                        badge.getStyleClass().add("resultado-visitante");
                    } else {
                        badge.getStyleClass().add("resultado-empate");
                    }
                    setText(null);
                    setGraphic(badge);
                }
            }
        });

        cmbLiga.valueProperty().addListener((o, a, n) -> cargar());
        cmbJornada.valueProperty().addListener((o, a, n) -> {
            if (!actualizandoFiltro) {
                aplicarFiltroJornada();
            }
        });

        cargar();
    }

    private <T> void col(TableColumn<FilaGoles, Object> c, Function<FilaGoles, T> fn) {
        c.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(fn.apply(cell.getValue())));
    }

    private void cargar() {
        String liga = cmbLiga.getValue();
        if (liga == null) return;
        int compId = LIGA_IDS.get(liga);

        UiUtil.ocupado(true, piCarga, cmbLiga, cmbJornada, btnExportar, btnExportarCsv, tblGoles);
        lblMensaje.setVisible(false);

        UiUtil.async(
                () -> dao.findFinishedByCompetitionOrdered(compId),
                partidos -> {
                    UiUtil.ocupado(false, piCarga, cmbLiga, cmbJornada, btnExportar, btnExportarCsv, tblGoles);
                    partidosLiga = partidos;

                    if (partidosLiga.isEmpty()) {
                        lblMensaje.setText("No hay datos de partidos disponibles para " + liga + ". Sincroniza la aplicación primero.");
                        lblMensaje.setVisible(true);
                        panelKpis.setVisible(false);
                        panelGrafica.setVisible(false);
                        tblGoles.getItems().clear();
                        filasCargadas = List.of();
                        cmbJornada.getItems().clear();
                        lblResumenTabla.setText("Sin datos");
                        return;
                    }

                    panelKpis.setVisible(true);
                    panelGrafica.setVisible(true);

                    // Poblar combo de jornadas disponibles
                    actualizandoFiltro = true;
                    cmbJornada.getItems().clear();
                    cmbJornada.getItems().add("Todas las jornadas");

                    Set<Integer> jornadas = partidosLiga.stream()
                            .map(Match::getMatchday)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toCollection(TreeSet::new));

                    for (Integer j : jornadas) {
                        cmbJornada.getItems().add("Jornada " + j);
                    }
                    cmbJornada.setValue("Todas las jornadas");
                    actualizandoFiltro = false;

                    mostrarPartidos(partidosLiga, null);
                },
                e -> {
                    UiUtil.ocupado(false, piCarga, cmbLiga, cmbJornada, btnExportar, btnExportarCsv, tblGoles);
                    UiUtil.error(e);
                }
        );
    }

    private void aplicarFiltroJornada() {
        String seleccion = cmbJornada.getValue();
        if (seleccion == null || seleccion.startsWith("Todas")) {
            mostrarPartidos(partidosLiga, null);
            return;
        }

        try {
            int jornadaSel = Integer.parseInt(seleccion.replace("Jornada ", "").trim());
            List<Match> filtrados = partidosLiga.stream()
                    .filter(m -> m.getMatchday() != null && m.getMatchday() == jornadaSel)
                    .toList();
            mostrarPartidos(filtrados, jornadaSel);
        } catch (Exception e) {
            mostrarPartidos(partidosLiga, null);
        }
    }

    private void mostrarPartidos(List<Match> partidos, Integer jornadaFiltro) {
        if (partidos.isEmpty()) {
            tblGoles.getItems().clear();
            filasCargadas = List.of();
            chartGoles.getData().clear();
            lblResumenTabla.setText("0 partidos");
            return;
        }

        int totalPartidos = partidos.size();
        int sumaGoles = partidos.stream()
                .mapToInt(m -> safe(m.getHomeScore()) + safe(m.getAwayScore()))
                .sum();

        BigDecimal prom = totalPartidos == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(sumaGoles)
                .divide(BigDecimal.valueOf(totalPartidos), 2, RoundingMode.HALF_UP);

        long over25 = partidos.stream()
                .filter(m -> safe(m.getHomeScore()) + safe(m.getAwayScore()) > 2)
                .count();

        double pctOver25 = totalPartidos == 0 ? 0.0 : (over25 * 100.0) / totalPartidos;

        Optional<Match> maxP = partidos.stream()
                .max(Comparator.comparingInt(m -> safe(m.getHomeScore()) + safe(m.getAwayScore())));
        Optional<Match> minP = partidos.stream()
                .min(Comparator.comparingInt(m -> safe(m.getHomeScore()) + safe(m.getAwayScore())));

        lblTotalGoles.setText(String.valueOf(sumaGoles));
        lblPromedio.setText(prom.toString());
        lblPartidosJugados.setText(String.valueOf(totalPartidos));
        lblOver25.setText(String.format(Locale.ROOT, "%.1f%%", pctOver25));
        lblOver25Sub.setText(over25 + " de " + totalPartidos + " partidos");

        lblMaxGoles.setText("🔥 Partido con más goles: " + maxP.map(this::descMatch).orElse("—"));
        lblMinGoles.setText("🔒 Partido con menos goles: " + minP.map(this::descMatch).orElse("—"));

        filasCargadas = partidos.stream().map(m -> {
            int h = safe(m.getHomeScore());
            int a = safe(m.getAwayScore());
            String resultado = h > a ? "Local" : (a > h ? "Visitante" : "Empate");
            return new FilaGoles(
                    m.getMatchday(),
                    m.getHomeTeam().getName(),
                    h + " - " + a,
                    m.getAwayTeam().getName(),
                    h + a,
                    resultado
            );
        }).toList();

        tblGoles.getItems().setAll(filasCargadas);

        if (jornadaFiltro != null) {
            lblResumenTabla.setText("Mostrando " + filasCargadas.size() + " partidos de la Jornada " + jornadaFiltro);
        } else {
            lblResumenTabla.setText("Mostrando " + filasCargadas.size() + " partidos disputados en la temporada");
        }

        actualizarGraficaGoles(partidos, jornadaFiltro);
    }

    private void actualizarGraficaGoles(List<Match> partidos, Integer jornadaFiltro) {
        chartGoles.getData().clear();

        if (jornadaFiltro == null) {
            // Visualización por todas las jornadas: Goles totales por Jornada
            lblTituloGrafica.setText("📊 Goles Totales por Jornada");
            lblSubtituloGrafica.setText("Evolución de anotaciones a lo largo del calendario");
            axisX.setLabel("Jornada");
            axisY.setLabel("Total de Goles");

            Map<Integer, Integer> golesPorJornada = new TreeMap<>();
            for (Match m : partidos) {
                if (m.getMatchday() != null) {
                    int goles = safe(m.getHomeScore()) + safe(m.getAwayScore());
                    golesPorJornada.merge(m.getMatchday(), goles, Integer::sum);
                }
            }

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName("Goles");

            for (Map.Entry<Integer, Integer> entry : golesPorJornada.entrySet()) {
                String labelJ = "J" + entry.getKey();
                int goles = entry.getValue();
                XYChart.Data<String, Number> item = new XYChart.Data<>(labelJ, goles);
                series.getData().add(item);
                instalarTooltip(item, "Jornada " + entry.getKey() + ": " + goles + " goles");
            }

            chartGoles.setLegendVisible(false);
            chartGoles.getData().add(series);
        } else {
            // Visualización por jornada específica: Goles por partido comparando Local vs Visitante
            lblTituloGrafica.setText("📊 Goles por Encuentro — Jornada " + jornadaFiltro);
            lblSubtituloGrafica.setText("Comparativa de goles: Equipo Local vs Visitante");
            axisX.setLabel("Partido");
            axisY.setLabel("Goles Anotados");

            XYChart.Series<String, Number> seriesLocal = new XYChart.Series<>();
            seriesLocal.setName("Goles Local");

            XYChart.Series<String, Number> seriesVisitante = new XYChart.Series<>();
            seriesVisitante.setName("Goles Visitante");

            for (Match m : partidos) {
                String etiquetaPartido = nombreCorto(m.getHomeTeam()) + " vs " + nombreCorto(m.getAwayTeam());
                int gLocal = safe(m.getHomeScore());
                int gVisita = safe(m.getAwayScore());

                XYChart.Data<String, Number> dataLocal = new XYChart.Data<>(etiquetaPartido, gLocal);
                seriesLocal.getData().add(dataLocal);
                instalarTooltip(dataLocal, m.getHomeTeam().getName() + " (Local): " + gLocal + " goles");

                XYChart.Data<String, Number> dataVisita = new XYChart.Data<>(etiquetaPartido, gVisita);
                seriesVisitante.getData().add(dataVisita);
                instalarTooltip(dataVisita, m.getAwayTeam().getName() + " (Visita): " + gVisita + " goles");
            }

            chartGoles.setLegendVisible(true);
            chartGoles.getData().addAll(seriesLocal, seriesVisitante);
        }
    }

    private void instalarTooltip(XYChart.Data<String, Number> data, String texto) {
        Node node = data.getNode();
        if (node != null) {
            Tooltip.install(node, new Tooltip(texto));
        } else {
            data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                if (newNode != null) {
                    Tooltip.install(newNode, new Tooltip(texto));
                }
            });
        }
    }

    private String nombreCorto(Team t) {
        if (t == null) return "???";
        if (t.getTla() != null && !t.getTla().isBlank()) return t.getTla();
        if (t.getShortName() != null && !t.getShortName().isBlank()) return t.getShortName();
        return t.getName();
    }

    private int safe(Integer v) { return v == null ? 0 : v; }

    private String descMatch(Match m) {
        return m.getHomeTeam().getName() + " " + safe(m.getHomeScore()) +
                " - " + safe(m.getAwayScore()) + " " + m.getAwayTeam().getName();
    }

    // ── Navegación entre reportes ────────────────────────────────────────────
    @FXML
    private void irAClasificacion() {
        UiUtil.cambiarVista(btnTabClasificacion, "reporte_clasificacion");
    }

    @FXML
    private void irARendimiento() {
        UiUtil.cambiarVista(btnTabRendimiento, "reporte_rendimiento");
    }

    // ── Exportación PDF (JasperReports) ──────────────────────────────────────
    @FXML
    private void onExportar() {
        if (filasCargadas.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "No hay datos para exportar.", ButtonType.OK).show();
            return;
        }
        String liga = cmbLiga.getValue();
        String filtroJ = cmbJornada.getValue();
        String sufijoJ = (filtroJ != null && !filtroJ.startsWith("Todas"))
                ? "_" + filtroJ.replace(" ", "")
                : "";
        String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String nombreSugerido = "goles_" + liga.replace(" ", "_") + sufijoJ + "_" + fecha + ".pdf";

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar Reporte PDF");
        chooser.setInitialFileName(nombreSugerido);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File destino = chooser.showSaveDialog(btnExportar.getScene().getWindow());
        if (destino == null) return;

        UiUtil.ocupado(true, piCarga, cmbLiga, cmbJornada, btnExportar, btnExportarCsv, tblGoles);

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

        // KPIs como parámetros requeridos por goles.jrxml
        Map<String, Object> params = new HashMap<>();
        params.put("LIGA_NOMBRE",   liga + (sufijoJ.isBlank() ? "" : " (" + filtroJ + ")"));
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
                UiUtil.ocupado(false, piCarga, cmbLiga, cmbJornada, btnExportar, btnExportarCsv, tblGoles);
                Alert ok = new Alert(Alert.AlertType.INFORMATION,
                        "PDF guardado en:\n" + pdf.getAbsolutePath(), ButtonType.OK);
                ok.setTitle("Exportación exitosa");
                ok.setHeaderText("Reporte generado correctamente");
                ok.show();
                try { Desktop.getDesktop().open(pdf); } catch (Exception ignored) {}
            },
            ex -> {
                UiUtil.ocupado(false, piCarga, cmbLiga, cmbJornada, btnExportar, btnExportarCsv, tblGoles);
                UiUtil.error(ex);
            }
        );
    }

    // ── Exportación CSV ──────────────────────────────────────────────────────
    @FXML
    private void onExportarCsv() {
        if (filasCargadas.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "No hay datos para exportar.", ButtonType.OK).show();
            return;
        }
        String liga = cmbLiga.getValue().replace(" ", "_");
        String filtroJ = cmbJornada.getValue();
        String sufijoJ = (filtroJ != null && !filtroJ.startsWith("Todas"))
                ? "_" + filtroJ.replace(" ", "")
                : "";
        String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String nombre = "goles_" + liga + sufijoJ + "_" + fecha + ".csv";
        String ruta = System.getProperty("user.home") + File.separator + nombre;

        try (PrintWriter pw = new PrintWriter(new FileWriter(ruta))) {
            pw.println("Jornada,Local,Marcador,Visitante,Total Goles,Resultado");
            for (FilaGoles f : filasCargadas) {
                pw.println((f.jornada() == null ? "" : f.jornada()) + "," +
                        escapeCsv(f.local()) + "," +
                        escapeCsv(f.marcador()) + "," +
                        escapeCsv(f.visitante()) + "," +
                        f.totalGoles() + "," +
                        escapeCsv(f.resultado()));
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
