ALTER TABLE IF EXISTS label_content
    ADD COLUMN IF NOT EXISTS product_id UUID;

UPDATE label_content
SET product_id = packaging_id
WHERE product_id IS NULL
  AND packaging_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_label_content_product_id
    ON label_content (product_id);
