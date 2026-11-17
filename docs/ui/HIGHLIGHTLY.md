# Detalle de partidos con Highlightly

La aplicación usa football-data para calendario, marcador y clasificación. Highlightly es la fuente preferida del detalle cuando existe `HIGHLIGHTLY_API_KEY`; API-Football sigue disponible como alternativa si no está configurada esa clave. Un fallo del resultado principal no impide intentar el complemento.

## Configuración

Añadir `HIGHLIGHTLY_API_KEY` a `config.local.properties` (ignorado por Git) o a las variables de entorno. La suscripción debe corresponder al producto Football API de Highlightly. El cliente usa `https://soccer.highlightly.net` y el encabezado `x-rapidapi-key`. No utiliza la clave en URLs ni la incluye en el JAR.

## Flujo

1. Al abrir un partido se consulta el detalle automáticamente, fuera del hilo JavaFX.
2. Se busca por día UTC, competición y país para las ligas domésticas. Se recorren las páginas y se exige una coincidencia única en fecha/hora, liga, país y ambos equipos. Las variantes conocidas de nombres se normalizan mediante equivalencias explícitas; no se asocian partidos únicamente por hora o marcador.
3. El ID propio de Highlightly se usa para `/matches/{id}` y `/lineups/{id}`. Se valida otra vez la identidad del partido y de las alineaciones.
4. Se guarda una respuesta independiente en `api_snapshots` con clave `highlightly/{idFootballData}`. Los IDs de equipos de proveedores distintos nunca se fusionan.
5. Los datos guardados se leen sin conexión. Hay TTL de 90 segundos y caché HTTP acotada; el ID ya asociado evita repetir la búsqueda. El cliente aplica un límite local conservador de 10 solicitudes/minuto. La cuota diaria del plan sigue aplicando.
6. Si fallan solo las alineaciones se conservan los eventos y estadísticas recibidos, así como las alineaciones anteriores del mismo partido. La pantalla informa del fallo. Los errores de actualización no se presentan como estadísticas en cero.

La interfaz presenta eventos con texto ajustado, una tabla con todas las estadísticas recibidas y titulares/suplentes con formación. Las tres barras resumen posesión, tiros y tiros a puerta. La posesión fraccionaria se convierte a porcentaje y los tiros se suman a partir de las zonas cuando el proveedor no entrega un total. Los campos ausentes permanecen sin valor.

La cobertura depende del partido. No se implementan todavía pantallas independientes para perfiles de jugadores, vídeos, pronósticos o momentum: esta integración cubre el detalle del encuentro solicitado.

## Verificación

```powershell
.\mvnw.cmd test
.\mvnw.cmd package "-Dfutbol.test.highlightly=true"
```

La prueba real requiere las claves de football-data y Highlightly, PostgreSQL y una sesión gráfica. Consulta Santander–Real Madrid del 01/11/2026 (football-data 564734, Highlightly 1336445224). Comprueba 14 eventos, posesión 46–54, tiros 10–22, tiros a puerta 1–7, El Sardinero y alineaciones. Verifica persistencia tras reconectar, TTL y lectura sin red.

Las pruebas sin red usan muestras de las respuestas reales (sin credenciales) en `src/test/resources/highlightly`. Cubren formatos, equipos invertidos, fechas/ligas/países distintos, paginación, coincidencias ambiguas y datos ausentes.

Referencias: [partidos](https://highlightly.net/football-api/documentation/matches/), [detalle](https://highlightly.net/football-api/documentation/matches/by-id/) y [alineaciones](https://highlightly.net/football-api/documentation/lineups/).

Las tablas de Partidos y Posiciones, los favoritos y el encabezado del detalle usan el nombre completo (`name`) del proveedor principal. Los nombres cortos se conservan para asociar proveedores y como respaldo si falta el nombre completo. Las celdas ajustan el texto y la altura de fila.

## Ventana de alineaciones

En la pestaña ⚑, el botón ⛶ abre una ventana independiente con los titulares de ambos equipos colocados en las filas de la formación y los suplentes debajo. La ventana permite maximizar y cerrar con iconos; Escape también la cierra.

El endpoint `/box-score/{matchId}` aporta nombres completos, puntuación del partido, capitán y estadísticas individuales. Se guarda como `boxScore` en el mismo snapshot PostgreSQL. Su caché HTTP dura cinco minutos, según el intervalo de actualización documentado del proveedor. Los snapshots anteriores sin este campo se enriquecen al actualizar.

- ⚽: goles; bota vectorial: asistencias; tarjetas amarillas y rojas en su color.
- ↘ y minuto: jugador sustituido; ↗ y minuto: jugador que entró.
- `|C|`: solo si Highlightly devuelve `isCaptain=true`.
- `—`: puntuación no publicada; nunca se calcula una nota ficticia.

Los contadores individuales se leen del box score y, cuando faltan, de los eventos. No se suman ambas fuentes para evitar duplicados. Los cambios se vinculan por equipo e ID de jugador; no por el orden de la lista. Una expulsión por segunda amarilla se representa también con tarjeta roja. Si el proveedor entrega información incompleta o discrepante, la interfaz refleja sus datos; no se infiere un capitán.

La navegación, las pestañas y los botones de acción usan iconos, con tooltips y nombres accesibles. Las pruebas cubren la ventana independiente, 22 titulares, puntuación, goles, asistencias, tarjetas, cambios, capitán, valores ausentes y persistencia sin red. Para generar una captura de verificación: `-Dfutbol.test.capture=true` escribe `target/alineacion.ppm`.
