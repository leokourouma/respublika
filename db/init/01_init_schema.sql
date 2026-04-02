-- 1. Référentiel des Acteurs et Organes
CREATE TABLE IF NOT EXISTS organes (
    uid VARCHAR(50) PRIMARY KEY,
    code_type VARCHAR(50),
    libelle TEXT NOT NULL,
    libelle_abrege VARCHAR(100),
    couleur_associee VARCHAR(10),
    region TEXT,
    departement_code VARCHAR(10),
    departement_libelle TEXT
);

CREATE TABLE IF NOT EXISTS deputes (
    id_an VARCHAR(20) PRIMARY KEY,
    id_hatvp VARCHAR(100),
    civ_prenom_nom TEXT NOT NULL,
    profession TEXT,
    groupe_politique_uid VARCHAR(50) REFERENCES organes(uid),
    uri_hatvp TEXT,
    twitter TEXT,
    facebook TEXT,
    instagram TEXT,
    linkedin TEXT,
    email TEXT,
    circonscription_id VARCHAR(50) REFERENCES organes(uid),
    est_membre_gouv BOOLEAN DEFAULT FALSE,
    slug_url TEXT UNIQUE NOT NULL
);

-- 2. Travail Législatif
CREATE TABLE IF NOT EXISTS dossiers (
    uid VARCHAR(50) PRIMARY KEY,
    titre TEXT NOT NULL,
    procedure_type TEXT,
    initiateur_id VARCHAR(20) REFERENCES deputes(id_an)
);

CREATE TABLE IF NOT EXISTS scrutins (
    uid VARCHAR(50) PRIMARY KEY,
    titre TEXT NOT NULL,
    date_vote TEXT NOT NULL,
    sort TEXT,
    nombre_votants INTEGER,
    suffrages_exprimes INTEGER,
    nbre_suffrages_requis INTEGER,
    dossier_id VARCHAR(50) REFERENCES dossiers(uid)
);

-- 3. Votes par groupe politique
CREATE TABLE IF NOT EXISTS votes_groupes (
    id SERIAL PRIMARY KEY,
    scrutin_uid VARCHAR(50) NOT NULL,
    groupe_uid VARCHAR(50) NOT NULL,
    nombre_membres INTEGER DEFAULT 0,
    position_majoritaire TEXT,
    pour INTEGER DEFAULT 0,
    contre INTEGER DEFAULT 0,
    abstentions INTEGER DEFAULT 0,
    non_votants INTEGER DEFAULT 0,
    UNIQUE(scrutin_uid, groupe_uid)
);

-- 4. Votes individuels nominatifs
CREATE TABLE IF NOT EXISTS votes_individuels (
    id SERIAL PRIMARY KEY,
    scrutin_uid VARCHAR(50) NOT NULL,
    depute_id VARCHAR(20) NOT NULL,
    position TEXT NOT NULL,  -- 'pour', 'contre', 'abstention', 'non_votant'
    par_delegation BOOLEAN DEFAULT FALSE,
    UNIQUE(scrutin_uid, depute_id)
);

CREATE TABLE IF NOT EXISTS amendements (
    uid VARCHAR(50) PRIMARY KEY,
    numero VARCHAR(20),
    auteur_id VARCHAR(20) REFERENCES deputes(id_an),
    dossier_id VARCHAR(50) REFERENCES dossiers(uid),
    dispositif_html TEXT,
    expose_sommaire TEXT,
    sort TEXT,
    date_depot DATE
);

-- 5. Éthique et Parole
CREATE TABLE IF NOT EXISTS deports (
    uid VARCHAR(50) PRIMARY KEY,
    depute_id VARCHAR(20) REFERENCES deputes(id_an),
    date_creation TIMESTAMP,
    libelle_portee TEXT,
    cible TEXT,
    explication_html TEXT
);
