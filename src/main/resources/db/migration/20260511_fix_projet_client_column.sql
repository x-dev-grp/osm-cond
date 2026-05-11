-- Align project client persistence with the backend mapping:
-- Projet.client is stored in projet.projet_client_id.
--
-- This script is safe to run on databases that still have the older client_id
-- column created by Hibernate.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'projet'
          AND column_name = 'projet_client_id'
    ) THEN
        ALTER TABLE public.projet ADD COLUMN projet_client_id uuid;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = 'projet'
          AND column_name = 'client_id'
    ) THEN
        UPDATE public.projet
        SET projet_client_id = client_id
        WHERE projet_client_id IS NULL
          AND client_id IS NOT NULL;

        ALTER TABLE public.projet ALTER COLUMN client_id DROP NOT NULL;
    END IF;

    ALTER TABLE public.projet ALTER COLUMN projet_client_id SET NOT NULL;
END $$;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'projet_aud'
    ) THEN
        IF NOT EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'projet_aud'
              AND column_name = 'projet_client_id'
        ) THEN
            ALTER TABLE public.projet_aud ADD COLUMN projet_client_id uuid;
        END IF;

        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = 'projet_aud'
              AND column_name = 'client_id'
        ) THEN
            UPDATE public.projet_aud
            SET projet_client_id = client_id
            WHERE projet_client_id IS NULL
              AND client_id IS NOT NULL;
        END IF;
    END IF;
END $$;
