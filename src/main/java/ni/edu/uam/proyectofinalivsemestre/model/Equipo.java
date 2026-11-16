package ni.edu.uam.proyectofinalivsemestre.model;

// TEMPORAL: reemplazar por la versión del Integrante 1/2
public final class Equipo {
    private final Long id;
    private final String nombre;

    private final String crestUrl;
    public Equipo(Long id, String nombre) { this(id, nombre, null); }
    public Equipo(Long id, String nombre, String crestUrl) {
        this.crestUrl = crestUrl;
        this.id = id;
        this.nombre = nombre;
    }
    public String getCrestUrl() { return crestUrl; }
    public Long getId() { return id; }
    public String getNombre() { return nombre; }
}
