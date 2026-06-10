-- Migrate legacy shipping records into expedition before removing the shipping module.
-- Execute manually during rollout (same process as other osm-cond migration scripts).

DO $$
BEGIN
    IF to_regclass('public.shipping_info') IS NULL THEN
        RAISE NOTICE 'shipping_info table not found, skipping shipping migration';
        RETURN;
    END IF;

    CREATE TEMP TABLE shipping_expedition_migration (
        shipping_id uuid PRIMARY KEY,
        expedition_id uuid NOT NULL
    ) ON COMMIT DROP;

    WITH migrated AS (
        INSERT INTO expedition (
            id,
            external_id,
            tenant_id,
            is_deleted,
            created_by,
            created_date,
            last_modified_by,
            last_modified_date,
            qr_hex,
            qr_image_base64,
            expedition_number,
            projet_id,
            client_id,
            status,
            destination,
            planned_ship_date,
            validated_at,
            shipped_at,
            delivered_at,
            cancelled_at,
            notes,
            carrier_name,
            driver_name,
            truck_number,
            tracking_number,
            incoterm
        )
        SELECT
            gen_random_uuid(),
            COALESCE(s.external_id, gen_random_uuid()),
            s.tenant_id,
            s.is_deleted,
            s.created_by,
            s.created_date,
            s.last_modified_by,
            s.last_modified_date,
            s.qr_hex,
            s.qr_image_base64,
            s.shipping_number,
            s.projet_id,
            p.client_id,
            CASE s.status
                WHEN 'DRAFT' THEN 'DRAFT'
                WHEN 'READY_TO_SHIP' THEN 'READY'
                WHEN 'IN_TRANSIT' THEN 'SHIPPED'
                WHEN 'ARRIVED' THEN 'SHIPPED'
                WHEN 'DELIVERED' THEN 'DELIVERED'
                WHEN 'CANCELLED' THEN 'CANCELLED'
                ELSE 'DRAFT'
            END,
            s.destination,
            s.expected_ship_date,
            NULL,
            CASE
                WHEN s.status IN ('IN_TRANSIT', 'ARRIVED', 'DELIVERED') THEN COALESCE(s.departed_at, s.last_modified_date)
                ELSE NULL
            END,
            s.delivered_at,
            CASE WHEN s.status = 'CANCELLED' THEN s.last_modified_date ELSE NULL END,
            CASE
                WHEN EXISTS (SELECT 1 FROM shipping_event se WHERE se.shipping_info_id = s.id)
                    THEN COALESCE(s.notes, '') || E'\n[Migrated shipping events preserved in legacy tables]'
                ELSE s.notes
            END,
            s.carrier_name,
            s.driver_name,
            s.truck_number,
            s.tracking_number,
            s.incoterm
        FROM shipping_info s
        JOIN projet p ON p.id = s.projet_id
        WHERE s.is_deleted = false
          AND NOT EXISTS (
              SELECT 1
              FROM expedition e
              WHERE e.projet_id = s.projet_id
                AND e.is_deleted = false
          )
        RETURNING id, expedition_number
    )
    INSERT INTO shipping_expedition_migration (shipping_id, expedition_id)
    SELECT s.id, m.id
    FROM shipping_info s
    JOIN migrated m ON m.expedition_number = s.shipping_number
    WHERE s.is_deleted = false;

    INSERT INTO expedition_line (
        id,
        external_id,
        tenant_id,
        is_deleted,
        created_by,
        created_date,
        last_modified_by,
        last_modified_date,
        expedition_id,
        article_id,
        article_name_snapshot,
        quantity,
        unit
    )
    SELECT
        gen_random_uuid(),
        COALESCE(sl.external_id, gen_random_uuid()),
        sl.tenant_id,
        sl.is_deleted,
        sl.created_by,
        sl.created_date,
        sl.last_modified_by,
        sl.last_modified_date,
        sem.expedition_id,
        sl.article_id,
        sl.article_name_snapshot,
        sl.quantity,
        sl.unit
    FROM shipping_line sl
    JOIN shipping_expedition_migration sem ON sem.shipping_id = sl.shipping_info_id
    WHERE sl.is_deleted = false;

    UPDATE expedition e
    SET
        destination = COALESCE(e.destination, s.destination),
        planned_ship_date = COALESCE(e.planned_ship_date, s.expected_ship_date),
        carrier_name = COALESCE(e.carrier_name, s.carrier_name),
        driver_name = COALESCE(e.driver_name, s.driver_name),
        truck_number = COALESCE(e.truck_number, s.truck_number),
        tracking_number = COALESCE(e.tracking_number, s.tracking_number),
        incoterm = COALESCE(e.incoterm, s.incoterm),
        notes = CASE
            WHEN s.notes IS NOT NULL AND btrim(s.notes) <> ''
                 AND (e.notes IS NULL OR position(s.notes in e.notes) = 0)
                THEN COALESCE(e.notes || E'\n', '') || '[Merged from shipping] ' || s.notes
            ELSE e.notes
        END,
        delivered_at = COALESCE(e.delivered_at, s.delivered_at),
        last_modified_date = GREATEST(e.last_modified_date, s.last_modified_date)
    FROM shipping_info s
    WHERE s.is_deleted = false
      AND e.projet_id = s.projet_id
      AND e.is_deleted = false
      AND NOT EXISTS (
          SELECT 1 FROM shipping_expedition_migration sem WHERE sem.shipping_id = s.id
      );
END $$;
