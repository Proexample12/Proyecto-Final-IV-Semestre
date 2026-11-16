package ni.edu.uam.proyectofinalivsemestre.model.dto;

import java.util.List;

/** Titulares en las filas originales de la formación; todos los IDs son de Highlightly. */
public record Alineacion(String equipo, String formacion, List<List<Jugador>> filas, List<Jugador> suplentes) {
    public record Jugador(Integer id, String nombre, Integer dorsal, Double puntuacion, Boolean capitan,
            int goles, int asistencias, int amarillas, int rojas, String salida, String entrada) {}
}
