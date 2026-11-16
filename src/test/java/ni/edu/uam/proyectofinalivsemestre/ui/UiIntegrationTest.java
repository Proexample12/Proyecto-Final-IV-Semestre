package ni.edu.uam.proyectofinalivsemestre.ui;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.*;
import ni.edu.uam.proyectofinalivsemestre.model.*;
import ni.edu.uam.proyectofinalivsemestre.service.Servicios;
import ni.edu.uam.proyectofinalivsemestre.service.stub.DatosDemo;
import ni.edu.uam.proyectofinalivsemestre.util.UiUtil;
import org.junit.jupiter.api.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

/** Prueba de integración local: necesita una sesión gráfica, no BD ni API. */
class UiIntegrationTest {
    private static Stage stage;
    private static Parent root;
    private static final List<Throwable> errores = new CopyOnWriteArrayList<>();

    @BeforeAll static void iniciar() throws Exception {
        CountDownLatch iniciado = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); iniciado.countDown(); });
        assertTrue(iniciado.await(10, TimeUnit.SECONDS));
        fx(() -> {
            Thread.currentThread().setUncaughtExceptionHandler((t,e) -> errores.add(e));
            root = FXMLLoader.load(UiIntegrationTest.class.getResource("/views/main.fxml"));
            Scene scene = new Scene(root, 1100, 680);
            scene.getStylesheets().add(UiIntegrationTest.class.getResource("/css/styles.css").toExternalForm());
            stage = new Stage(); stage.setScene(scene); stage.show();
            return null;
        });
    }
    @AfterAll static void cerrar() throws Exception {
        DatosDemo.FALLAR_SINCRONIZACION = false;
        fx(() -> { for (Window w : List.copyOf(Window.getWindows())) w.hide(); return null; });
        UiUtil.cerrar(); Platform.exit();
    }
    private static <T> T fx(Callable<T> accion) throws Exception {
        FutureTask<T> tarea = new FutureTask<>(accion);
        Platform.runLater(tarea); return tarea.get(10, TimeUnit.SECONDS);
    }
    @SuppressWarnings("unchecked") private static <T extends Node> T control(String id) {
        Node n = root.lookup("#" + id); assertNotNull(n, id); return (T) n;
    }
    private static void esperar(BooleanSupplier condicion) throws Exception {
        long fin = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        while (!fx(condicion::getAsBoolean) && System.nanoTime() < fin) Thread.sleep(25);
        assertTrue(fx(condicion::getAsBoolean), "La operación asíncrona no terminó");
    }
    private static void listo() throws Exception {
        esperar(() -> !UiIntegrationTest.<ProgressIndicator>control("piCarga").isVisible());
    }
    private static void pulsar(String id) throws Exception {
        fx(() -> { UiIntegrationTest.<ButtonBase>control(id).fire(); return null; });
    }
    private static void cerrarAlertas() throws Exception {
        fx(() -> { for (Window w : List.copyOf(Window.getWindows())) if (w != stage) w.hide(); return null; });
    }
    private static void dobleClic(TableRow<?> fila) {
        fila.getOnMouseClicked().handle(new MouseEvent(MouseEvent.MOUSE_CLICKED,
                0,0,0,0, MouseButton.PRIMARY,2,false,false,false,false,
                true,false,false,false,false,false,null));
    }
    @Test void recorridoCompletoConCacheYFavoritos() throws Exception {
        listo();
        assertEquals(6, fx(() -> UiIntegrationTest.<TableView<?>>control("tblPartidos").getItems().size()));
        assertEquals(2, Servicios.favoritos().idsPartidosFavoritos().size());
        fx(() -> {
            var label = ni.edu.uam.proyectofinalivsemestre.util.Escudos.equipo(new Equipo(999L,"Real Racing Club de Santander"));
            label.setPrefWidth(145);
            assertTrue(label.isWrapText());
            assertEquals("Real Racing Club de Santander",label.getText());
            return null;
        });
        fx(() -> { UiIntegrationTest.<TextField>control("txtBusqueda").setText("  ARSENAL  "); return null; });
        assertEquals(1, fx(() -> UiIntegrationTest.<TableView<?>>control("tblPartidos").getItems().size()));
        fx(() -> { UiIntegrationTest.<TextField>control("txtBusqueda").clear(); return null; });
        double alto = fx(() -> control("tblPartidos").getBoundsInParent().getHeight());
        fx(() -> { stage.setHeight(900); return null; });
        esperar(() -> control("tblPartidos").getBoundsInParent().getHeight() > alto + 100);
        fx(() -> { stage.setHeight(680); return null; });

        fx(() -> {
            UiIntegrationTest.<ComboBox<String>>control("cmbLiga").setValue("LaLiga"); return null;
        });
        listo();
        assertEquals(2, fx(() -> UiIntegrationTest.<TableView<?>>control("tblPartidos").getItems().size()));
        fx(() -> { UiIntegrationTest.<DatePicker>control("dpFecha").setValue(LocalDate.of(2026,11,11)); return null; });
        listo();
        assertEquals(0, fx(() -> UiIntegrationTest.<TableView<?>>control("tblPartidos").getItems().size()));
        fx(() -> { UiIntegrationTest.<DatePicker>control("dpFecha").setValue(DatosDemo.FECHA); return null; });
        listo();

        DatosDemo.FALLAR_SINCRONIZACION = true;
        pulsar("btnActualizar");
        assertTrue(fx(() -> UiIntegrationTest.<ProgressIndicator>control("piCarga").isVisible()));
        // Un pulso responde mientras sincronizar duerme en segundo plano.
        assertEquals("hilo activo", fx(() -> "hilo activo"));
        listo();
        assertEquals(2, fx(() -> UiIntegrationTest.<TableView<?>>control("tblPartidos").getItems().size()));
        assertTrue(fx(() -> Window.getWindows().size() > 1), "Debe aparecer una alerta");
        cerrarAlertas(); DatosDemo.FALLAR_SINCRONIZACION = false;

        fx(() -> {
            TableView<Partido> tabla = control("tblPartidos"); tabla.applyCss(); tabla.layout();
            TableRow<?> fila = tabla.lookupAll(".table-row-cell").stream()
                    .filter(n -> n instanceof TableRow<?> r && r.getItem() instanceof Partido partido && partido.getId().equals(2L))
                    .map(n -> (TableRow<?>)n).findFirst().orElseThrow();
            dobleClic(fila); return null;
        });
        listo();
        assertEquals("2 - 1", fx(() -> UiIntegrationTest.<Label>control("lblMarcador").getText()));
        assertEquals(0.54, fx(() -> UiIntegrationTest.<ProgressBar>control("pbPosesion").getProgress()), 0.001);
        fx(() -> {
            ((TabPane)root.lookup(".tab-pane")).getSelectionModel().select(2);
            root.applyCss(); root.layout();
            UiIntegrationTest.<Button>control("btnAbrirAlineaciones").fire();
            var popup=Window.getWindows().stream().filter(w->w instanceof Stage st && st.getTitle() != null && st.getTitle().startsWith("Alineaciones ·")).findFirst().orElseThrow();
            assertNotSame(stage,popup); popup.hide();
            ((TabPane)root.lookup(".tab-pane")).getSelectionModel().select(0);
            return null;
        });
        pulsar("btnFavorito"); listo();
        assertFalse(Servicios.favoritos().idsPartidosFavoritos().contains(2L));
        assertEquals("Agregar a favoritos", fx(() -> UiIntegrationTest.<Button>control("btnFavorito").getAccessibleText()));
        pulsar("btnFavorito"); listo();
        assertTrue(Servicios.favoritos().idsPartidosFavoritos().contains(2L));
        DatosDemo.FALLAR_SINCRONIZACION = true;
        pulsar("btnActualizarDetalle"); listo();
        assertEquals("2 - 1", fx(() -> UiIntegrationTest.<Label>control("lblMarcador").getText()));
        cerrarAlertas(); DatosDemo.FALLAR_SINCRONIZACION = false;

        pulsar("btnPosiciones"); listo();
        assertEquals(8, fx(() -> UiIntegrationTest.<TableView<?>>control("tblPosiciones").getItems().size()));
        fx(() -> {
            TableView<Posicion> tabla = control("tblPosiciones"); tabla.applyCss(); tabla.layout();
            TableRow<?> fila = tabla.lookupAll(".table-row-cell").stream()
                    .filter(n -> n instanceof TableRow<?> r && r.getItem() instanceof Posicion pos && pos.getPosicion() == 2)
                    .map(n -> (TableRow<?>) n).findFirst().orElseThrow();
            assertTrue(fila.getStyleClass().contains("zona-champions")); dobleClic(fila); return null;
        });
        listo();
        assertEquals(2, Servicios.favoritos().equiposFavoritos().size());
        esperar(() -> UiIntegrationTest.<ToggleButton>control("btnFavoritos").getText().contains("(4)"));
        fx(() -> { UiIntegrationTest.<TextField>control("txtBusqueda").setText("zz-no-existe"); return null; });
        assertEquals(0, fx(() -> UiIntegrationTest.<TableView<?>>control("tblPosiciones").getItems().size()));
        fx(() -> { UiIntegrationTest.<TextField>control("txtBusqueda").clear(); return null; });
        DatosDemo.FALLAR_SINCRONIZACION = true;
        pulsar("btnActualizar"); listo();
        assertEquals(8, fx(() -> UiIntegrationTest.<TableView<?>>control("tblPosiciones").getItems().size()));
        cerrarAlertas(); DatosDemo.FALLAR_SINCRONIZACION = false;
        pulsar("btnFavoritos"); listo(); pulsar("btnTipoEquipos"); listo();
        assertEquals(2, fx(() -> UiIntegrationTest.<ListView<?>>control("lstFavoritos").getItems().size()));
        fx(() -> {
            stage.setWidth(1400); stage.setHeight(850); root.applyCss(); root.layout();
            assertTrue(control("lstFavoritos").getBoundsInParent().getHeight() > 0);
            stage.setWidth(1100); stage.setHeight(680); return null;
        });
        pulsar("btnPartidos"); listo();
        fx(() -> {
            root.applyCss(); root.layout();
            Button estrella = (Button) root.lookupAll(".btn-estrella").stream()
                    .filter(n -> n instanceof Button button && button.getAccessibleText().equals("Marcar favorito: Arsenal"))
                    .findFirst().orElseThrow();
            assertTrue(estrella.getHeight() >= 44); estrella.fire(); return null;
        });
        listo();
        assertTrue(Servicios.favoritos().idsPartidosFavoritos().contains(3L));
        assertTrue(errores.isEmpty(), () -> "Errores del hilo JavaFX: " + errores);
    }

    @Test void ventanaIndependienteConTodosLosTitulares() throws Exception {
        var detalle=AlineacionTest.detalle(AlineacionTest.snapshot());
        fx(()->{
            Stage alineacion=ni.edu.uam.proyectofinalivsemestre.util.AlineacionWindow.crear(stage,detalle);
            try {
                alineacion.show(); Parent panel=alineacion.getScene().getRoot(); panel.applyCss(); panel.layout();
                assertNotSame(stage,alineacion); assertTrue(alineacion.isShowing());
                assertEquals(44,panel.lookupAll(".jugador-ficha").size(),"22 titulares y 22 suplentes");
                assertTrue(panel.lookupAll(".jugador-rating").stream().anyMatch(n->n instanceof Label l && l.getText().equals("7.84")));
                for(Node n:panel.lookupAll(".jugador-ficha")) assertTrue(n.getBoundsInLocal().getWidth()>60);
                if(Boolean.getBoolean("futbol.test.capture")) {
                    var imagen=panel.snapshot(null,null); int w=(int)imagen.getWidth(),h=(int)imagen.getHeight();
                    var ruta=java.nio.file.Path.of("target","alineacion.ppm");
                    try(var out=new java.io.BufferedOutputStream(java.nio.file.Files.newOutputStream(ruta))) {
                        out.write(("P6\n"+w+" "+h+"\n255\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                        var pixels=imagen.getPixelReader();
                        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {int rgb=pixels.getArgb(x,y);out.write((rgb>>16)&255);out.write((rgb>>8)&255);out.write(rgb&255);}
                    }
                }
                alineacion.close(); assertTrue(stage.isShowing(),"Cerrar el campo no cierra la aplicación");
            } finally { alineacion.close(); }
            return null;
        });
    }
}
