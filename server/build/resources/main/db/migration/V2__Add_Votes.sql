-- server/src/main/resources/db/migration/V2__Add_Votes.sql

CREATE TABLE IF NOT EXISTS votes_deputes (
    id SERIAL PRIMARY KEY,
    scrutin_id VARCHAR(50) REFERENCES scrutins(uid),
    depute_id VARCHAR(20) REFERENCES deputes(id_an),
    position VARCHAR(15) NOT NULL, -- POUR, CONTRE, ABSTENTION
    UNIQUE(scrutin_id, depute_id) -- Un député ne vote qu'une fois par scrutin
);