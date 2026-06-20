-- =========================================================
-- VELIORASUITE DATABASE SCHEMA
-- =========================================================
-- Catatan:
-- Versi sekarang mayoritas module menyimpan data simple ke data/*.yml.
-- File ini disiapkan untuk migrasi SQLite lanjutan agar data lebih profesional.
-- =========================================================

CREATE TABLE IF NOT EXISTS veliora_teams (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT UNIQUE NOT NULL,
    owner_uuid TEXT NOT NULL,
    level INTEGER DEFAULT 1,
    max_members INTEGER DEFAULT 5,
    created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS veliora_team_members (
    team_id INTEGER NOT NULL,
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL,
    role TEXT NOT NULL,
    joined_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS veliora_reports (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL,
    status TEXT DEFAULT 'open',
    reporter TEXT NOT NULL,
    reporter_ip TEXT,
    target TEXT,
    target_ip TEXT,
    reason TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    closed_by TEXT,
    closed_at INTEGER
);

CREATE TABLE IF NOT EXISTS veliora_login_users (
    player_name TEXT PRIMARY KEY,
    uuid TEXT,
    salt TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    last_ip TEXT,
    registered_at INTEGER NOT NULL,
    last_login INTEGER
);

CREATE TABLE IF NOT EXISTS veliora_fishing_stats (
    uuid TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    catches INTEGER DEFAULT 0,
    score INTEGER DEFAULT 0
);
