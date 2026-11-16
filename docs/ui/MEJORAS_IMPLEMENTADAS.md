# Mejoras de interfaz y persistencia

## Interfaz

- Tablas expandibles: aprovechan la altura disponible sin espaciadores inferiores.
- Navegación con iconos y contador conjunto de equipos y partidos favoritos.
- Búsqueda instantánea por ambos equipos en Partidos y por equipo en Posiciones; ignora mayúsculas, tildes y espacios exteriores. Conserva la ordenación de columnas.
- Acciones compactas con ayudas emergentes y nombres accesibles.
- Estado en vivo con punto rojo; finalizado con marca de verificación; encuentros programados con fecha relativa y hora local.
- Estados vacíos con icono e instrucciones.
- Escudos de 24 px, descarga asíncrona y caché de hasta 256 imágenes. Respaldo vectorial local si falta imagen, falla la conexión o el formato no es compatible con JavaFX (por ejemplo, SVG remoto).

## Persistencia y API

- TTL de 90 segundos por competición/temporada/recurso. En football-data se registra solo después del commit; no oculta errores de persistencia.
- Límite móvil de 10 solicitudes/minuto por proveedor en el proceso. No evita cuotas consumidas desde otras aplicaciones ni la cuota diaria del proveedor.
- HikariCP: máximo 10 conexiones, mínimo 2 y espera máxima de 20 segundos.
- `GenericDao.executeInTransaction` centraliza commit, rollback y cierre. Los errores al eliminar se propagan, en lugar de aparentar una eliminación inexistente.
- `MatchDao.findPage` pagina en SQL y permite filtrar jornada; no hace fetch de colecciones al paginar.
- Se conserva la conversión explícita UTC → zona del sistema y los límites de día local en las consultas.
- Cuando Highlightly no está configurado, API-Football complementa eventos y estadísticas al actualizar el detalle. Busca por liga y temporada, valida la hora UTC y ambos nombres de equipo, y usa su propio fixture ID. Si no hay coincidencia única, conserva los datos principales. Variantes de nombres no reconocidas pueden impedir el complemento.
- El complemento se guarda en `api_snapshots` separado de football-data. No mezcla IDs de equipos o partidos entre proveedores.
- Se detectan errores de API-Football incluso con HTTP 200. Una restricción de plan conserva el detalle principal y se informa en la interfaz.

## Configuración y ejecución

Las claves permanecen en `config.local.properties`, ignorado por Git y fuera del JAR. También se admiten variables de entorno. Usa Java 21.

```powershell
.\mvnw.cmd test
.\mvnw.cmd package "-Dfutbol.test.real=true"
.\mvnw.cmd javafx:run
```

Las pruebas reales requieren PostgreSQL y ambas claves; consumen cuota y sincronizan datos reales. Comprueban TTL, paginación, favoritos tras reconexión, persistencia del detalle de API-Football, rollback y lectura sin red. Las pruebas de interfaz requieren una sesión gráfica.

## Verificación del 15 de noviembre de 2026

- football-data: consulta real de LaLiga con 380 partidos; base de datos con 2.237 partidos.
- API-Football: suscripción Free activa; fixture histórico 1208630 devuelve 24 eventos y estadísticas de ambos equipos. Se comprobó su persistencia y lectura tras reconexión.
- La clave Free de API-Football rechaza la temporada 2026; la respuesta indica acceso a temporadas 2022–2024. La autenticación correcta no implica acceso a todas las temporadas. No se puede habilitar esa cobertura desde el código.
- Revisión visual de Partidos y Posiciones y pruebas de búsqueda, expansión vertical, navegación y contador de favoritos.

Documentación de referencia: [API-Football](https://www.api-football.com/documentation-v3) y [políticas de football-data](https://docs.football-data.org/general/v4/policies.html).

La integración preferida del detalle es ahora [Highlightly](HIGHLIGHTLY.md), con eventos y estadísticas actuales comprobados.
