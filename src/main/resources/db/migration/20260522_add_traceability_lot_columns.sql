-- Conditioning-side immutable lot references for OF and labels.
-- NOTE:
--   This repository currently does not include a Flyway/Liquibase runtime dependency,
--   so this script should be executed manually during rollout unless a migration runner
--   is added later.

ALTER TABLE IF EXISTS ordre_fabrication
    ADD COLUMN IF NOT EXISTS traceability_lot_id uuid;

ALTER TABLE IF EXISTS ordre_fabrication_aud
    ADD COLUMN IF NOT EXISTS traceability_lot_id uuid;

ALTER TABLE IF EXISTS label_content
    ADD COLUMN IF NOT EXISTS traceability_lot_id uuid;

ALTER TABLE IF EXISTS label_content_aud
    ADD COLUMN IF NOT EXISTS traceability_lot_id uuid;

CREATE INDEX IF NOT EXISTS idx_ordre_fabrication_traceability_lot_id
    ON ordre_fabrication (traceability_lot_id);

CREATE INDEX IF NOT EXISTS idx_label_content_traceability_lot_id
    ON label_content (traceability_lot_id);

COMMENT ON COLUMN ordre_fabrication.traceability_lot_id IS 'Immutable genealogy anchor resolved from production traceability.';
COMMENT ON COLUMN label_content.traceability_lot_id IS 'Immutable genealogy anchor used for label traceability and expedition grouping.';
