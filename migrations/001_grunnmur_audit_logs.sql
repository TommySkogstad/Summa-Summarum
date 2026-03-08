-- Migrering: Tilpass audit_logs-tabellen til grunnmur sin AuditLogs-definisjon.
-- Kjøres manuelt mot dev og prod databaser.
--
-- Endringer:
-- 1. Legg til "user_email" kolonne (grunnmur krever denne)
-- 2. Fjern FK constraint til users-tabellen (grunnmur bruker enkel integer)
-- 3. Legg til indekser som grunnmur forventer

-- Legg til user_email kolonne
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS user_email VARCHAR(255) NOT NULL DEFAULT 'system';

-- Fjern FK constraint hvis den finnes
DO $$
BEGIN
    PERFORM 1 FROM information_schema.table_constraints
    WHERE table_name = 'audit_logs'
    AND constraint_type = 'FOREIGN KEY';

    IF FOUND THEN
        EXECUTE (
            SELECT 'ALTER TABLE audit_logs DROP CONSTRAINT ' || constraint_name
            FROM information_schema.table_constraints
            WHERE table_name = 'audit_logs'
            AND constraint_type = 'FOREIGN KEY'
            LIMIT 1
        );
    END IF;
END $$;

-- Legg til indekser som grunnmur forventer (ignorerer hvis de finnes)
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user ON audit_logs(user_id);
