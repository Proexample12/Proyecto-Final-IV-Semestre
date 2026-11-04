-- =============================================================================
-- PROYECTO: Aplicación de Seguimiento de Fútbol (JavaFX + PostgreSQL)
-- BASE DE DATOS: football_db
-- COMPETICIONES: Premier League (PL - 2021), LaLiga (PD - 2014), Champions League (CL - 2001)
-- =============================================================================

-- 1. TABLA MAESTRA COMPARTIDA: Competiciones
CREATE TABLE IF NOT EXISTS competitions (
    id INT PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,       -- 'PL', 'PD', 'CL'
    name VARCHAR(100) NOT NULL,
    emblem_url TEXT
);

INSERT INTO competitions (id, code, name, emblem_url) VALUES 
    (2021, 'PL', 'Premier League', 'https://crests.football-data.org/PL.png'),
    (2014, 'PD', 'LaLiga', 'https://crests.football-data.org/PD.png'),
    (2001, 'CL', 'UEFA Champions League', 'https://crests.football-data.org/CL.png')
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- MÓDULO 1 (INTEGRANTE 1 - Donald): Equipos y Gestión de Favoritos
-- =============================================================================
CREATE TABLE IF NOT EXISTS teams (
    id INT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    short_name VARCHAR(60),
    tla VARCHAR(5),                          -- Siglas (ej: 'RMA', 'FCB', 'MCI')
    crest_url TEXT,
    competition_id INT REFERENCES competitions(id) ON DELETE CASCADE,
    is_favorite BOOLEAN DEFAULT FALSE,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Datos de prueba para Equipos (LaLiga, Premier League y Champions League)
INSERT INTO teams (id, name, short_name, tla, crest_url, competition_id, is_favorite) VALUES
    -- LaLiga (2014)
    (86,  'Real Madrid CF',         'Real Madrid', 'RMA', 'https://crests.football-data.org/86.png',  2014, TRUE),
    (81,  'FC Barcelona',           'Barcelona',   'BAR', 'https://crests.football-data.org/81.png',  2014, FALSE),
    (78,  'Club Atlético de Madrid', 'Atlético',    'ATM', 'https://crests.football-data.org/78.png',  2014, FALSE),
    -- Premier League (2021)
    (65,  'Manchester City FC',     'Man City',    'MCI', 'https://crests.football-data.org/65.png',  2021, TRUE),
    (64,  'Liverpool FC',           'Liverpool',   'LIV', 'https://crests.football-data.org/64.png',  2021, FALSE),
    (57,  'Arsenal FC',             'Arsenal',     'ARS', 'https://crests.football-data.org/57.png',  2021, FALSE),
    -- UEFA Champions League (2001) / Equipos europeos adicionales
    (5,   'FC Bayern München',      'Bayern',      'FCB', 'https://crests.football-data.org/5.png',   2001, TRUE),
    (108, 'FC Internazionale Milano', 'Inter',     'INT', 'https://crests.football-data.org/108.png', 2001, FALSE)
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- MÓDULO 2 (INTEGRANTE 2): Partidos y Marcadores
-- =============================================================================
CREATE TABLE IF NOT EXISTS matches (
    id INT PRIMARY KEY,
    competition_id INT REFERENCES competitions(id) ON DELETE CASCADE,
    utc_date TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL,            -- 'TIMED', 'IN_PLAY', 'FINISHED'
    matchday INT,
    home_team_id INT REFERENCES teams(id) ON DELETE CASCADE,
    away_team_id INT REFERENCES teams(id) ON DELETE CASCADE,
    home_score INT DEFAULT 0,
    away_score INT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO matches (id, competition_id, utc_date, status, matchday, home_team_id, away_team_id, home_score, away_score) VALUES
    (1001, 2014, CURRENT_TIMESTAMP, 'FINISHED', 11, 86, 81, 2, 1),
    (1002, 2021, CURRENT_TIMESTAMP + INTERVAL '2 hour', 'IN_PLAY', 10, 65, 64, 1, 1),
    (1003, 2021, CURRENT_TIMESTAMP + INTERVAL '1 day',  'TIMED',   10, 57, 65, 0, 0),
    (1004, 2001, CURRENT_TIMESTAMP + INTERVAL '3 day',  'TIMED',   4,  5,  108, 0, 0)
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- MÓDULO 3 (INTEGRANTE 3): Tablas de Posiciones (Standings)
-- =============================================================================
CREATE TABLE IF NOT EXISTS standings (
    id SERIAL PRIMARY KEY,
    competition_id INT REFERENCES competitions(id) ON DELETE CASCADE,
    team_id INT REFERENCES teams(id) ON DELETE CASCADE,
    position INT NOT NULL,
    played_games INT DEFAULT 0,
    won INT DEFAULT 0,
    draw INT DEFAULT 0,
    lost INT DEFAULT 0,
    points INT DEFAULT 0,
    goals_for INT DEFAULT 0,
    goals_against INT DEFAULT 0,
    goal_difference INT DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_competition_team UNIQUE (competition_id, team_id)
);

INSERT INTO standings (competition_id, team_id, position, played_games, won, draw, lost, points, goals_for, goals_against, goal_difference) VALUES
    -- LaLiga
    (2014, 86, 1, 10, 8, 1, 1, 25, 21, 7,  14),
    (2014, 81, 2, 10, 7, 2, 1, 23, 24, 10, 14),
    (2014, 78, 3, 10, 6, 2, 2, 20, 18, 9,  9),
    -- Premier League
    (2021, 65, 1, 9,  7, 2, 0, 23, 20, 8,  12),
    (2021, 64, 2, 9,  6, 2, 1, 20, 17, 7,  10),
    (2021, 57, 3, 9,  5, 3, 1, 18, 15, 8,  7)
ON CONFLICT (competition_id, team_id) DO NOTHING;

-- =============================================================================
-- ÍNDICES PARA ALTO RENDIMIENTO
-- =============================================================================
CREATE INDEX IF NOT EXISTS idx_teams_competition ON teams(competition_id);
CREATE INDEX IF NOT EXISTS idx_teams_favorite ON teams(is_favorite);
CREATE INDEX IF NOT EXISTS idx_matches_date ON matches(utc_date);
CREATE INDEX IF NOT EXISTS idx_matches_comp ON matches(competition_id);
CREATE INDEX IF NOT EXISTS idx_standings_comp_pos ON standings(competition_id, position);
