package ni.edu.uam.proyectofinalivsemestre.service;
import ni.edu.uam.proyectofinalivsemestre.service.stub.*;
// TEMPORAL: reemplazar por la versión del Integrante 1/2
public final class Servicios {
    // Sustituir únicamente estas instancias por los servicios reales de los Integrantes 1 y 2.
    // Sus consultas, sincronizaciones y transacciones deben completarse en el hilo llamante.
    private static final PartidoService PARTIDOS = new PartidoServiceDemo();
    private static final PosicionService POSICIONES = new PosicionServiceDemo();
    private static final FavoritoService FAVORITOS = new FavoritoServiceDemo();
    private Servicios() {}
    public static PartidoService partidos() { return PARTIDOS; }
    public static PosicionService posiciones() { return POSICIONES; }
    public static FavoritoService favoritos() { return FAVORITOS; }
}

