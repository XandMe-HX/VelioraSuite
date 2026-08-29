-- =========================================================
-- VELIORASUITE DATABASE SCHEMA
-- =========================================================
-- SQLite aktif dipakai sebagai fondasi runtime VelioraSuite.
-- Konfigurasi tetap berada di YAML. Table module ditambah lewat migrasi Java
-- agar upgrade schema selalu memiliki versi dan backup yang jelas.
-- =========================================================

CREATE TABLE IF NOT EXISTS veliorasuite_meta (
    key_name TEXT PRIMARY KEY,
    value_text TEXT
);
