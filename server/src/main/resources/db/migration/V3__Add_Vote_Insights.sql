-- server/src/main/resources/db/migration/V3__Add_Vote_Insights.sql

CREATE TABLE IF NOT EXISTS vote_insights (
    id SERIAL PRIMARY KEY,
    scrutin_uid VARCHAR(50) NOT NULL UNIQUE REFERENCES scrutins(uid),
    titre_court TEXT,
    type_objet VARCHAR(50)
        CHECK (type_objet IS NULL OR type_objet IN (
            'RESOLUTION_COMMISSION_ENQUETE',
            'PROJET_LOI_ENSEMBLE',
            'PROPOSITION_LOI_ENSEMBLE',
            'AMENDEMENT',
            'MOTION_CENSURE',
            'DECLARATION_POLITIQUE_GENERALE',
            'ARTICLE',
            'AUTRE'
        )),
    est_consensuel BOOLEAN NOT NULL DEFAULT FALSE,
    demandeurs_groupes TEXT[],
    taux_participation DOUBLE PRECISION
        CHECK (taux_participation IS NULL OR (taux_participation >= 0.0 AND taux_participation <= 1.0)),
    insights_version INTEGER NOT NULL DEFAULT 1,
    computed_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_vote_insights_scrutin_uid ON vote_insights(scrutin_uid);
