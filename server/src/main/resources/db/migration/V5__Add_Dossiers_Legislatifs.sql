-- server/src/main/resources/db/migration/V5__Add_Dossiers_Legislatifs.sql
--
-- Introduce the canonical "Dossier législatif" entity and link scrutins to
-- their parent dossier. Driven by analysis/05_recommendation.md.
--
-- The legacy `public.dossiers` table (one column = uid, one = titre) is left
-- untouched. It is empty in the current DB and not populated by anything;
-- this migration creates a NEW table `dossiers_legislatifs` with the proper
-- schema and points scrutins.dossier_uid at it.
--
-- Type mapping from dossierParlementaire JSON (applied by DossierIngestor):
--   @xsi:type = DossierLegislatif_Type
--       AND procedureParlementaire.code = "5" (Projet ou proposition de loi organique)
--                                                          → LOI_ORGANIQUE
--       otherwise                                          → LOI_ORDINAIRE
--   @xsi:type = DossierResolutionAN                        → PROPOSITION_RESOLUTION
--   @xsi:type = DossierIniativeExecutif_Type               → MOTION_CENSURE
--   anything else                                           → AUTRE
--
-- Etat derivation (checked in order):
--   any acte @xsi:type = Promulgation_Type                 → PROMULGUE
--   any acte @xsi:type = RetraitInitiative_Type            → RETIRE
--   most recent Decision_Type / DecisionMotionCensure_Type
--     statutConclusion.libelle case-insensitive CONTAINS "rejet"  → REJETE
--     statutConclusion.libelle case-insensitive CONTAINS "adopt"  → ADOPTE
--     other                                                       → EN_COURS
--   no Decision_Type at all                                → DORMANT

CREATE TABLE IF NOT EXISTS dossiers_legislatifs (
    uid                       VARCHAR(50) PRIMARY KEY,
    titre                     TEXT NOT NULL,
    titre_court               TEXT,
    type                      VARCHAR(40) NOT NULL
        CHECK (type IN ('LOI_ORDINAIRE', 'LOI_ORGANIQUE', 'PROPOSITION_RESOLUTION', 'MOTION_CENSURE', 'AUTRE')),
    etat                      VARCHAR(20) NOT NULL
        CHECK (etat IN ('DORMANT', 'EN_COURS', 'ADOPTE', 'REJETE', 'RETIRE', 'PROMULGUE')),
    legislature               SMALLINT NOT NULL,
    date_depot                DATE,
    date_derniere_decision    DATE,
    date_promulgation         DATE,
    numero_loi                VARCHAR(40),
    themes                    TEXT[],
    raw_json                  JSONB NOT NULL,
    created_at                TIMESTAMP NOT NULL DEFAULT now(),
    updated_at                TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_dossiers_legislatifs_etat              ON dossiers_legislatifs (etat);
CREATE INDEX IF NOT EXISTS idx_dossiers_legislatifs_date_decision     ON dossiers_legislatifs (date_derniere_decision DESC);
CREATE INDEX IF NOT EXISTS idx_dossiers_legislatifs_legislature       ON dossiers_legislatifs (legislature);
CREATE INDEX IF NOT EXISTS idx_dossiers_legislatifs_raw_json          ON dossiers_legislatifs USING gin (raw_json);

ALTER TABLE scrutins ADD COLUMN IF NOT EXISTS dossier_uid             VARCHAR(50);
ALTER TABLE scrutins ADD COLUMN IF NOT EXISTS dossier_link_method     VARCHAR(20)
    CHECK (dossier_link_method IS NULL OR dossier_link_method IN ('voteRef', 'seance'));
ALTER TABLE scrutins ADD COLUMN IF NOT EXISTS dossier_link_confidence NUMERIC(3, 2)
    CHECK (dossier_link_confidence IS NULL OR (dossier_link_confidence >= 0.0 AND dossier_link_confidence <= 1.0));

-- ALTER TABLE … ADD CONSTRAINT does not support IF NOT EXISTS in PG 16; guard it.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM   pg_constraint
        WHERE  conname = 'scrutins_dossier_uid_fkey'
        AND    conrelid = 'scrutins'::regclass
    ) THEN
        ALTER TABLE scrutins
            ADD CONSTRAINT scrutins_dossier_uid_fkey
            FOREIGN KEY (dossier_uid) REFERENCES dossiers_legislatifs(uid) ON DELETE SET NULL;
    END IF;
END$$;

CREATE INDEX IF NOT EXISTS idx_scrutins_dossier_uid ON scrutins (dossier_uid);

-- Staging table for ambiguous séance matches (one scrutin → multiple candidate
-- dossiers because the séance discussed several texts on the same day).
-- Resolved in V2 once the amendments table is populated.
CREATE TABLE IF NOT EXISTS dossier_link_candidates (
    id            BIGSERIAL PRIMARY KEY,
    scrutin_uid   VARCHAR(50) NOT NULL REFERENCES scrutins(uid) ON DELETE CASCADE,
    dossier_uid   VARCHAR(50) NOT NULL REFERENCES dossiers_legislatifs(uid) ON DELETE CASCADE,
    link_method   VARCHAR(20) NOT NULL
        CHECK (link_method IN ('seance')),
    confidence    NUMERIC(3, 2) NOT NULL
        CHECK (confidence >= 0.0 AND confidence <= 1.0),
    created_at    TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (scrutin_uid, dossier_uid)
);

CREATE INDEX IF NOT EXISTS idx_dossier_link_candidates_scrutin ON dossier_link_candidates (scrutin_uid);
