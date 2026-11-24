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

    // ── FXML Navigation ──────────────────────────────────────────────────────
    @FXML private ToggleButton btnTabClasificacion, btnTabGoles, btnTabRendimiento;

    // ── FXML Controles y Filtros ─────────────────────────────────────────────
    @FXML private ComboBox<String> cmbLiga, cmbJornada;
    @FXML private Label lblLider, lblDefensa, lblAtaque, lblDescenso, lblMensaje, lblResumenTabla;
    @FXML private VBox panelKpis;
    @FXML private TableView<FilaReporte> tblClasificacion;
    @FXML private TableColumn<FilaReporte, Object> colPos, colEquipo, colPj, colG, colE, colP,
            colGf, colGc, colDg, colPts, colRacha;
    @FXML private Button btnExportar, btnExportarCsv;
    @FXML private ProgressIndicator piCarga;

    private final StandingDao standingDao = new StandingDao();
    private final MatchDao matchDao = new MatchDao();

    private List<Standing> standingsOriginales = List.of();
    private List<Match> partidosFinalizados = List.of();
    private List<FilaReporte> filasCargadas = List.of();
    private boolean actualizandoFiltro = false;

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

        // Estilo de franja azul para zona Champions (posiciones 1 a 4)
        tblClasificacion.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(FilaReporte item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("zona-champions");
                if (!empty && item != null && item.pos() <= 4) {
                    getStyleClass().add("zona-champions");
                }
            }
        });

        // Estilo visual tipo badge para la racha (V: verde, E: amarillo, D: rojo)
        colRacha.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Object item, boolean vacia) {
                super.updateItem(item, vacia);
                if (vacia || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String racha = item.toString();
                    Label badge = new Label(racha);
                    badge.getStyleClass().add("badge-racha");
                    if ("V".equalsIgnoreCase(racha)) {
                        badge.getStyleClass().add("racha-victoria");
                    } else if ("D".equalsIgnoreCase(racha)) {
                        badge.getStyleClass().add("racha-derrota");
                    } else {
                        badge.getStyleClass().add("racha-empate");
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

    private <T> void col(TableColumn<FilaReporte, Object> c, Function<FilaReporte, T> fn) {
        c.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(fn.apply(cell.getValue())));
    }

    private void cargar() {
        String liga = cmbLiga.getValue();
        if (liga == null) return;
        int compId = LIGA_IDS.get(liga);

        UiUtil.ocupado(true, piCarga, cmbLiga, cmbJornada, btnExportar, btnExportarCsv, tblClasificacion);
        lblMensaje.setVisible(false);

        UiUtil.async(
                () -> {
                    List<Standing> standings = standingDao.findStandingsByCompetitionOrdered(compId);
                    List<Match> matches = matchDao.findFinishedMatchesByCompetition(compId);
                    return new DatosCargados(standings, matches);
                },
                datos -> {
                    UiUtil.ocupado(false, piCarga, cmbLiga, cmbJornada, btnExportar, btnExportarCsv, tblClasificacion);
                    standingsOriginales = datos.standings();
                    partidosFinalizados = datos.matches();

                    if (standingsOriginales.isEmpty()) {
                        lblMensaje.setText("No hay datos disponibles para " + liga + ". Sincroniza la aplicación primero.");
                        lblMensaje.setVisible(true);
                        panelKpis.setVisible(false);
                        tblClasificacion.getItems().clear();
                        filasCargadas = List.of();
                        cmbJornada.getItems().clear();
                        lblResumenTabla.setText("Sin datos");
                        return;
                    }

                    panelKpis.setVisible(true);

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

                    mostrarClasificacionOficial();
                },
                e -> {
                    UiUtil.ocupado(false, piCarga, cmbLiga, cmbJornada, btnExportar, btnExportarCsv, tblClasificacion);
                    UiUtil.error(e);
                }
        );
    }

    private record DatosCargados(List<Standing> standings, List<Match> matches) {}

    private void mostrarClasificacionOficial() {
        String liga = cmbLiga.getValue();
        int compId = LIGA_IDS.getOrDefault(liga, 2014);
        int descensoPosMin = compId == 2001 ? 24 : 18;

        // KPIs calculados
        Optional<Standing> lider = standingsOriginales.stream()
                .filter(s -> s.getPosition() == 1).findFirst();
        Optional<Standing> mejorDefensa = standingsOriginales.stream()
                .filter(s -> s.getPlayedGames() > 0)
                .min(Comparator.comparingInt(Standing::getGoalsAgainst));
        Optional<Standing> mejorAtaque = standingsOriginales.stream()
                .max(Comparator.comparingInt(Standing::getGoalsFor));
        Optional<Standing> descenso = standingsOriginales.stream()
                .filter(s -> s.getPosition() >= descensoPosMin)
                .max(Comparator.comparingInt(Standing::getPosition));

        lblLider.setText(lider.map(s -> s.getTeam().getName() + " (" + s.getPoints() + " pts)").orElse("—"));
        lblDefensa.setText(mejorDefensa.map(s -> s.getTeam().getName() + " (" + s.getGoalsAgainst() + " GC)").orElse("—"));
        lblAtaque.setText(mejorAtaque.map(s -> s.getTeam().getName() + " (" + s.getGoalsFor() + " GF)").orElse("—"));
        lblDescenso.setText(descenso.map(s -> s.getTeam().getName() + " (pos " + s.getPosition() + ")").orElse("—"));

        // Filas tabla
        filasCargadas = standingsOriginales.stream().map(s -> new FilaReporte(
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
        lblResumenTabla.setText("Mostrando " + filasCargadas.size() + " equipos (clasificación oficial acumulada)");
    }

    private void aplicarFiltroJornada() {
        String seleccion = cmbJornada.getValue();
        if (seleccion == null || seleccion.startsWith("Todas")) {
            mostrarClasificacionOficial();
            return;
        }

        try {
            int jornadaMax = Integer.parseInt(seleccion.replace("Hasta Jornada ", "").trim());
            calcularClasificacionHastaJornada(jornadaMax);
        } catch (Exception e) {
            mostrarClasificacionOficial();
        }
    }

    private static class AcumuladorEquipo {
        final Team team;
        int pj = 0, g = 0, e = 0, p = 0, gf = 0, gc = 0, pts = 0;
        String ultimaRacha = "—";

        AcumuladorEquipo(Team team) { this.team = team; }
    }

    private void calcularClasificacionHastaJornada(int hastaJornada) {
        String liga = cmbLiga.getValue();
        int compId = LIGA_IDS.getOrDefault(liga, 2014);
        int descensoPosMin = compId == 2001 ? 24 : 18;

        Map<Integer, AcumuladorEquipo> mapa = new HashMap<>();

        for (Standing s : standingsOriginales) {
            mapa.put(s.getTeam().getId(), new AcumuladorEquipo(s.getTeam()));
        }

        List<Match> partidosFiltrados = partidosFinalizados.stream()
                .filter(m -> m.getMatchday() != null && m.getMatchday() <= hastaJornada)
                .sorted(Comparator.comparing(Match::getUtcDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        for (Match m : partidosFiltrados) {
            Team home = m.getHomeTeam();
            Team away = m.getAwayTeam();
            if (home == null || away == null) continue;

            AcumuladorEquipo aHome = mapa.computeIfAbsent(home.getId(), k -> new AcumuladorEquipo(home));
            AcumuladorEquipo aAway = mapa.computeIfAbsent(away.getId(), k -> new AcumuladorEquipo(away));

            int hs = m.getHomeScore() == null ? 0 : m.getHomeScore();
            int as = m.getAwayScore() == null ? 0 : m.getAwayScore();

            aHome.pj++;
            aAway.pj++;
            aHome.gf += hs;
            aHome.gc += as;
            aAway.gf += as;
            aAway.gc += hs;

            if (hs > as) {
                aHome.g++;
                aHome.pts += 3;
                aHome.ultimaRacha = "V";
                aAway.p++;
                aAway.ultimaRacha = "D";
            } else if (as > hs) {
                aAway.g++;
                aAway.pts += 3;
                aAway.ultimaRacha = "V";
                aHome.p++;
                aHome.ultimaRacha = "D";
            } else {
                aHome.e++;
                aHome.pts += 1;
                aHome.ultimaRacha = "E";
                aAway.e++;
                aAway.pts += 1;
                aAway.ultimaRacha = "E";
            }
        }

        List<AcumuladorEquipo> ordenados = mapa.values().stream()
                .sorted(Comparator.comparingInt((AcumuladorEquipo a) -> a.pts).reversed()
                        .thenComparingInt(a -> (a.gf - a.gc)).reversed()
                        .thenComparingInt(a -> a.gf).reversed()
                        .thenComparing(a -> a.team.getName()))
                .toList();

        List<FilaReporte> filas = new ArrayList<>();
        int pos = 1;
        for (AcumuladorEquipo a : ordenados) {
            filas.add(new FilaReporte(
                    pos++,
                    a.team.getName(),
                    a.pj,
                    a.g,
                    a.e,
                    a.p,
                    a.gf,
                    a.gc,
                    a.gf - a.gc,
                    a.pts,
                    a.ultimaRacha
            ));
        }

        filasCargadas = filas;
        tblClasificacion.getItems().setAll(filasCargadas);

        // Actualizar KPIs calculados
        if (!filas.isEmpty()) {
            FilaReporte lid = filas.get(0);
            lblLider.setText(lid.equipo() + " (" + lid.pts() + " pts)");

            Optional<FilaReporte> mDef = filas.stream().min(Comparator.comparingInt(FilaReporte::gc));
            lblDefensa.setText(mDef.map(f -> f.equipo() + " (" + f.gc() + " GC)").orElse("—"));

            Optional<FilaReporte> mAta = filas.stream().max(Comparator.comparingInt(FilaReporte::gf));
            lblAtaque.setText(mAta.map(f -> f.equipo() + " (" + f.gf() + " GF)").orElse("—"));

            Optional<FilaReporte> desc = filas.stream()
                    .filter(f -> f.pos() >= descensoPosMin)
                    .max(Comparator.comparingInt(FilaReporte::pos));
            if (desc.isEmpty() && filas.size() > 1) {
                desc = Optional.of(filas.get(filas.size() - 1));
            }
            lblDescenso.setText(desc.map(f -> f.equipo() + " (pos " + f.pos() + ")").orElse("—"));
        }

        lblResumenTabla.setText("Mostrando " + filasCargadas.size() + " equipos (acumulado hasta Jornada " + hastaJornada + ")");
    }

    private String racha(Standing s) {
        if (s.getWon() > s.getLost()) return "V";
        if (s.getLost() > s.getWon()) return "D";
        return "E";
    }

    // ── Navegación entre reportes ────────────────────────────────────────────
    @FXML
    private void irAGoles() {
        UiUtil.cambiarVista(btnTabGoles, "reporte_goles");
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
        String nombreSugerido = "clasificacion_" + liga.replace(" ", "_") + sufijoJ + "_" + fecha + ".pdf";

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Guardar Reporte PDF");
        chooser.setInitialFileName(nombreSugerido);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File destino = chooser.showSaveDialog(btnExportar.getScene().getWindow());
        if (destino == null) return;

        UiUtil.ocupado(true, piCarga, cmbLiga, cmbJornada, btnExportar, btnExportarCsv, tblClasificacion);

        // Convertir records a Maps para JRMapCollectionDataSource
        List<Map<String, ?>> rows = new ArrayList<>();
        for (FilaReporte f : filasCargadas) {
            Map<String, Object> m = new HashMap<>();
            m.put("pos",    f.pos());
            m.put("equipo", f.equipo());
            m.put("pj",     f.pj());
            m.put("g",      f.g());
            m.put("e",      f.e());
            m.put("p",      f.p());
            m.put("gf",     f.gf());
            m.put("gc",     f.gc());
            m.put("dg",     f.dg());
            m.put("pts",    f.pts());
            m.put("racha",  f.racha());
            rows.add(m);
        }

        Map<String, Object> params = new HashMap<>();
        params.put("LIGA_NOMBRE",   liga + (sufijoJ.isBlank() ? "" : " (" + filtroJ + ")"));
        params.put("FECHA_EMISION", fecha);

        File destinoFinal = destino;
        UiUtil.async(
            () -> {
                ReportePdfService.exportarPdf("/reportes/clasificacion.jrxml", params, rows, destinoFinal);
                return destinoFinal;
            },
            pdf -> {
                UiUtil.ocupado(false, piCarga, cmbLiga, cmbJornada, btnExportar, btnExportarCsv, tblClasificacion);
                Alert ok = new Alert(Alert.AlertType.INFORMATION,
                        "PDF guardado en:\n" + pdf.getAbsolutePath(), ButtonType.OK);
                ok.setTitle("Exportación exitosa");
                ok.setHeaderText("Reporte generado correctamente");
                ok.show();
                try { Desktop.getDesktop().open(pdf); } catch (Exception ignored) {}
            },
            ex -> {
                UiUtil.ocupado(false, piCarga, cmbLiga, cmbJornada, btnExportar, btnExportarCsv, tblClasificacion);
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
        String nombre = "clasificacion_" + liga + sufijoJ + "_" + fecha + ".csv";
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
