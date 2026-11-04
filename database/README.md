# Base de Datos: football_db (PostgreSQL)

Este directorio contiene los scripts para inicializar y mantener la base de datos local del proyecto.

## Instrucciones para el Equipo

### 1. Requisitos
- Tener instalado PostgreSQL (versión 14 a 18+).
- Estar configurado en modo `trust` localmente (o con el usuario `postgres`).

### 2. Creación e Inicialización Rápida
Ejecuta los siguientes comandos desde tu terminal (PowerShell o CMD):

```powershell
# 1. Crear la base de datos si aún no existe
psql -U postgres -h 127.0.0.1 -p 5432 -c "CREATE DATABASE football_db;"

# 2. Ejecutar el script con las tablas y datos de prueba
psql -U postgres -h 127.0.0.1 -p 5432 -d football_db -f "database/schema.sql"
```

Alternativamente, puedes abrir `database/schema.sql` en **pgAdmin 4** o **DBeaver** conectado a `localhost:5432` y ejecutar todo el script.

### 3. Módulos asignados:
- **Módulo 1 (Donald):** Tabla `teams` (Equipos y Favoritos) + Conexión Centralizada `DatabaseConnection.java`.
- **Módulo 2 (Integrante 2):** Tabla `matches` (Partidos, Marcadores y Fechas).
- **Módulo 3 (Integrante 3):** Tabla `standings` (Tablas de posiciones, Puntos y Diferencia de Gol).
