package ni.edu.uam.proyectofinalivsemestre.util;

import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import ni.edu.uam.proyectofinalivsemestre.model.Equipo;
import java.util.*;

/** Imágenes asíncronas; cada celda tiene su nodo y comparte solo la imagen. */
public final class Escudos {
    private static final Map<String, Image> CACHE = new LinkedHashMap<>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Image> e) { return size() > 256; }
    };
    private Escudos() {}
    public static Label equipo(Equipo equipo) {
        SVGPath escudo = new SVGPath();
        escudo.setContent("M2 2 L12 0 L22 2 L21 14 Q18 21 12 24 Q6 21 3 14 Z");
        escudo.setStyle("-fx-fill: #8395a7;");
        StackPane icono = new StackPane(escudo); icono.setMinSize(24,24); icono.setMaxSize(24,24);
        String url = equipo.getCrestUrl();
        if (url != null && url.startsWith("https://")) {
            try {
                Image imagen = CACHE.computeIfAbsent(url, u -> new Image(u,24,24,true,true,true));
                ImageView vista = new ImageView(imagen); vista.setFitWidth(24); vista.setFitHeight(24); vista.setPreserveRatio(true);
                vista.visibleProperty().bind(imagen.errorProperty().not().and(imagen.progressProperty().isEqualTo(1)));
                escudo.visibleProperty().bind(vista.visibleProperty().not()); icono.getChildren().add(vista);
            } catch (IllegalArgumentException ignored) { /* Escudo local si la URL no es válida. */ }
        }
        Label nombre = new Label(equipo.getNombre(), icono); nombre.setGraphicTextGap(8); nombre.setWrapText(true); nombre.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        nombre.setTooltip(new Tooltip(equipo.getNombre())); return nombre;
    }
    public static <S,T> void columna(TableColumn<S,T> columna, java.util.function.Function<S,Equipo> equipo) {
        columna.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty); setText(null); setGraphic(null);
                if (!empty && getTableRow() != null && getTableRow().getItem() != null) {
                    Label nombre = Escudos.equipo(equipo.apply(getTableRow().getItem()));
                    nombre.prefWidthProperty().bind(widthProperty().subtract(20));
                    nombre.maxWidthProperty().bind(widthProperty().subtract(20));
                    setGraphic(nombre);
                }
            }
        });
    }
}
