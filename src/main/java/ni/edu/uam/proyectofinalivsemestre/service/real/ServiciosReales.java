package ni.edu.uam.proyectofinalivsemestre.service.real;

import com.google.gson.*;
import jakarta.persistence.EntityManager;
import ni.edu.uam.proyectofinalivsemestre.config.HibernateUtil;
import ni.edu.uam.proyectofinalivsemestre.model.*;
import ni.edu.uam.proyectofinalivsemestre.model.dto.PartidoDetalle;
import ni.edu.uam.proyectofinalivsemestre.service.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Adaptador API -> transacción Hibernate -> modelos de lectura de la interfaz. */
public final class ServiciosReales implements PartidoService, PosicionService, FavoritoService {
    private final FootballApiService api;
    private final ni.edu.uam.proyectofinalivsemestre.service.highlightly.HighlightlyService highlightly;
    private final ni.edu.uam.proyectofinalivsemestre.service.apifootball.ApiFootballService secundaria = new ni.edu.uam.proyectofinalivsemestre.service.apifootball.ApiFootballService();
    public ServiciosReales() { this(new FootballApiService(false)); }
    public ServiciosReales(FootballApiService api) { this(api, new ni.edu.uam.proyectofinalivsemestre.service.highlightly.HighlightlyService()); }
    public ServiciosReales(FootballApiService api, ni.edu.uam.proyectofinalivsemestre.service.highlightly.HighlightlyService highlightly) {
        this.api = api; this.highlightly = highlightly;
    }
    public static String codigo(String liga) {
        return switch (liga) {
            case "LaLiga" -> "PD"; case "Premier League" -> "PL"; case "Champions League" -> "CL";
            default -> throw new IllegalArgumentException("Liga no reconocida.");
        };
    }
    public static String nombre(String codigo) {
        return switch (codigo) { case "PD" -> "LaLiga"; case "PL" -> "Premier League"; case "CL" -> "Champions League"; default -> codigo; };
    }
    public static int temporada(LocalDate fecha) { return fecha.getMonthValue() >= 7 ? fecha.getYear() : fecha.getYear() - 1; }
    private static <T> T leer(Function<EntityManager,T> trabajo) {
        try (EntityManager em = HibernateUtil.getEntityManager()) { return trabajo.apply(em); }
    }
    private static <T> T escribir(Function<EntityManager,T> trabajo) {
        return ni.edu.uam.proyectofinalivsemestre.dao.GenericDao.executeInTransaction(trabajo);
    }
    private final Map<String, Instant> sincronizaciones = new HashMap<>();
    private boolean fresca(String clave) {
        Instant ultima = sincronizaciones.get(clave);
        return ultima != null && Instant.now().isBefore(ultima.plusSeconds(90));
    }
    private static JsonObject objeto(JsonObject json, String campo) {
        return json != null && json.has(campo) && json.get(campo).isJsonObject() ? json.getAsJsonObject(campo) : new JsonObject();
    }
    private static String texto(JsonObject json, String campo, String defecto) {
        return json.has(campo) && !json.get(campo).isJsonNull() ? json.get(campo).getAsString() : defecto;
    }
    private static Integer numero(JsonObject json, String campo) {
        return json.has(campo) && !json.get(campo).isJsonNull() ? json.get(campo).getAsInt() : null;
    }
    private static int requerido(JsonObject json, String campo) {
        Integer n = numero(json, campo);
        if (n == null) throw new IllegalArgumentException("La API no entregó el campo " + campo + ".");
        return n;
    }
    private static JsonArray lista(JsonObject json, String campo) {
        return json.has(campo) && json.get(campo).isJsonArray() ? json.getAsJsonArray(campo) : new JsonArray();
    }
    private static JsonObject respuesta(String json) {
        try { return JsonParser.parseString(json).getAsJsonObject(); }
        catch (RuntimeException e) { throw new IllegalArgumentException("La API devolvió datos no válidos."); }
    }
    private static void snapshot(EntityManager em, String id, JsonObject json) {
        em.merge(new ApiSnapshot(id, json.toString()));
    }
    private static Competition competicion(EntityManager em, JsonObject json) {
        int id = requerido(json, "id");
        Competition c = em.find(Competition.class, id);
        boolean nuevo = c == null;
        if (nuevo) { c = new Competition(); c.setId(id); }
        c.setCode(texto(json, "code", "")); c.setName(nombre(c.getCode()));
        c.setEmblemUrl(texto(json, "emblem", null));
        if (nuevo) em.persist(c);
        return c;
    }
    private static Team equipo(EntityManager em, JsonObject json, Competition c) {
        int id = requerido(json, "id");
        Team t = em.find(Team.class, id);
        boolean nuevo = t == null;
        if (nuevo) { t = new Team(); t.setId(id); t.setCompetition(c); }
        t.setName(texto(json, "name", "Equipo"));
        t.setShortName(texto(json, "shortName", t.getName())); t.setTla(texto(json, "tla", null));
        t.setCrestUrl(texto(json, "crest", null)); t.setUpdatedAt(LocalDateTime.now());
        // Un equipo participa en liga y Champions; no sobrescribir su liga doméstica.
        if (!"CL".equals(c.getCode())) t.setCompetition(c);
        if (nuevo) em.persist(t);
        return t;
    }
    private static void guardarPartido(EntityManager em, JsonObject json, JsonObject fallback) {
        JsonObject liga = objeto(json, "competition");
        if (!liga.has("id")) liga = fallback;
        Competition c = competicion(em, liga);
        Team local = equipo(em, objeto(json, "homeTeam"), c), visitante = equipo(em, objeto(json, "awayTeam"), c);
        int id = requerido(json, "id");
        Match m = em.find(Match.class, id);
        boolean nuevo = m == null;
        if (nuevo) { m = new Match(); m.setId(id); }
        m.setCompetition(c); m.setHomeTeam(local); m.setAwayTeam(visitante);
        m.setUtcDate(Instant.parse(texto(json, "utcDate", "")).atOffset(ZoneOffset.UTC).toLocalDateTime());
        m.setStatus(texto(json, "status", "SCHEDULED")); m.setMatchday(numero(json, "matchday"));
        JsonObject score = objeto(objeto(json, "score"), "fullTime");
        m.setHomeScore(numero(score, "home")); m.setAwayScore(numero(score, "away")); m.setUpdatedAt(LocalDateTime.now());
        if (nuevo) em.persist(m);
        snapshot(em, "match/" + id, json);
    }
    private static Equipo equipoUi(Team t) {
        return new Equipo((long)t.getId(), t.getName() == null || t.getName().isBlank() ? t.getShortName() : t.getName(), t.getCrestUrl());
    }
    public static String estadoUi(String status) {
        return switch (status) {
            case "IN_PLAY", "PAUSED", "EXTRA_TIME", "PENALTY_SHOOTOUT" -> "EN_VIVO";
            case "FINISHED", "AWARDED" -> "FINALIZADO";
            case "POSTPONED" -> "APLAZADO"; case "SUSPENDED" -> "SUSPENDIDO"; case "CANCELLED" -> "CANCELADO";
            default -> "PROGRAMADO";
        };
    }
    private static Partido partidoUi(Match m) {
        return new Partido((long)m.getId(), m.getUtcDate().atOffset(ZoneOffset.UTC).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime(),
                new Liga(nombre(m.getCompetition().getCode())), equipoUi(m.getHomeTeam()), equipoUi(m.getAwayTeam()),
                m.getHomeScore(), m.getAwayScore(), estadoUi(m.getStatus()), null);
    }
    @Override public List<Partido> buscar(String liga, LocalDate fecha) {
        return leer(em -> {
            var q = em.createQuery("select m from Match m join fetch m.competition join fetch m.homeTeam join fetch m.awayTeam "
                    + "where m.utcDate >= :desde and m.utcDate < :hasta "
                    + (liga == null ? "" : "and m.competition.code = :liga ") + "order by m.utcDate", Match.class);
            ZoneId zona = ZoneId.systemDefault();
            q.setParameter("desde", fecha.atStartOfDay(zona).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());
            q.setParameter("hasta", fecha.plusDays(1).atStartOfDay(zona).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());
            if (liga != null) q.setParameter("liga", codigo(liga));
            // El esquema original incluía ejemplos: solo mostrar partidos confirmados por la API.
            return q.getResultList().stream().filter(m -> em.find(ApiSnapshot.class, "match/" + m.getId()) != null)
                    .map(ServiciosReales::partidoUi).toList();
        });
    }
    @Override public synchronized void sincronizar(String liga, LocalDate fecha) throws Exception {
        Map<String, JsonObject> respuestas = new LinkedHashMap<>();
        for (String code : liga == null ? List.of("PD","PL","CL") : List.of(codigo(liga))) {
            String clave = "matches/" + code + "/" + temporada(fecha);
            if (fresca(clave)) continue;
            JsonObject json = respuesta(api.consultarRecurso("competitions/" + code + "/matches?season=" + temporada(fecha)));
            if (!json.has("matches")) throw new IllegalArgumentException("La respuesta no contiene partidos.");
            respuestas.put(clave, json);
        }
        // Todas las respuestas llegan antes de abrir la transacción: un fallo no borra la caché.
        escribir(em -> {
            for (JsonObject json : respuestas.values()) for (JsonElement match : lista(json,"matches"))
                guardarPartido(em, match.getAsJsonObject(), objeto(json,"competition"));
            return null;
        });
        respuestas.keySet().forEach(k -> sincronizaciones.put(k, Instant.now()));
    }
    @Override public synchronized void actualizarDetalle(Long id) throws Exception {
        List<String> avisos = new ArrayList<>();
        if (!fresca("detail/" + id)) {
            try {
                JsonObject json = respuesta(api.consultarRecurso("matches/" + id));
                escribir(em -> { guardarPartido(em, json, new JsonObject()); snapshot(em, "detail/" + id, json); return null; });
                sincronizaciones.put("detail/" + id, Instant.now());
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw e; }
            catch (Exception e) { avisos.add("Resultado principal sin actualizar: " + e.getMessage()); }
        }
        try {
            if (highlightly.configurado()) actualizarHighlightly(id);
            else actualizarComplemento(id);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw e; }
        catch (Exception e) { avisos.add(e.getMessage()); }
        if (!avisos.isEmpty()) throw new java.io.IOException(String.join("\n",avisos));
    }
    private void actualizarHighlightly(Long id) throws Exception {
        String clave = "highlightly/" + id;
        JsonObject anterior = leer(em -> {
            ApiSnapshot s = em.find(ApiSnapshot.class,clave);
            return s == null ? new JsonObject() : respuesta(s.getPayload());
        });
        boolean vigente = leer(em -> {
            ApiSnapshot s = em.find(ApiSnapshot.class,clave);
            return s != null && !anterior.has("warning") && anterior.has("boxScore") && s.getUpdatedAt().plusSeconds(90).isAfter(LocalDateTime.now());
        });
        if (vigente) return;
        JsonObject principal = leer(em -> {
            ApiSnapshot s = em.find(ApiSnapshot.class,"detail/" + id);
            if (s == null) s = em.find(ApiSnapshot.class,"match/" + id);
            if (s == null) throw new IllegalStateException("Actualiza la lista de partidos antes de abrir el detalle.");
            return respuesta(s.getPayload());
        });
        JsonObject conocido = objeto(anterior,"match");
        // La fecha o los equipos pueden cambiar tras un aplazamiento: revalidar la asociación.
        JsonObject candidato = ni.edu.uam.proyectofinalivsemestre.service.highlightly.HighlightlyService.coincide(principal,conocido)
                ? conocido : highlightly.localizar(principal);
        long highlightlyId = candidato.get("id").getAsLong();
        JsonObject match = highlightly.partido(highlightlyId);
        if (!ni.edu.uam.proyectofinalivsemestre.service.highlightly.HighlightlyService.coincide(principal,match))
            throw new java.io.IOException("El detalle de Highlightly no coincide en liga, fecha y equipos.");
        JsonObject nuevo = new JsonObject(); nuevo.add("match",match);
        String aviso = null;
        try { nuevo.add("lineups",highlightly.alineaciones(highlightlyId,match)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw e; }
        catch (Exception e) {
            // Un fallo en alineaciones no descarta los eventos ni estadísticas descargados.
            if (objeto(anterior,"match").has("id") && anterior.getAsJsonObject("match").get("id").getAsLong() == highlightlyId
                    && anterior.has("lineups")) nuevo.add("lineups",anterior.get("lineups"));
            aviso = "Alineaciones sin actualizar: " + e.getMessage(); nuevo.addProperty("warning",aviso);
        }
        try { nuevo.add("boxScore",highlightly.puntuaciones(highlightlyId,match)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw e; }
        catch (Exception e) {
            if (objeto(anterior,"match").has("id") && anterior.getAsJsonObject("match").get("id").getAsLong() == highlightlyId
                    && anterior.has("boxScore")) nuevo.add("boxScore",anterior.get("boxScore"));
            aviso = (aviso == null ? "" : aviso + "\n") + "Puntuaciones sin actualizar: " + e.getMessage();
            nuevo.addProperty("warning",aviso);
        }
        escribir(em -> { snapshot(em,clave,nuevo); return null; });
        if (aviso != null) throw new java.io.IOException(aviso);
    }
    /** Nunca cruza los IDs de proveedores: verifica fecha, liga y ambos equipos. */
    private void actualizarComplemento(Long id) throws Exception {
        if (ni.edu.uam.proyectofinalivsemestre.config.LocalConfig.valor("API_FOOTBALL_KEY", "").isBlank()
                || fresca("apisports/" + id)) return;
        JsonObject principal = leer(em -> respuesta(em.find(ApiSnapshot.class, "detail/" + id).getPayload()));
        String codigo = texto(objeto(principal,"competition"),"code","");
        int liga = switch (codigo) { case "PD" -> 140; case "PL" -> 39; case "CL" -> 2; default -> 0; };
        if (liga == 0) return;
        Instant inicio = Instant.parse(texto(principal,"utcDate",""));
        LocalDate fecha = inicio.atOffset(ZoneOffset.UTC).toLocalDate();
        JsonObject lista = respuesta(secundaria.consultarRecurso("fixtures?league=" + liga + "&season="
                + temporada(fecha)));
        List<JsonObject> candidatos = new ArrayList<>();
        for (JsonElement e : lista(lista,"response")) {
            JsonObject fixture = e.getAsJsonObject();
            String hora = texto(objeto(fixture,"fixture"),"date","");
            if (hora.isBlank() || !OffsetDateTime.parse(hora).toInstant().equals(inicio)) continue;
            JsonObject equipos = objeto(fixture,"teams");
            if (mismoEquipo(objeto(principal,"homeTeam"), objeto(equipos,"home"))
                    && mismoEquipo(objeto(principal,"awayTeam"), objeto(equipos,"away"))) candidatos.add(fixture);
        }
        if (candidatos.size() != 1) {
            sincronizaciones.put("apisports/" + id, Instant.now());
            return; // Sin coincidencia inequívoca, conservar el detalle de football-data.
        }
        int fixtureId = requerido(objeto(candidatos.getFirst(),"fixture"),"id");
        JsonArray detalles = lista(respuesta(secundaria.consultarRecurso("fixtures?id=" + fixtureId)),"response");
        if (detalles.size() != 1) throw new IllegalStateException("API-Football no devolvió el detalle solicitado.");
        JsonObject complemento = detalles.get(0).getAsJsonObject();
        if (requerido(objeto(complemento,"fixture"),"id") != fixtureId)
            throw new IllegalStateException("El detalle de API-Football no corresponde al partido.");
        escribir(em -> { snapshot(em,"apisports/" + id,complemento); return null; });
        sincronizaciones.put("apisports/" + id, Instant.now());
    }
    private static String nombreComparable(String nombre) {
        return ni.edu.uam.proyectofinalivsemestre.service.highlightly.HighlightlyService.nombre(nombre);
    }
    static boolean mismoEquipo(JsonObject principal, JsonObject secundario) {
        String otro = nombreComparable(texto(secundario,"name",""));
        return !otro.isEmpty() && (otro.equals(nombreComparable(texto(principal,"name","")))
                || otro.equals(nombreComparable(texto(principal,"shortName",""))));
    }
    private static Integer estadistica(JsonObject complemento, int equipoId, String tipo) {
        for (JsonElement e : lista(complemento,"statistics")) {
            JsonObject fila = e.getAsJsonObject();
            if (!Objects.equals(numero(objeto(fila,"team"),"id"), equipoId)) continue;
            for (JsonElement v : lista(fila,"statistics")) {
                JsonObject dato = v.getAsJsonObject();
                if (tipo.equals(texto(dato,"type",""))) {
                    try { return Integer.valueOf(texto(dato,"value","").replace("%", "")); }
                    catch (NumberFormatException ignored) { return null; }
                }
            }
        }
        return null;
    }
    @Override public PartidoDetalle detalle(Long id) {
        return leer(em -> {
            Match m = em.find(Match.class, Math.toIntExact(id));
            if (m == null) throw new IllegalArgumentException("El partido no está guardado. Actualiza la lista.");
            ApiSnapshot s = em.find(ApiSnapshot.class, "detail/" + id);
            if (s == null) s = em.find(ApiSnapshot.class, "match/" + id);
            JsonObject json = s == null ? new JsonObject() : respuesta(s.getPayload());
            JsonObject homeStats = objeto(objeto(json,"homeTeam"),"statistics");
            JsonObject awayStats = objeto(objeto(json,"awayTeam"),"statistics");
            List<String> eventos = new ArrayList<>();
            for (JsonElement e : lista(json,"goals")) {
                JsonObject gol = e.getAsJsonObject();
                eventos.add(texto(gol,"minute","?") + "' · Gol · " + texto(objeto(gol,"team"),"name",""));
            }
            for (JsonElement e : lista(json,"bookings")) {
                JsonObject tarjeta = e.getAsJsonObject();
                eventos.add(texto(tarjeta,"minute","?") + "' · Tarjeta · " + texto(objeto(tarjeta,"team"),"name",""));
            }
            ApiSnapshot extra = em.find(ApiSnapshot.class,"apisports/" + id);
            JsonObject complemento = extra == null ? new JsonObject() : respuesta(extra.getPayload());
            JsonArray eventosExtra = lista(complemento,"events");
            if (!eventosExtra.isEmpty()) {
                eventos.clear();
                for (JsonElement e : eventosExtra) {
                    JsonObject evento = e.getAsJsonObject();
                    eventos.add(texto(objeto(evento,"time"),"elapsed","?") + "' · "
                            + texto(evento,"type","") + " · " + texto(objeto(evento,"player"),"name","")
                            + " · " + texto(evento,"detail",""));
                }
            }
            JsonObject equiposExtra = objeto(complemento,"teams");
            Integer localId = numero(objeto(equiposExtra,"home"),"id"), visitaId = numero(objeto(equiposExtra,"away"),"id");
            if (localId != null && visitaId != null) {
                for (var par : Map.of("Ball Possession","ball_possession", "Total Shots","shots", "Shots on Goal","shots_on_goal").entrySet()) {
                    Integer local = estadistica(complemento,localId,par.getKey()), visita = estadistica(complemento,visitaId,par.getKey());
                    if (local != null) homeStats.addProperty(par.getValue(),local);
                    if (visita != null) awayStats.addProperty(par.getValue(),visita);
                }
            }
            if (eventos.isEmpty()) eventos.add("La API no proporciona eventos para este partido.");
            Partido p = partidoUi(m);
            p = new Partido(p.getId(),p.getFecha(),p.getLiga(),p.getLocal(),p.getVisitante(),
                    p.getGolesLocal(),p.getGolesVisitante(),p.getEstado(),numero(json,"minute"));
            PartidoDetalle base = new PartidoDetalle(p, texto(json,"venue","No disponible"), eventos,
                    numero(homeStats,"ball_possession"), numero(homeStats,"shots"), numero(awayStats,"shots"),
                    numero(homeStats,"shots_on_goal"), numero(awayStats,"shots_on_goal"));
            ApiSnapshot h = em.find(ApiSnapshot.class,"highlightly/" + id);
            if (h == null) return base;
            JsonObject guardado = respuesta(h.getPayload());
            if (!ni.edu.uam.proyectofinalivsemestre.service.highlightly.HighlightlyService.coincide(json,objeto(guardado,"match"))) return base;
            return ni.edu.uam.proyectofinalivsemestre.service.highlightly.HighlightlyMapper.combinar(base,guardado,h.getUpdatedAt());
        });
    }
    private static String claveTabla(String liga, String temporada) { return "standings/" + codigo(liga) + "/" + temporada; }
    private static List<Posicion> posiciones(JsonObject json) {
        List<Posicion> filas = new ArrayList<>();
        for (JsonElement e : lista(json,"standings")) {
            JsonObject tabla = e.getAsJsonObject();
            if (!"TOTAL".equals(texto(tabla,"type",""))) continue;
            for (JsonElement r : lista(tabla,"table")) {
                JsonObject row = r.getAsJsonObject(), t = objeto(row,"team");
                filas.add(new Posicion(requerido(row,"position"), new Equipo((long)requerido(t,"id"), texto(t,"name",texto(t,"shortName","Equipo")), texto(t,"crest",null)),
                        requerido(row,"playedGames"), requerido(row,"won"), requerido(row,"draw"), requerido(row,"lost"),
                        requerido(row,"goalsFor"), requerido(row,"goalsAgainst"), requerido(row,"goalDifference"), requerido(row,"points")));
            }
        }
        return filas;
    }
    @Override public List<Posicion> tabla(String liga, String temporada) {
        return leer(em -> {
            ApiSnapshot s = em.find(ApiSnapshot.class, claveTabla(liga,temporada));
            return s == null ? List.of() : posiciones(respuesta(s.getPayload()));
        });
    }
    @Override public synchronized void sincronizar(String liga, String temporada) throws Exception {
        if (fresca(claveTabla(liga, temporada))) return;
        if (!temporada.matches("\\d{4}/\\d{2}")) throw new IllegalArgumentException("Temporada no válida.");
        JsonObject json = respuesta(api.consultarRecurso("competitions/" + codigo(liga) + "/standings?season=" + temporada.substring(0,4)));
        if (!json.has("standings")) throw new IllegalArgumentException("La API no devolvió una clasificación.");
        List<Posicion> filas = posiciones(json);
        escribir(em -> {
            Competition c = competicion(em, objeto(json,"competition"));
            for (JsonElement e : lista(json,"standings")) for (JsonElement r : lista(e.getAsJsonObject(),"table"))
                equipo(em, objeto(r.getAsJsonObject(),"team"), c);
            snapshot(em, claveTabla(liga,temporada),json);
            // Standing no tiene temporada: solo refleja la actual; el histórico queda en snapshots por temporada.
            if (Integer.parseInt(temporada.substring(0,4)) == temporada(LocalDate.now())) {
                em.createQuery("delete from Standing s where s.competition.id = :id").setParameter("id",c.getId()).executeUpdate();
                for (Posicion f : filas) em.persist(new Standing(c, em.getReference(Team.class,Math.toIntExact(f.getEquipo().getId())),
                        f.getPosicion(),f.getPartidosJugados(),f.getGanados(),f.getEmpatados(),f.getPerdidos(),f.getPuntos(),
                        f.getGolesFavor(),f.getGolesContra(),f.getDiferencia()));
            }
            return null;
        });
        sincronizaciones.put(claveTabla(liga, temporada), Instant.now());
    }
    @Override public Set<Long> idsPartidosFavoritos() {
        return leer(em -> em.createQuery("select f.match.id from MatchFavorite f",Integer.class).getResultList()
                .stream().map(Integer::longValue).collect(Collectors.toSet()));
    }
    @Override public Set<Long> idsEquiposFavoritos() {
        return leer(em -> em.createQuery("select f.team.id from Favorite f",Integer.class).getResultList()
                .stream().map(Integer::longValue).collect(Collectors.toSet()));
    }
    @Override public synchronized boolean alternarPartido(Long id) {
        return escribir(em -> {
            MatchFavorite f = em.find(MatchFavorite.class,Math.toIntExact(id));
            if (f != null) { em.remove(f); return false; }
            Match m = em.find(Match.class,Math.toIntExact(id));
            if (m == null) throw new IllegalArgumentException("Partido no encontrado.");
            em.persist(new MatchFavorite(m)); return true;
        });
    }
    @Override public synchronized boolean alternarEquipo(Long id) {
        return escribir(em -> {
            Team t = em.find(Team.class,Math.toIntExact(id));
            if (t == null) throw new IllegalArgumentException("Equipo no encontrado.");
            List<Favorite> existentes = em.createQuery("select f from Favorite f where f.team.id = :id",Favorite.class)
                    .setParameter("id",Math.toIntExact(id)).getResultList();
            if (!existentes.isEmpty()) { existentes.forEach(em::remove); t.setFavorite(false); return false; }
            em.persist(new Favorite(t,null)); t.setFavorite(true); return true;
        });
    }
    @Override public List<Partido> partidosFavoritos() {
        return leer(em -> em.createQuery("select f from MatchFavorite f join fetch f.match m join fetch m.competition "
                + "join fetch m.homeTeam join fetch m.awayTeam order by m.utcDate",MatchFavorite.class).getResultList()
                .stream().map(f -> partidoUi(f.getMatch())).toList());
    }
    @Override public List<Equipo> equiposFavoritos() {
        return leer(em -> em.createQuery("select f from Favorite f join fetch f.team order by f.team.name",Favorite.class)
                .getResultList().stream().map(f -> equipoUi(f.getTeam())).toList());
    }
}
