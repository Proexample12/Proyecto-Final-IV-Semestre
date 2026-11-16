package ni.edu.uam.proyectofinalivsemestre.util;

import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.canvas.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.stage.*;
import ni.edu.uam.proyectofinalivsemestre.model.dto.*;
import java.util.*;

/** Ventana independiente; el dibujo se genera con JavaFX y no descarga imágenes. */
public final class AlineacionWindow {
    private AlineacionWindow() {}
    public static Stage crear(Window owner, PartidoDetalle detalle) {
        Stage stage=new Stage(); if(owner!=null) stage.initOwner(owner);
        stage.setTitle("Alineaciones · "+detalle.partido().getLocal().getNombre()+" — "+detalle.partido().getVisitante().getNombre());
        BorderPane root=new BorderPane(); root.getStyleClass().add("alineacion-ventana");
        Label titulo=new Label("ALINEACIONES"); titulo.getStyleClass().add("alineacion-titulo");
        Label partido=new Label(detalle.partido().getLocal().getNombre()+"  "+UiUtil.marcador(detalle.partido())+"  "+detalle.partido().getVisitante().getNombre());
        partido.setWrapText(true); partido.getStyleClass().add("alineacion-subtitulo");
        VBox encabezado=new VBox(5,titulo,partido); HBox.setHgrow(encabezado,Priority.ALWAYS);
        Button maximizar=boton("⛶","Maximizar o restaurar"); maximizar.setOnAction(e->stage.setMaximized(!stage.isMaximized()));
        Button cerrar=boton("✕","Cerrar alineación"); cerrar.setOnAction(e->stage.close());
        HBox barra=new HBox(14,encabezado,maximizar,cerrar); barra.setAlignment(Pos.CENTER_LEFT); barra.setPadding(new Insets(18,24,14,24)); root.setTop(barra);
        HBox equipos=new HBox(18); equipos.setPadding(new Insets(0,24,18,24)); equipos.setAlignment(Pos.TOP_CENTER);
        for(int i=0;i<detalle.formaciones().size();i++) {
            VBox equipo=equipo(detalle.formaciones().get(i),i==0); equipos.getChildren().add(equipo); HBox.setHgrow(equipo,Priority.ALWAYS);
        }
        if(equipos.getChildren().isEmpty()) {
            Label vacio=new Label("El proveedor todavía no publica las alineaciones de este partido.");
            vacio.setWrapText(true); vacio.getStyleClass().add("alineacion-subtitulo"); equipos.getChildren().add(vacio);
        }
        ScrollPane scroll=new ScrollPane(equipos); scroll.setFitToWidth(true); scroll.setPannable(true); root.setCenter(scroll);
        FlowPane leyenda=new FlowPane(18,8); leyenda.setPadding(new Insets(12,24,16,24));
        leyenda.getChildren().addAll(leyenda("⚽","Gol"),leyendaBota(),leyendaTarjeta("#f4c542","Amarilla"),leyendaTarjeta("#ef5350","Roja"),
                leyenda("↘","Sale"),leyenda("↗","Entra"),leyenda("|C|","Capitán"),leyenda("—","Sin puntuación"));
        root.setBottom(leyenda);
        Scene scene=new Scene(root,1320,880); scene.getStylesheets().add(Objects.requireNonNull(AlineacionWindow.class.getResource("/css/styles.css")).toExternalForm());
        scene.setOnKeyPressed(e->{if(e.getCode()==javafx.scene.input.KeyCode.ESCAPE) stage.close();});
        stage.setScene(scene); stage.setMinWidth(980); stage.setMinHeight(640); return stage;
    }
    private static Button boton(String icono,String ayuda) {
        Button b=new Button(icono); b.setAccessibleText(ayuda); b.setTooltip(new Tooltip(ayuda)); b.getStyleClass().add("alineacion-boton"); return b;
    }
    private static Node leyenda(String icono,String texto) {
        Label l=new Label(icono+"  "+texto); l.getStyleClass().add("alineacion-leyenda"); return l;
    }
    private static Node leyendaBota() { Label l=new Label("Asistencia",bota()); l.getStyleClass().add("alineacion-leyenda"); return l; }
    private static Node leyendaTarjeta(String color,String texto) { Label l=new Label(texto,tarjeta(color)); l.getStyleClass().add("alineacion-leyenda"); return l; }
    private static Node bota() {
        SVGPath p=new SVGPath(); p.setContent("M1 3 L8 3 L10 9 L16 12 L23 13 L23 18 L2 18 Z M4 18 L4 21 L7 21 L7 18 M17 18 L17 21 L20 21 L20 18");
        p.setFill(Color.web("#f8f0d5")); p.setStroke(Color.web("#b39c67")); p.setStrokeWidth(.8); return p;
    }
    private static Node tarjeta(String color) { Rectangle r=new Rectangle(11,17,Color.web(color)); r.setArcWidth(3); r.setArcHeight(3); return r; }
    private static VBox equipo(Alineacion alineacion,boolean local) {
        Label nombre=new Label(alineacion.equipo()); nombre.setWrapText(true); nombre.getStyleClass().add("alineacion-equipo");
        Label formacion=new Label(alineacion.formacion()); formacion.getStyleClass().add("alineacion-formacion");
        HBox cabecera=new HBox(12,nombre,formacion); cabecera.setAlignment(Pos.CENTER); HBox.setHgrow(nombre,Priority.ALWAYS);
        VBox filas=new VBox(); filas.setPadding(new Insets(26,10,24,10)); filas.setSpacing(14);
        for(var linea:alineacion.filas()) {
            HBox fila=new HBox(6); fila.setAlignment(Pos.CENTER);
            for(var jugador:linea) {
                VBox ficha=ficha(jugador,local); ficha.setMaxWidth(150);
                ficha.prefWidthProperty().bind(javafx.beans.binding.Bindings.min(150,filas.widthProperty().subtract(26+(linea.size()-1)*6).divide(linea.size())));
                fila.getChildren().add(ficha); HBox.setHgrow(ficha,Priority.ALWAYS);
            }
            filas.getChildren().add(fila);
        }
        if(alineacion.filas().isEmpty()) {
            Label l=new Label("Titulares no publicados"); l.getStyleClass().add("alineacion-leyenda"); filas.getChildren().add(l);
        }
        Canvas canvas=new Canvas(); canvas.setManaged(false); canvas.setMouseTransparent(true); StackPane campo=new StackPane(canvas,filas); campo.getStyleClass().add("campo");
        canvas.widthProperty().bind(campo.widthProperty()); canvas.heightProperty().bind(campo.heightProperty());
        canvas.widthProperty().addListener((o,a,n)->dibujar(canvas)); canvas.heightProperty().addListener((o,a,n)->dibujar(canvas));
        Label banco=new Label("SUPLENTES"); banco.getStyleClass().add("alineacion-formacion");
        FlowPane suplentes=new FlowPane(8,8); suplentes.setPrefWrapLength(540);
        for(var j:alineacion.suplentes()) { VBox f=ficha(j,local); f.setPrefWidth(142); suplentes.getChildren().add(f); }
        VBox panel=new VBox(12,cabecera,campo,banco,suplentes); panel.setMinWidth(440); panel.setPrefWidth(620); panel.setMaxWidth(Double.MAX_VALUE); return panel;
    }
    private static VBox ficha(Alineacion.Jugador j,boolean local) {
        Label dorsal=new Label(j.dorsal()==null ? "—" : j.dorsal().toString()); dorsal.getStyleClass().addAll("jugador-dorsal",local ? "dorsal-local" : "dorsal-visita");
        Label rating=new Label(j.puntuacion()==null ? "—" : String.format(Locale.ROOT,"%.2f",j.puntuacion()));
        rating.getStyleClass().add("jugador-rating"); if(j.puntuacion()==null) rating.getStyleClass().add("rating-ausente");
        Tooltip.install(rating,new Tooltip(j.puntuacion()==null ? "Puntuación no publicada" : "Puntuación del partido: "+rating.getText()));
        HBox arriba=new HBox(6,dorsal,rating); arriba.setAlignment(Pos.CENTER);
        if(Boolean.TRUE.equals(j.capitan())) { Label c=new Label("|C|"); c.getStyleClass().add("jugador-capitan"); Tooltip.install(c,new Tooltip("Capitán")); arriba.getChildren().add(c); }
        Label nombre=new Label(j.nombre()); nombre.setWrapText(true); nombre.setAlignment(Pos.CENTER); nombre.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        nombre.setMaxWidth(Double.MAX_VALUE); nombre.setMinHeight(36); nombre.getStyleClass().add("jugador-nombre");
        FlowPane eventos=new FlowPane(5,3); eventos.setAlignment(Pos.CENTER);
        if(j.goles()>0) eventos.getChildren().add(insignia(new Label("⚽"),j.goles(),"Goles: "+j.goles()));
        if(j.asistencias()>0) eventos.getChildren().add(insignia(bota(),j.asistencias(),"Asistencias: "+j.asistencias()));
        if(j.amarillas()>0) eventos.getChildren().add(insignia(tarjeta("#f4c542"),j.amarillas(),"Amarillas: "+j.amarillas()));
        if(j.rojas()>0) eventos.getChildren().add(insignia(tarjeta("#ef5350"),j.rojas(),"Expulsiones: "+j.rojas()));
        if(j.salida()!=null) eventos.getChildren().add(cambio("↘",j.salida(),"Sustituido en el minuto ","cambio-sale"));
        if(j.entrada()!=null) eventos.getChildren().add(cambio("↗",j.entrada(),"Entró en el minuto ","cambio-entra"));
        eventos.setMinHeight(22);
        VBox card=new VBox(4,arriba,nombre,eventos); card.setAlignment(Pos.TOP_CENTER); card.setMinWidth(0); card.setPadding(new Insets(7,3,5,3)); card.getStyleClass().add("jugador-ficha");
        card.setAccessibleText(j.nombre()+", dorsal "+j.dorsal()+", puntuación "+rating.getText()+(Boolean.TRUE.equals(j.capitan()) ? ", capitán" : ""));
        Tooltip.install(card,new Tooltip(j.nombre())); return card;
    }
    private static Node insignia(Node icono,int cantidad,String ayuda) {
        Label l=new Label(cantidad>1 ? Integer.toString(cantidad) : "",icono); l.setGraphicTextGap(2); l.getStyleClass().add("jugador-insignia");
        l.setAccessibleText(ayuda); Tooltip.install(l,new Tooltip(ayuda)); return l;
    }
    private static Node cambio(String icono,String minuto,String texto,String estilo) {
        Label l=new Label(icono+" "+minuto); l.getStyleClass().addAll("jugador-insignia",estilo); Tooltip.install(l,new Tooltip(texto+minuto)); return l;
    }
    private static void dibujar(Canvas canvas) {
        double w=canvas.getWidth(),h=canvas.getHeight(); if(w<=0 || h<=0) return;
        GraphicsContext g=canvas.getGraphicsContext2D();
        for(int i=0;i<10;i++) { g.setFill(Color.web(i%2==0 ? "#176346" : "#1b6c4c")); g.fillRect(0,i*h/10,w,h/10+1); }
        g.setStroke(Color.web("#bce5ce",.48)); g.setLineWidth(1.5); g.strokeRect(12,12,w-24,h-24);
        g.strokeLine(12,h/2,w-12,h/2); g.strokeOval(w/2-48,h/2-48,96,96);
        g.strokeRect(w*.22,12,w*.56,h*.14); g.strokeRect(w*.22,h-h*.14-12,w*.56,h*.14);
        g.strokeRect(w*.36,12,w*.28,h*.055); g.strokeRect(w*.36,h-h*.055-12,w*.28,h*.055);
        g.setFill(Color.web("#bce5ce",.6)); g.fillOval(w/2-3,h/2-3,6,6);
    }
}
