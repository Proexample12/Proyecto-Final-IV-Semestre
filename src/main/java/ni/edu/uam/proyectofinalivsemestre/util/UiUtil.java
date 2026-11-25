package ni.edu.uam.proyectofinalivsemestre.util;

import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.control.*;
import ni.edu.uam.proyectofinalivsemestre.model.Partido;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class UiUtil {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "futbol-ui-worker"); t.setDaemon(true); return t;
    });
    private UiUtil() {}
    public static final javafx.beans.property.IntegerProperty revisionFavoritos = new javafx.beans.property.SimpleIntegerProperty();
    public static void favoritosCambiaron() { revisionFavoritos.set(revisionFavoritos.get() + 1); }
    public static String normalizar(String texto) {
        return java.text.Normalizer.normalize(texto == null ? "" : texto, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(java.util.Locale.ROOT).strip();
    }
    public static Node vacio(String icono, String titulo, String ayuda) {
        Label dibujo = new Label(icono); dibujo.getStyleClass().add("vacio-icono");
        Label nota = new Label(ayuda); nota.setWrapText(true); nota.setMaxWidth(240); nota.getStyleClass().add("nota");
        var panel = new javafx.scene.layout.VBox(10, dibujo, new Label(titulo), nota);
        panel.setAlignment(javafx.geometry.Pos.CENTER); return panel;
    }

    public static <T> void async(Callable<T> trabajo, Consumer<T> exito, Consumer<Throwable> error) {
        Task<T> task = new Task<>() { protected T call() throws Exception { return trabajo.call(); } };
        task.setOnSucceeded(e -> exito.accept(task.getValue()));
        task.setOnFailed(e -> error.accept(task.getException()));
        EXECUTOR.execute(task);
    }
    public static void ocupado(boolean valor, ProgressIndicator indicador, Node... controles) {
        indicador.setVisible(valor); indicador.setManaged(valor);
        for (Node control : controles) control.setDisable(valor);
    }
    public static void error(Throwable error) {
        alerta(Alert.AlertType.ERROR, "No se pudo completar la operación",
                error.getMessage() == null ? "Inténtalo nuevamente." : error.getMessage());
    }
    public static void aviso(String mensaje) { alerta(Alert.AlertType.WARNING, "Datos guardados", mensaje); }
    private static void alerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert a = new Alert(tipo, mensaje, ButtonType.OK);
        Button aceptar = (Button) a.getDialogPane().lookupButton(ButtonType.OK);
        aceptar.setText("✓"); aceptar.setAccessibleText("Aceptar"); aceptar.setTooltip(new Tooltip("Aceptar"));
        a.setTitle("Fútbol App"); a.setHeaderText(titulo); a.show();
    }
    public static String marcador(Partido p) {
        return p.getGolesLocal() == null || p.getGolesVisitante() == null ? "—" : p.getGolesLocal() + " - " + p.getGolesVisitante();
    }
    public static String estado(Partido p) {
        return switch (p.getEstado()) {
            case "EN_VIVO" -> "● EN VIVO" + (p.getMinuto() == null ? "" : " " + p.getMinuto() + "'");
            case "FINALIZADO" -> "✓ FINALIZADO";
            case "APLAZADO" -> "Aplazado";
            case "SUSPENDIDO" -> "Suspendido";
            case "CANCELADO" -> "Cancelado";
            default -> {
                var fecha = p.getFecha().toLocalDate(); var hoy = java.time.LocalDate.now();
                String dia = fecha.equals(hoy) ? "Hoy" : fecha.equals(hoy.plusDays(1)) ? "Mañana"
                        : fecha.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"));
                yield dia + " " + p.getFecha().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
            }
        };
    }
    public static String liga(String nombre) {
        return switch (nombre) { case "Champions League" -> "Champions"; case "Premier League" -> "Premier"; default -> nombre; };
    }
    public static void cambiarVista(Node origen, String fxmlName) {
        try {
            if (origen == null || origen.getScene() == null) return;
            Node contenidoNode = origen.getScene().lookup("#contenido");
            if (contenidoNode instanceof javafx.scene.layout.Pane pane) {
                javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(UiUtil.class.getResource("/views/" + fxmlName + ".fxml"));
                javafx.scene.Parent root = loader.load();
                pane.getChildren().setAll(root);
            }
        } catch (Exception e) {
            error(e);
        }
    }
    public static void cerrar() { EXECUTOR.shutdownNow(); }
}
