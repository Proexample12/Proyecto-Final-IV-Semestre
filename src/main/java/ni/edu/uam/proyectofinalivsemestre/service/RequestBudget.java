package ni.edu.uam.proyectofinalivsemestre.service;

import java.io.IOException;
import java.time.*;
import java.util.ArrayDeque;

/** Ventana móvil compartida por proveedor; no bloquea hilos esperando cuota. */
public final class RequestBudget {
    private final Clock clock;
    private final ArrayDeque<Instant> solicitudes = new ArrayDeque<>();
    public RequestBudget() { this(Clock.systemUTC()); }
    public RequestBudget(Clock clock) { this.clock = clock; }
    public synchronized void acquire() throws IOException {
        Instant ahora = clock.instant();
        while (!solicitudes.isEmpty() && !solicitudes.peekFirst().plusSeconds(60).isAfter(ahora)) solicitudes.removeFirst();
        if (solicitudes.size() >= 10) throw new IOException("Límite local de consultas: espera un minuto. Los datos guardados siguen disponibles.");
        solicitudes.addLast(ahora);
    }
}
