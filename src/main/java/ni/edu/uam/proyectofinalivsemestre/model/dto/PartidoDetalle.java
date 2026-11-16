package ni.edu.uam.proyectofinalivsemestre.model.dto;
import ni.edu.uam.proyectofinalivsemestre.model.Partido;
import java.time.LocalDateTime;
import java.util.List;

public record PartidoDetalle(Partido partido, String estadio, List<String> eventos,
        Integer posesionLocal, Integer tirosLocal, Integer tirosVisitante,
        Integer tirosPuertaLocal, Integer tirosPuertaVisitante, Integer posesionVisitante,
        List<Estadistica> estadisticas, List<String> alineaciones, String fuente,
        LocalDateTime actualizado, String aviso, List<Alineacion> formaciones) {
    public record Estadistica(String nombre, String local, String visitante) {}
    public PartidoDetalle(Partido partido, String estadio, List<String> eventos,
            Integer posesionLocal, Integer tirosLocal, Integer tirosVisitante,
            Integer tirosPuertaLocal, Integer tirosPuertaVisitante) {
        this(partido,estadio,eventos,posesionLocal,tirosLocal,tirosVisitante,tirosPuertaLocal,tirosPuertaVisitante,
                posesionLocal == null ? null : 100 - posesionLocal, List.of(),List.of(),"Datos guardados",null,null,List.of());
    }
}
