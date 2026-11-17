package ni.edu.uam.proyectofinalivsemestre.controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import ni.edu.uam.proyectofinalivsemestre.dao.MatchDao;
import ni.edu.uam.proyectofinalivsemestre.dao.StandingDao;
import ni.edu.uam.proyectofinalivsemestre.model.Match;
import ni.edu.uam.proyectofinalivsemestre.model.Standing;
import ni.edu.uam.proyectofinalivsemestre.model.Team;
import ni.edu.uam.proyectofinalivsemestre.service.ReportePdfService;
import ni.edu.uam.proyectofinalivsemestre.util.UiUtil;

import java.awt.Desktop;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
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

    // ── FXML Navigation ──────────────────────────────────────────────────────
    @FXML private ToggleButton btnTabClasificacion, btnTabGoles, btnTabRendimiento;

    // ── FXML Controles y Filtros ─────────────────────────────────────────────
    @FXML private ComboBox<String> cmbLiga, cmbJornada;
    @FXML private Label lblMaxEfectividad, lblPromEfectividad, lblMaxGolesPartido,
            lblMasSolido, lblMensaje, lblResumenTabla;
    @FXML private VBox panelKpis, panelContenido;
    @FXML private TableView<FilaRendimiento> tblRendimiento;
    @FXML private TableColumn<FilaRendimiento, Object> colPos, colEquipo, colPj, colPts,
            colEfectividad, colGolesPartido, colSolidez, colZona;
    @FXML private Button btnExportar, btnExportarCsv;
    @FXML private ProgressIndicator piCarga;

    private final StandingDao standingDao = new StandingDao();
    private final MatchDao matchDao = new MatchDao();

    private List<Standing> standingsOriginales = List.of();
    private List<Match> partidosFinalizados = List.of();
    private List<FilaRendimiento> filasCargadas = List.of();
    private boolean actualizandoFiltro = false;

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

        // Estilo visual con píldoras para la columna Zona
        colZona.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Object item, boolean vacia) {
                super.updateItem(item, vacia);
                if (vacia || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String zona = item.toString();
                    Label badge = new Label(zona);
                    badge.getStyleClass().add("badge-zona");
                    if (zona.contains("Champions")) {
                        badge.getStyleClass().add("zona-champions-pill");
                    } else if (zona.contains("Europa")) {
                        badge.getStyleClass().add("zona-europa-pill");
                    } else if (zona.contains("Descenso")) {
                        badge.getStyleClass().add("zona-descenso-pill");
                    } else {
                        badge.getStyleClass().add("zona-mitad-pill");
                    }
                    setText(null);
                    setGraphic(badge);
                }
            }
        });

        // Estilo visual con badges para la columna Solidez
        colSolidez.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Object item, boolean vacia) {
                super.updateItem(item, vacia);
                if (vacia || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String solidez = item.toString();
                    Label badge = new Label(solidez);
                    badge.getStyleClass().add("badge-solidez");
                    if ("Alta".equalsIgnoreCase(solidez)) {
                        badge.getStyleClass().add("badge-solidez-alta");
                    } else if ("Baja".equalsIgnoreCase(solidez)) {
                        badge.getStyleClass().add("badge-solidez-baja");
                    } else {
                        badge.getStyleClass().add("badge-solidez-media");
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

    private <T> void col(TableColumn<FilaRendimiento, Object> c, Function<FilaRendimiento, T> fn) {
        c.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(fn.apply(cell.getValue())));
    }

    private void cargar() {
        String liga = cmbLiga.getValue();
        if (liga == null) return;
        int compId = LIGA_IDS.get(liga);

        UiUtil.ocupado(true, piCarga, cmbLiga, cmbJornada, btnExportar, btnExportarCsv, tblRendimiento);
        lblMensaje.setVisible(false);

        UiUtil.async(
                () -> {
                    List<Standing> standings = standingDao.findStandingsByCompetitionOrdered(compId);
                    List<Match> matches = matchDao.findFinishedByCompetitionOrdered(compId);
                    return new DatosCargados(standings, matches);
                },
                datos -> {
                    UiUtil.ocupado(false, piCarga, cmbLiga, cmbJornada, btnExportar, btnExportarCsv, tblRendimiento);
                    standingsOriginales = datos.standings();
                    partidosFinalizados = datos.matches();

                    if (standingsOriginales.isEmpty()) {
                        lblMensaje.setText("No hay datos disponibles para " + liga + ". Sincroniza la aplicación primero.");
                        lblMensaje.setVisible(true);
                        panelKpis.setVisible(false);
                        panelContenido.setVisible(false);
                        tblRendimiento.getItems().clear();
                        filasCargadas = List.of();
                        cmbJornada.getItems().clear();
                        lblResumenTabla.setText("Sin datos");
                        return;
                    }

                    panelKpis.setVisible(true);
                    panelContenido.setVisible(true);

                    // Llenar combo de jornadas detectadas
                    actualizandoFiltro = true;
                    cmbJornada.getItems().clear();
                    cmbJornada.getItems().add("Todas las jornadas");

                    Set<Integer> jornadas = new TreeSet<>();
                    for (Match m : partidosFinalizados) {
                        if (m.getMatchday() != null) {
                            jornadas.add(m.getMatchday());
                        }
                    }
                    for (Integer j : jornadas) {
                        cmbJornada.getItems().add("Hasta Jornada " + j);
                    }
                    cmbJornada.setValue("Todas las jornadas");
                    actualizandoFiltro = false;

                    mostrarRendimientoOficial();
                },
                e -> {
                    UiUtil.ocupado(false, piCarga, cmbLiga, cmbJornada, btnExportar, btnExportarCsv, tblRendimiento);
                    UiUtil.error(e);
                }
        );
    }

    private record DatosCargados(List<Standing> standings, List<Match> matches) {}

    private void mostrarRendimientoOficial() {
        filasCargadas = standingsOriginales.stream()
                .map(s -> {
                    int pos = s.getPosition();
                    int pj = s.getPlayedGames();
                    int pts = s.getPoints();

                    double ef = pj > 0 ? (pts / (pj * 3.0)) * 100 : 0.0;
                    String efectividad = String.format(Locale.ROOT, "%.1f%%", ef);

                    double gpp = pj > 0 ? (double) s.getGoalsFor() / pj : 0.0;
                    String golesPartido = String.format(Locale.ROOT, "%.2f", gpp);

                    double solidezVal = pj > 0 ? 1.0 - ((double) s.getGoalsAgainst() / (pj * 3.0)) : 1.0;
                    String solidez = solidezVal > 0.7 ? "Alta" : (solidezVal > 0.4 ? "Media" : "Baja");

                    String zona;
                    if (pos <= 4) zona = "🏆 Champions";
                    else if (pos <= 6) zona = "⚽ Europa";
                    else if (pos <= 17) zona = "🔄 Mitad";
                    else zona = "⬇ Descenso";

                    return new FilaRendimiento(pos,
                            s.getTeam().getName(), pj, pts,
                            efectividad, golesPartido, solidez, zona);
                })
                .sorted(Comparator.comparingDouble(
                        (FilaRendimiento f) -> Double.parseDouble(
                                f.efectividad().replace("%", "").replace(",", ".")
                        )).reversed())
                .toList();

        tblRendimiento.getItems().setAll(filasCargadas);
        actualizarKpisRendimiento();
        lblResumenTabla.setText("Mostrando " + filasCargadas.size() + " equipos ordenados por efectividad");
    }

    private void aplicarFiltroJornada() {
        String seleccion = cmbJornada.getValue();
        if (seleccion == null || seleccion.startsWith("Todas")) {
            mostrarRendimientoOficial();
            return;
        }

        try {
            int jornadaMax = Integer.parseInt(seleccion.replace("Hasta Jornada ", "").trim());
            calcularRendimientoHastaJornada(jornadaMax);
        } catch (Exception e) {
            mostrarRendimientoOficial();
        }
    }

    private static class AcumuladorRendimiento {
        final Team team;
        int pj = 0, pts = 0, gf = 0, gc = 0;

        AcumuladorRendimiento(Team team) { this.team = team; }
    }

    private void calcularRendimientoHastaJornada(int hastaJornada) {
        Map<Long, AcumuladorRendimiento> mapa = new HashMap<>();

        for (Standing s : standingsOriginales) {
            mapa.put(s.getTeam().getId(), new AcumuladorRendimiento(s.getTeam()));
        }

        List<Match> partidosFiltrados = partidosFinalizados.stream()
                .filter(m -> m.getMatchday() != null && m.getMatchday() <= hastaJornada)
                .toList();

        for (Match m : partidosFiltrados) {
            Team home = m.getHomeTeam();
            Team away = m.getAwayTeam();
            if (home == null || away == null) continue;

            AcumuladorRendimiento aHome = mapa.computeIfAbsent(home.getId(), k -> new AcumuladorRendimiento(home));
            AcumuladorRendimiento aAway = mapa.computeIfAbsent(away.getId(), k -> new AcumuladorRendimiento(away));

            int hs = m.getHomeScore() == null ? 0 : m.getHomeScore();
            int as = m.getAwayScore() == null ? 0 : m.getAwayScore();

            aHome.pj++;
            aAway.pj++;
            aHome.gf += hs;
            aHome.gc += as;
            aAway.gf += as;
            aAway.gc += hs;

            if (hs > as) {
                aHome.pts += 3;
            } else if (as > hs) {
                aAway.pts += 3;
            } else {
                aHome.pts += 1;
                aAway.pts += 1;
            }
        }

        // Posiciones generales en el torneo acumulado
        List<AcumuladorRendimiento> ordenTorneo = mapa.values().stream()
                .sorted(Comparator.comparingInt((AcumuladorRendimiento a) -> a.pts).reversed()
                        .thenComparingInt(a -> (a.gf - a.gc)).reversed()
                        .thenComparingInt(a -> a.gf).reversed()
                        .thenComparing(a -> a.team.getName()))
                .toList();

        Map<Long, Integer> posicionesTorneo = new HashMap<>();
        for (int i = 0; i < ordenTorneo.size(); i++) {
            posicionesTorneo.put(ordenTorneo.get(i).team.getId(), i + 1);
        }

        filasCargadas = mapa.values().stream()
                .map(s -> {
                    int pos = posicionesTorneo.getOrDefault(s.team.getId(), 1);
                    int pj = s.pj;
                    int pts = s.pts;

                    double ef = pj > 0 ? (pts / (pj * 3.0)) * 100 : 0.0;
                    String efectividad = String.format(Locale.ROOT, "%.1f%%", ef);

                    double gpp = pj > 0 ? (double) s.gf / pj : 0.0;
                    String golesPartido = String.format(Locale.ROOT, "%.2f", gpp);

                    double solidezVal = pj > 0 ? 1.0 - ((double) s.gc / (pj * 3.0)) : 1.0;
                    String solidez = solidezVal > 0.7 ? "Alta" : (solidezVal > 0.4 ? "Media" : "Baja");

                    String zona;
                    if (pos <= 4) zona = "🏆 Champions";
                    else if (pos <= 6) zona = "⚽ Europa";
                    else if (pos <= 17) zona = "🔄 Mitad";
                    else zona = "⬇ Descenso";

                    return new FilaRendimiento(pos,
                            s.team.getName(), pj, pts,
                            efectividad, golesPartido, solidez, zona);
                })
                .sorted(Comparator.comparingDouble(
                        (FilaRendimiento f) -> Double.parseDouble(
                                f.efectividad().replace("%", "").replace(",", ".")
                        )).reversed())
                .toList();

        tblRendimiento.getItems().setAll(filasCargadas);
        actualizarKpisRendimiento();
        lblResumenTabla.setText("Mostrando " + filasCargadas.size() + " equipos (acumulado hasta Jornada " + hastaJornada + ")");
    }

    private void actualizarKpisRendimiento() {
        if (filasCargadas.isEmpty()) {
            lblMaxEfectividad.setText("—");
            lblPromEfectividad.setText("0.0%");
            lblMaxGolesPartido.setText("—");
            lblMasSolido.setText("—");
            return;
        }

        // 1. Más efectivo
        FilaRendimiento topEf = filasCargadas.get(0);
        lblMaxEfectividad.setText(topEf.equipo() + " (" + topEf.efectividad() + ")");

        // 2. Promedio de efectividad de la liga
        double sumaEf = 0.0;
        for (FilaRendimiento f : filasCargadas) {
            sumaEf += Double.parseDouble(f.efectividad().replace("%", "").replace(",", "."));
        }
        double promEf = sumaEf / filasCargadas.size();
        lblPromEfectividad.setText(String.format(Locale.ROOT, "%.1f%%", promEf));

        // 3. Mejor promedio goleador
        Optional<FilaRendimiento> maxGoles = filasCargadas.stream()
                .max(Comparator.comparingDouble(f -> Double.parseDouble(f.golesPartido().replace(",", "."))));
        lblMaxGolesPartido.setText(maxGoles.map(f -> f.equipo() + " (" + f.golesPartido() + " G/P)").orElse("—"));

        // 4. Equipo con mayor solidez
        Optional<FilaRendimiento> solido = filasCargadas.stream()
                .filter(f -> "Alta".equals(f.solidez()))
                .findFirst();
        lblMasSolido.setText(solido.map(FilaRendimiento::equipo).orElse("—"));
    }

    // ── Navegación entre reportes ────────────────────────────────────────────
    @FXML
    private void irAClasificacion() {
        UiUtil.cambiarVista(btnTabClasificacion, "reporte_clasificacion");
    }

    @FXML
    private void irAGoles() {
        UiUtil.cambiarVista(btnTabGoles, "reporte_goles");
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
        String nombreSugerido = "rendimiento_" + liga.replace(" ", "_") + sufijoJ + "_" + fecha + ".pdf";

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar Reporte PDF");
        chooser.setInitialFileName(nombreSugerido);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File destino = chooser.showSaveDialog(btnExportar.getScene().getWindow());
        if (destino == null) return;

        UiUtil.ocupado(true, piCarga, cmbLiga, cmbJornada, btnExportar, btnExportarCsv, tblRendimiento);

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

        Map<String, Object> params = new HashMap<>();
        params.put("LIGA_NOMBRE",   liga + (sufijoJ.isBlank() ? "" : " (" + filtroJ + ")"));
        params.put("FECHA_EMISION", fecha);

        File destinoFinal = destino;
        UiUtil.async(
            () -> {
                ReportePdfService.exportarPdf("/reportes/rendimiento.jrxml", params, rows, destinoFinal);
                return destinoFinal;
            },
            pdf -> {
                UiUtil.ocupado(false, piCarga, cmbLiga, cmbJornada, btnExportar, btnExportarCsv, tblRendimiento);
                Alert ok = new Alert(Alert.AlertType.INFORMATION,
                        "PDF guardado en:\n" + pdf.getAbsolutePath(), ButtonType.OK);
                ok.setTitle("Exportación exitosa");
                ok.setHeaderText("Reporte generado correctamente");
                ok.show();
                try { Desktop.getDesktop().open(pdf); } catch (Exception ignored) {}
            },
            ex -> {
                UiUtil.ocupado(false, piCarga, cmbLiga, cmbJornada, btnExportar, btnExportarCsv, tblRendimiento);
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
        String nombre = "rendimiento_" + liga + sufijoJ + "_" + fecha + ".csv";
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
